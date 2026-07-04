/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kompress.zlib

import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.UnwrappingDecompressor
import dev.karmakrafts.kompress.deflate.Inflater
import dev.karmakrafts.kompress.exception.DataFormatException
import dev.karmakrafts.kompress.exception.InvalidChecksumException
import dev.karmakrafts.kompress.lz77.LZ77
import dev.karmakrafts.kompress.util.Adler32
import kotlinx.io.readUByte
import kotlinx.io.readUInt

@OptIn(InternalCompressionApi::class)
class ZlibDecompressor : UnwrappingDecompressor(Inflater()) {
    companion object {
        /**
         * Decompresses the given data in one go using the given
         * buffer size.
         *
         * @param data The data to decompress.
         * @param bufferSize The size of the intermediate buffer used during decompression.
         * @return The decompressed data.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun decompress( // @formatter:off
            data: ByteArray,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = ZlibDecompressor().use { decompressor -> // @formatter:on
            decompressor.decompressBulk(data, bufferSize)
        }
    }

    private val adler32: Adler32 = Adler32()

    override fun consumePrologue(): Boolean {
        if (buffer.size < 2L) return false
        val cmf = ZlibCMF(buffer.readUByte())
        val flg = ZlibFlags(buffer.readUByte())
        val header = (cmf.value.toUInt() shl 8) or flg.fields.toUInt()
        if (header % 31U != 0U) {
            throw DataFormatException("Invalid Zlib header check bits")
        }
        val compressionMethod = try {
            cmf.compressionMethod
        } catch (_: NoSuchElementException) {
            val encodedCompressionMethod = (cmf.value.toUInt() and 0b1111U).toUByte()
            throw DataFormatException("Unsupported Zlib compression method 0x${encodedCompressionMethod.toHexString()}")
        }
        if (compressionMethod != ZlibCompressionMethod.DEFLATE) {
            throw DataFormatException("Unsupported Zlib compression method 0x${compressionMethod.encodedValue.toHexString()}")
        }
        val windowSize = cmf.windowSize
        if (windowSize > LZ77.DEFAULT_WINDOW_SIZE) {
            throw DataFormatException("Unsupported Zlib window size $windowSize")
        }
        if (flg.hasDictionary) {
            throw DataFormatException("Zlib preset dictionaries are not supported")
        }
        return true
    }

    override fun onDataWritten(output: ByteArray, offset: Int, size: Int) {
        adler32.round(output, offset, size)
    }

    override fun consumeEpilogue(): Boolean {
        if (buffer.size < UInt.SIZE_BYTES.toLong()) return false
        val expectedChecksum = buffer.readUInt()
        val actualChecksum = adler32.checksum
        if (expectedChecksum != actualChecksum) {
            throw InvalidChecksumException(expectedChecksum, actualChecksum)
        }
        return true
    }

    override fun reset() {
        super.reset()
        adler32.reset()
    }
}