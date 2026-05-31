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

class DeflaterTest {
    private companion object {
        val TEXT_DATA: ByteArray = "Hello, DEFLATE!".encodeToByteArray()
        val BINARY_DATA: ByteArray = ByteArray(128 * 1024) { index ->
            ((index * 31 + index / 7) and 0xFF).toByte()
        }

        fun assertDeflatesTo(data: ByteArray, compressedData: ByteArray) {
            assertTrue(compressedData.isNotEmpty())
            assertContentEquals(data, Inflater.decompress(compressedData, bufferSize = 31))
        }

        fun assertGuardBytes(buffer: ByteArray, offset: Int, count: Int) {
            assertEquals(0x55.toByte(), buffer[offset - 1])
            assertEquals(0x55.toByte(), buffer[offset + count])
        }

        fun drain(deflater: Deflater, chunkSize: Int = 7, outputOffset: Int = 2): ByteArray {
            val compressedBuffer = Buffer()
            val chunk = ByteArray(outputOffset + chunkSize + 1)
            while (true) {
                chunk.fill(0x55.toByte())
                val written = deflater.compress(chunk, outputOffset, chunkSize, flush = true)
                assertGuardBytes(chunk, outputOffset, written)
                if (written == 0) break
                compressedBuffer.write(chunk, outputOffset, outputOffset + written)
            }
            return compressedBuffer.readByteArray()
        }
    }

    @Test
    fun `compressBulk updates counters`() {
        Deflater(Deflater.MAX_LEVEL).use { deflater ->
            val compressedData = deflater.compressBulk(BINARY_DATA, bufferSize = 97)
            assertDeflatesTo(BINARY_DATA, compressedData)
            assertEquals(BINARY_DATA.size.toLong(), deflater.bytesRead)
            assertEquals(compressedData.size.toLong(), deflater.bytesWritten)
        }
    }

    @Test
    fun `streaming compressor accepts repeated input chunks`() {
        val dataChunks = listOf(
            "Streaming ".encodeToByteArray(),
            "DEFLATE ".encodeToByteArray(),
            "can receive ".encodeToByteArray(),
            "input over repeated compress calls.".encodeToByteArray()
        )
        val data = dataChunks.fold(ByteArray(0)) { result, chunk -> result + chunk }
        val compressedBuffer = Buffer()
        val outputBuffer = ByteArray(4)

        Deflater(Deflater.DEFAULT_LEVEL).use { deflater ->
            for (chunk in dataChunks) {
                assertTrue(deflater.needsInput)
                deflater.setInput(chunk)
                assertFalse(deflater.needsInput)

                while (!deflater.needsInput) {
                    val written = deflater.compress(outputBuffer)
                    if (written > 0) {
                        compressedBuffer.write(outputBuffer, 0, written)
                    }
                }
            }

            deflater.finish()
            val compressedData = compressedBuffer.readByteArray() + drain(deflater, chunkSize = 4, outputOffset = 1)

            assertDeflatesTo(data, compressedData)
            assertEquals(data.size.toLong(), deflater.bytesRead)
            assertEquals(compressedData.size.toLong(), deflater.bytesWritten)
            assertTrue(deflater.finished)
        }
    }

    @Test
    fun `streaming compressor handles offset input and can be reset`() {
        val prefix = byteArrayOf(1, 2, 3)
        val suffix = byteArrayOf(4, 5)
        val paddedData = prefix + TEXT_DATA + suffix

        Deflater(Deflater.MIN_LEVEL).use { deflater ->
            deflater.level = Deflater.DEFAULT_LEVEL
            assertEquals(Deflater.DEFAULT_LEVEL, deflater.level)
            assertTrue(deflater.needsInput)
            assertFalse(deflater.finished)

            deflater.setInput(paddedData, offset = prefix.size, size = TEXT_DATA.size)
            assertEquals(prefix.size, deflater.inputOffset)
            assertEquals(TEXT_DATA.size, deflater.remaining)
            assertFalse(deflater.needsInput)

            deflater.finish()
            val compressedData = drain(deflater)
            assertDeflatesTo(TEXT_DATA, compressedData)
            assertEquals(TEXT_DATA.size.toLong(), deflater.bytesRead)
            assertEquals(compressedData.size.toLong(), deflater.bytesWritten)
            assertTrue(deflater.finished)

            deflater.reset()
            assertEquals(0L, deflater.bytesRead)
            assertEquals(0L, deflater.bytesWritten)
            assertFalse(deflater.finished)
            assertTrue(deflater.needsInput)

            val reusedData = "Reusable deflater".encodeToByteArray()
            val reusedCompressedData = deflater.compressBulk(reusedData, bufferSize = 16)
            assertDeflatesTo(reusedData, reusedCompressedData)
        }
    }

    @Test
    fun `source and sink wrappers compress streaming data`() {
        val source = Buffer().apply { write(BINARY_DATA) }
        val compressedSource =
            (source as RawSource).deflatingSource(level = Deflater.DEFAULT_LEVEL, bufferSize = 123).buffered()
        assertDeflatesTo(BINARY_DATA, compressedSource.readByteArray())

        val compressedBuffer = Buffer()
        val deflatingSink = compressedBuffer.deflatingSink(level = Deflater.MIN_LEVEL, bufferSize = 11)
        val sinkSource = Buffer().apply { write(TEXT_DATA) }

        deflatingSink.write(sinkSource, 5)
        deflatingSink.flush()
        deflatingSink.write(sinkSource, sinkSource.size)
        deflatingSink.close()

        assertDeflatesTo(TEXT_DATA, compressedBuffer.readByteArray())
    }
}