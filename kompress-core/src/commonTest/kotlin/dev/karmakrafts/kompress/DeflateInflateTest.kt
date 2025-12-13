/*
 * Copyright (C) 2025 Karma Krafts & associates
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
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeflateInflateTest {
    @Test
    fun deflate() {
        val value = "Hellou, World!"
        val compressedData = Deflater.deflate(value.encodeToByteArray())
        assertTrue(compressedData.isNotEmpty())
    }

    @Test
    fun `Deflate and inflate small array raw`() {
        val value = "Hellou, World!"
        val compressedData = Deflater.deflate(value.encodeToByteArray())
        val decompressedData = Inflater.inflate(compressedData)
        assertEquals(value, decompressedData.decodeToString())
    }

    @Test
    fun `Deflate and inflate small array`() {
        val value = "Hellou, World!"
        val compressedData = Deflater.deflate(value.encodeToByteArray(), raw = false)
        val decompressedData = Inflater.inflate(compressedData, raw = false)
        assertEquals(value, decompressedData.decodeToString())
    }

    @Test
    fun `Deflate and inflate small buffer raw`() {
        val value = "Hello, World!"
        val buffer = Buffer()
        buffer.writeString(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom(buffer.deflating())
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflating())
        assertEquals(value, decompressedBuffer.readString())
    }

    @Test
    fun `Deflate and inflate small buffer`() {
        val value = "Hello, World!"
        val buffer = Buffer()
        buffer.writeString(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom(buffer.deflating(false))
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflating(false))
        assertEquals(value, decompressedBuffer.readString())
    }

    @Test
    fun `Deflate and inflate large array raw`() {
        val value = Random.nextBytes(1024 * 1024 * 32)
        val compressedData = Deflater.deflate(value)
        val decompressedData = Inflater.inflate(compressedData)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `Deflate and inflate large array`() {
        val value = Random.nextBytes(1024 * 1024 * 32)
        val compressedData = Deflater.deflate(value, raw = false)
        val decompressedData = Inflater.inflate(compressedData, raw = false)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `Deflate and inflate large buffer raw`() {
        val value = Random.nextBytes(1024 * 1024 * 128)
        val buffer = Buffer()
        buffer.write(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom(buffer.deflating())
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflating())
        assertContentEquals(value, decompressedBuffer.readByteArray())
    }

    @Test
    fun `Deflate and inflate large buffer`() {
        val value = Random.nextBytes(1024 * 1024 * 128)
        val buffer = Buffer()
        buffer.write(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom(buffer.deflating(false))
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflating(false))
        assertContentEquals(value, decompressedBuffer.readByteArray())
    }
}