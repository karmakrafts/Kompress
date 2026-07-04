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

import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.exception.DataFormatException
import dev.karmakrafts.kompress.exception.InvalidChecksumException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ZlibCompressorDecompressorTest {
    private companion object {
        val TEXT_DATA: ByteArray = "Hello, Zlib!".encodeToByteArray()
        val BINARY_DATA: ByteArray = Random(42).nextBytes(128 * 1024)

        fun header(cmfValue: UByte, flags: ZlibFlags = ZlibFlags()): ByteArray {
            val cmf = ZlibCMF(cmfValue)
            val withCheckBits = flags.withCheckBits(cmf)
            return byteArrayOf(cmf.value.toByte(), withCheckBits.toByte())
        }
    }

    @Test
    fun `companion compress and decompress round trip text payload`() {
        val compressedData = ZlibCompressor.compress(TEXT_DATA)
        val decompressedData = ZlibDecompressor.decompress(compressedData)

        assertContentEquals(TEXT_DATA, decompressedData)
    }

    @Test
    fun `instance compressor and decompressor round trip binary payload`() {
        ZlibCompressor(level = Deflater.MIN_LEVEL).use { compressor ->
            val compressedData = compressor.compressBulk(BINARY_DATA, bufferSize = 97)

            ZlibDecompressor().use { decompressor ->
                val decompressedData = decompressor.decompressBulk(compressedData, bufferSize = 89)

                assertContentEquals(BINARY_DATA, decompressedData)
            }
        }
    }

    @Test
    fun `compressed header reflects selected deflater level`() {
        val compressedData = ZlibCompressor.compress(TEXT_DATA, level = Deflater.MAX_LEVEL)
        val cmf = ZlibCMF(compressedData[0].toUByte())
        val flags = ZlibFlags(compressedData[1].toUByte())
        val header = (cmf.value.toUInt() shl 8) or flags.fields.toUInt()

        assertEquals(ZlibCompressionMethod.DEFLATE, cmf.compressionMethod)
        assertEquals(32 * 1024, cmf.windowSize)
        assertEquals(ZlibCompressionLevel.MAXIMUM, flags.level)
        assertFalse(flags.hasDictionary)
        assertEquals(0U, header % 31U)
    }

    @Test
    fun `decompress rejects invalid header check bits`() {
        val compressedData = ZlibCompressor.compress(TEXT_DATA).copyOf()
        compressedData[1] = (compressedData[1].toInt() xor 1).toByte()

        assertFailsWith<DataFormatException> {
            ZlibDecompressor.decompress(compressedData)
        }
    }

    @Test
    fun `decompress rejects unsupported compression method`() {
        assertFailsWith<DataFormatException> {
            ZlibDecompressor.decompress(header(0x70U))
        }
    }

    @Test
    fun `decompress rejects unsupported window size`() {
        assertFailsWith<DataFormatException> {
            ZlibDecompressor.decompress(header(0x88U))
        }
    }

    @Test
    fun `decompress rejects preset dictionary flag`() {
        assertFailsWith<DataFormatException> {
            ZlibDecompressor.decompress(header(ZlibCMF().value, ZlibFlags(hasDictionary = true)))
        }
    }

    @Test
    fun `decompress rejects invalid checksum`() {
        val compressedData = ZlibCompressor.compress(TEXT_DATA).copyOf()
        compressedData[compressedData.lastIndex] = (compressedData.last().toInt() xor 1).toByte()

        assertFailsWith<InvalidChecksumException> {
            ZlibDecompressor.decompress(compressedData)
        }
    }
}