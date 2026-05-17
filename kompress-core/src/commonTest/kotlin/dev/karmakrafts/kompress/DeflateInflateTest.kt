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
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DeflateInflateTest {
    @Test
    fun `deflate and inflate small array raw`() {
        val value = "Hellou, World!"
        val compressedData = Deflater.compress(value.encodeToByteArray())
        val decompressedData = Inflater.decompress(compressedData)
        assertEquals(value, decompressedData.decodeToString())
    }

    @Test
    fun `deflate and inflate small array`() {
        val value = "Hellou, World!"
        val compressedData = Deflater.compress(value.encodeToByteArray(), raw = false)
        val decompressedData = Inflater.decompress(compressedData, raw = false)
        assertEquals(value, decompressedData.decodeToString())
    }

    @Test
    fun `deflate and inflate small buffer raw`() {
        val value = "Hello, World!"
        val buffer = Buffer()
        buffer.writeString(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((buffer as RawSource).deflatingSource())
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflatingSource())
        assertEquals(value, decompressedBuffer.readString())
    }

    @Test
    fun `deflate and inflate small buffer`() {
        val value = "Hello, World!"
        val buffer = Buffer()
        buffer.writeString(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((buffer as RawSource).deflatingSource(false))
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflatingSource(false))
        assertEquals(value, decompressedBuffer.readString())
    }

    @Test
    fun `deflate and inflate large array raw`() {
        val value = Random.nextBytes(1024 * 1024)
        val compressedData = Deflater.compress(value)
        val decompressedData = Inflater.decompress(compressedData)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `deflate and inflate large array`() {
        val value = Random.nextBytes(1024 * 1024)
        val compressedData = Deflater.compress(value, raw = false)
        val decompressedData = Inflater.decompress(compressedData, raw = false)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `deflate and inflate large buffer raw`() {
        val value = Random.nextBytes(1024 * 1024)
        val buffer = Buffer()
        buffer.write(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((buffer as RawSource).deflatingSource())
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflatingSource())
        assertContentEquals(value, decompressedBuffer.readByteArray())
    }

    @Test
    fun `deflate and inflate large buffer`() {
        val value = Random.nextBytes(1024 * 1024)
        val buffer = Buffer()
        buffer.write(value)
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((buffer as RawSource).deflatingSource(false))
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflatingSource(false))
        assertContentEquals(value, decompressedBuffer.readByteArray())
    }

    @Test
    fun `deflate and inflate empty array raw`() {
        val value = byteArrayOf()
        val compressedData = Deflater.compress(value)
        val decompressedData = Inflater.decompress(compressedData)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `deflate and inflate empty array`() {
        val value = byteArrayOf()
        val compressedData = Deflater.compress(value, raw = false)
        val decompressedData = Inflater.decompress(compressedData, raw = false)
        assertContentEquals(value, decompressedData)
    }

    @Test
    fun `deflate and inflate empty buffer raw`() {
        val buffer = Buffer()
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((buffer as RawSource).deflatingSource())
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflatingSource())
        assertEquals(0, decompressedBuffer.size)
    }

    @Test
    fun `deflate and inflate empty buffer`() {
        val buffer = Buffer()
        val compressedBuffer = Buffer()
        compressedBuffer.transferFrom((buffer as RawSource).deflatingSource(false))
        val decompressedBuffer = Buffer()
        decompressedBuffer.transferFrom(compressedBuffer.inflatingSource(false))
        assertEquals(0, decompressedBuffer.size)
    }
}