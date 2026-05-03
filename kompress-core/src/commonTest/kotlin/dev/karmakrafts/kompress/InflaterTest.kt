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

package dev.karmakrafts.kompress

import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class InflaterTest {
    @Test
    fun `Raw decompression sanity check`() {
        val data = Inflater.inflate(ubyteArrayOf(0xF3U, 0x48U, 0xCDU, 0xC9U, 0xC9U, 0x57U, 0x04U, 0x00U).asByteArray())
        assertTrue(data.isNotEmpty())
        data.forEach { println("Byte: 0x${it.toHexString()}") }
        println("Decompressed: ${data.decodeToString()}")
    }

    @Test
    fun `Decompression sanity check`() {
        val data = Inflater.inflate(
            ubyteArrayOf(
                0x78U, 0x9CU, 0xF3U, 0x48U, 0xCDU, 0xC9U, 0xC9U, 0x57U, 0x04U, 0x00U, 0x07U, 0xA2U, 0x02U, 0x16U
            ).asByteArray(), raw = false
        )
        assertTrue(data.isNotEmpty())
        data.forEach { println("Byte: 0x${it.toHexString()}") }
        println("Decompressed: ${data.decodeToString()}")
    }

    @Test
    fun `Inflating source flushes pending output when delegate reaches EOF`() {
        val value = Random(4).nextBytes(3 * 1024 * 1024)
        val compressedData = Deflater.deflate(value)
        val compressedBuffer = Buffer()
        compressedBuffer.write(compressedData)
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflating())
        val decompressedData = decompressedBuffer.readByteArray()
        assertTrue(decompressedData.isNotEmpty())
        assertEquals(value.size, decompressedData.size)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `Raw inflating source buffered immediate read`() {
        val value = "Hello, World!".encodeToByteArray()
        val compressedBytes = Deflater.deflate(value)
        val buffer = Buffer().apply { write(compressedBytes) }
        val decompressed = buffer.inflating(raw = true).buffered()
        val decompressedBytes = decompressed.readByteArray()
        assertContentEquals(value, decompressedBytes)
    }

    @Test
    fun `Inflating source buffered immediate read`() {
        val value = "Hello, World!".encodeToByteArray()
        val compressedBytes = Deflater.deflate(value, raw = false)
        val buffer = Buffer().apply { write(compressedBytes) }
        val decompressed = buffer.inflating(raw = false).buffered()
        val decompressedBytes = decompressed.readByteArray()
        assertContentEquals(value, decompressedBytes)
    }
}