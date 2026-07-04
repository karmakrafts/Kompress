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

import dev.karmakrafts.kompress.compressingSource
import dev.karmakrafts.kompress.decompressingSource
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZlibArchiverUnarchiverTest {
    private companion object {
        fun compress(vararg chunks: ByteArray): ByteArray {
            val inputBuffer = Buffer()
            chunks.forEach { chunk ->
                inputBuffer.write(chunk)
            }
            val compressedBuffer = Buffer()
            (inputBuffer as RawSource).compressingSource(ZlibCompressor(), bufferSize = 97).use { source ->
                compressedBuffer.transferFrom(source)
            }
            return compressedBuffer.readByteArray()
        }

        fun decompress(compressedData: ByteArray): ByteArray {
            val inputBuffer = Buffer()
            inputBuffer.write(compressedData)

            val outputBuffer = Buffer()
            (inputBuffer as RawSource).decompressingSource(ZlibDecompressor(), bufferSize = 89).use { source ->
                var read = source.readAtMostTo(outputBuffer, 64)
                while (read != -1L) {
                    read = source.readAtMostTo(outputBuffer, 64)
                }
            }
            return outputBuffer.readByteArray()
        }
    }

    @Test
    fun `archive and unarchive text payload preserves content`() {
        val payload = "HELLO, WORLD!".encodeToByteArray()

        val compressedData = compress(payload)
        val decompressedData = decompress(compressedData)

        assertContentEquals(payload, decompressedData)
    }

    @Test
    fun `archive and unarchive multiple text chunks preserves order and data`() {
        val payload1 = "HELLO, WORLD!".encodeToByteArray()
        val payload2 = "The fox goes yap!".encodeToByteArray()
        val expectedPayload = payload1 + payload2

        val compressedData = compress(payload1, payload2)
        val decompressedData = decompress(compressedData)

        assertContentEquals(expectedPayload, decompressedData)
        assertEquals("HELLO, WORLD!The fox goes yap!", decompressedData.decodeToString())
    }

    @Test
    fun `archive and unarchive supports latin-1 bytes`() {
        val latin1Payload = byteArrayOf(
            0x74.toByte(),
            0xE4.toByte(),
            0x73.toByte(),
            0x74.toByte(),
            0x2D.toByte(),
            0x6C.toByte(),
            0xE2.toByte(),
            0x74.toByte(),
            0xEC.toByte(),
            0x6E.toByte(),
            0x31.toByte(),
            0x2D.toByte(),
            0xFF.toByte()
        )

        val compressedData = compress(latin1Payload)
        val decompressedData = decompress(compressedData)

        assertContentEquals(latin1Payload, decompressedData)
    }

    @Test
    fun `archive and unarchive empty payload keeps zero-length output`() {
        val compressedData = compress(byteArrayOf())
        val decompressedData = decompress(compressedData)

        assertEquals(0, decompressedData.size)
    }
}
