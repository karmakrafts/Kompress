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
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeflaterTest {
    @Test
    fun `raw compression sanity check`() {
        val data = Deflater.compress("Hello!".encodeToByteArray())
        assertTrue(data.isNotEmpty())
        data.forEach { println("Byte: 0x${it.toHexString()}") }
    }

    @Test
    fun `compression sanity check`() {
        val data = Deflater.compress("Hello!".encodeToByteArray(), raw = false)
        assertTrue(data.isNotEmpty())
        data.forEach { println("Byte: 0x${it.toHexString()}") }
    }

    @Test
    fun `deflating source flushes pending output when delegate reaches eof`() {
        val value = Random(4).nextBytes(3 * 1024 * 1024)
        val sourceBuffer = Buffer()
        sourceBuffer.write(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((sourceBuffer as RawSource).deflating())
        val compressedData = compressedBuffer.readByteArray()
        val decompressedData = Inflater.decompress(compressedData)
        assertTrue(compressedData.isNotEmpty())
        assertEquals(value.size, decompressedData.size)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `raw deflating source buffered immediate read`() {
        val value = "Hello, World!".encodeToByteArray()
        val source = Buffer().apply { write(value) }
        val compressed = (source as RawSource).deflating(raw = true).buffered()
        val compressedBytes = compressed.readByteArray()
        val decompressedBytes = Inflater.decompress(compressedBytes, raw = true)
        assertContentEquals(value, decompressedBytes)
    }

    @Test
    fun `deflating source buffered immediate read`() {
        val value = "Hello, World!".encodeToByteArray()
        val source = Buffer().apply { write(value) }
        val compressed = (source as RawSource).deflating(raw = false).buffered()
        val compressedBytes = compressed.readByteArray()
        val decompressedBytes = Inflater.decompress(compressedBytes, raw = false)
        assertContentEquals(value, decompressedBytes)
    }
}