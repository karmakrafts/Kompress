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

import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.WrappingCompressor
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.compressingSource
import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.util.Adler32
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.writeUByte
import kotlinx.io.writeUInt

/**
 * Compresses data into the Zlib format.
 *
 * @param level The DEFLATE compression level between 0 and 9.
 * @param cmf The CMF header fields to write.
 * @param flags The FLG header fields to write.
 */
@OptIn(InternalCompressionApi::class)
class ZlibCompressor(
    level: Int = Deflater.DEFAULT_LEVEL,
    private val cmf: ZlibCMF = ZlibCMF(),
    private val flags: ZlibFlags = ZlibFlags(ZlibCompressionLevel.fromDeflaterLevel(level))
) : WrappingCompressor(Deflater(level)) {
    companion object {
        /**
         * Compresses the given data in one go using the given
         * compression level and buffer size.
         *
         * @param data The data to compress.
         * @param level The compression level between 0 and 9.
         * @param bufferSize The size of the intermediate buffer used during compression.
         * @return The compressed data.
         */
        fun compress( // @formatter:off
            data: ByteArray,
            level: Int = Deflater.DEFAULT_LEVEL,
            bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = ZlibCompressor(level).use { compressor -> // @formatter:on
            compressor.compressBulk(data, bufferSize)
        }
    }

    private val adler32: Adler32 = Adler32()

    override fun appendPrologue() {
        buffer.writeUByte(cmf.value)
        buffer.writeUByte(flags.withCheckBits(cmf))
    }

    override fun onDataRead(offset: Int, size: Int) {
        adler32.round(input, offset, size)
    }

    override fun appendEpilogue() {
        buffer.writeUInt(adler32.checksum)
    }

    override fun reset() {
        super.reset()
        adler32.reset()
    }
}

/**
 * Returns a [RawSource] that reads uncompressed bytes from this source and
 * emits their Zlib-compressed form.
 *
 * This is a streaming wrapper: bytes are compressed on the fly as you read
 * from the returned source. Close the returned source when finished to free
 * any underlying resources.
 *
 * @param level Compression level in range 0..9. See [Deflater.level].
 * @param cmf The CMF header fields to write.
 * @param flags The FLG header fields to write.
 * @param bufferSize Size of the internal working buffers used during
 *  compression.
 * @return A [RawSource] that produces compressed data.
 */
fun RawSource.zlibSource( // @formatter:off
    level: Int = Deflater.DEFAULT_LEVEL,
    cmf: ZlibCMF = ZlibCMF(),
    flags: ZlibFlags = ZlibFlags(ZlibCompressionLevel.fromDeflaterLevel(level)),
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSource = compressingSource(ZlibCompressor(level, cmf, flags), bufferSize) // @formatter:on

/**
 * Returns a [RawSink] that compresses written bytes using Zlib and
 * writes them to this sink.
 *
 * This is a streaming wrapper: bytes are compressed on the fly as you write
 * to the returned sink. Close the returned sink when finished to free
 * any underlying resources and ensure all data is flushed.
 *
 * @param level Compression level in range 0..9. See [Deflater.level].
 * @param cmf The CMF header fields to write.
 * @param flags The FLG header fields to write.
 * @param bufferSize Size of the internal working buffers used during
 *  compression.
 * @return A [RawSink] that accepts uncompressed data and writes compressed data.
 */
fun RawSink.zlibSink( // @formatter:off
    level: Int = Deflater.DEFAULT_LEVEL,
    cmf: ZlibCMF = ZlibCMF(),
    flags: ZlibFlags = ZlibFlags(ZlibCompressionLevel.fromDeflaterLevel(level)),
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSink = compressingSink(ZlibCompressor(level, cmf, flags), bufferSize) // @formatter:on