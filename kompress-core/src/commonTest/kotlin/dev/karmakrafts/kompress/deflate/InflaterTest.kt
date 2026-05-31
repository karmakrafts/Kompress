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

package dev.karmakrafts.kompress.deflate

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InflaterTest {
    private companion object {
        val TEXT_DATA: ByteArray = "Hello!".encodeToByteArray()
        val KNOWN_DEFLATE_DATA: ByteArray = byteArrayOf(
            0xF3.toByte(), 0x48, 0xCD.toByte(), 0xC9.toByte(), 0xC9.toByte(), 0x57, 0x04, 0x00
        )
        val BINARY_DATA: ByteArray = ByteArray(128 * 1024) { index ->
            ((index * 17 + index / 5) and 0xFF).toByte()
        }
        val TRAILER: ByteArray = ByteArray(128) { index -> (255 - index).toByte() }

        fun deflate(data: ByteArray): ByteArray = Deflater.compress(
            data, level = Deflater.DEFAULT_LEVEL, bufferSize = 67
        )

        fun assertGuardBytes(buffer: ByteArray, offset: Int, count: Int) {
            assertEquals(0x55.toByte(), buffer[offset - 1])
            assertEquals(0x55.toByte(), buffer[offset + count])
        }

        fun drain(inflater: Inflater, chunkSize: Int = 5, outputOffset: Int = 2): ByteArray {
            val decompressedBuffer = Buffer()
            val chunk = ByteArray(outputOffset + chunkSize + 1)
            while (true) {
                chunk.fill(0x55.toByte())
                val read = inflater.decompress(chunk, outputOffset, chunkSize, flush = true)
                assertGuardBytes(chunk, outputOffset, read)
                if (read == 0) break
                decompressedBuffer.write(chunk, outputOffset, outputOffset + read)
            }
            return decompressedBuffer.readByteArray()
        }
    }

    @Test
    fun `computeCompressedSize stops before trailing data`() {
        val compressedData = deflate(BINARY_DATA)
        val combinedData = compressedData + TRAILER

        assertEquals(compressedData.size, Inflater.computeCompressedSize(combinedData, bufferSize = 29))

        val source = Buffer().apply { write(combinedData) }
        assertEquals(compressedData.size.toLong(), Inflater.computeCompressedSize(source.peek(), bufferSize = 29))
        assertEquals(combinedData.size.toLong(), source.size)
    }

    @Test
    fun `decompressBulk updates counters`() {
        val compressedData = deflate(BINARY_DATA)

        Inflater().use { inflater ->
            val decompressedData = inflater.decompressBulk(compressedData, bufferSize = 97)
            assertContentEquals(BINARY_DATA, decompressedData)
            assertEquals(compressedData.size.toLong(), inflater.bytesRead)
            assertEquals(BINARY_DATA.size.toLong(), inflater.bytesWritten)
        }
    }

    @Test
    fun `streaming decompressor accepts repeated input chunks`() {
        val compressedData = deflate(BINARY_DATA)
        val decompressedBuffer = Buffer()
        val outputBuffer = ByteArray(37)

        Inflater().use { inflater ->
            var inputOffset = 0
            while (!inflater.finished) {
                if (inflater.needsInput) {
                    if (inputOffset == compressedData.size) {
                        inflater.finish()
                    }
                    else {
                        val inputSize = 11.coerceAtMost(compressedData.size - inputOffset)
                        inflater.setInput(compressedData, inputOffset, inputSize)
                        inputOffset += inputSize
                    }
                }

                val read = inflater.decompress(outputBuffer)
                if (read > 0) {
                    decompressedBuffer.write(outputBuffer, 0, read)
                }
            }

            assertEquals(compressedData.size, inputOffset)
            assertEquals(0, inflater.remaining)
            assertContentEquals(BINARY_DATA, decompressedBuffer.readByteArray())
            assertEquals(compressedData.size.toLong(), inflater.bytesRead)
            assertEquals(BINARY_DATA.size.toLong(), inflater.bytesWritten)
        }
    }

    @Test
    fun `streaming decompressor handles offset input and can be reset`() {
        val compressedData = deflate(TEXT_DATA)
        val prefix = byteArrayOf(1, 2, 3)
        val suffix = byteArrayOf(4, 5)
        val paddedData = prefix + compressedData + suffix

        Inflater().use { inflater ->
            assertTrue(inflater.needsInput)
            assertFalse(inflater.finished)

            inflater.setInput(paddedData, offset = prefix.size, size = compressedData.size)
            assertEquals(prefix.size, inflater.inputOffset)
            assertEquals(compressedData.size, inflater.remaining)
            assertFalse(inflater.needsInput)

            inflater.finish()
            val decompressedData = drain(inflater)
            assertContentEquals(TEXT_DATA, decompressedData)
            assertEquals(compressedData.size.toLong(), inflater.bytesRead)
            assertEquals(TEXT_DATA.size.toLong(), inflater.bytesWritten)
            assertTrue(inflater.finished)

            inflater.reset()
            assertEquals(0L, inflater.bytesRead)
            assertEquals(0L, inflater.bytesWritten)
            assertFalse(inflater.finished)
            assertTrue(inflater.needsInput)

            val reusedCompressedData = deflate(BINARY_DATA)
            val reusedData = inflater.decompressBulk(reusedCompressedData, bufferSize = 113)
            assertContentEquals(BINARY_DATA, reusedData)
        }
    }

    @Test
    fun `source and sink wrappers decompress streaming data`() {
        val compressedData = deflate(BINARY_DATA)
        val source = Buffer().apply { write(compressedData) }
        val inflatingSource = (source as RawSource).inflatingSource(bufferSize = 127).buffered()

        assertContentEquals(BINARY_DATA, inflatingSource.readByteArray())

        val compressedText = deflate(TEXT_DATA)
        val decompressedBuffer = Buffer()
        val inflatingSink = decompressedBuffer.inflatingSink(bufferSize = 7)
        val sinkSource = Buffer().apply { write(compressedText) }

        inflatingSink.write(sinkSource, compressedText.size / 2L)
        inflatingSink.flush()
        inflatingSink.write(sinkSource, sinkSource.size)
        inflatingSink.close()

        assertContentEquals(TEXT_DATA, decompressedBuffer.readByteArray())
    }
}