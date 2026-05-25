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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class DeflaterTest {
    @Test
    fun `raw compression sanity check`() {
        val data = Deflater.compress("Hello!".encodeToByteArray())
        assertTrue(data.isNotEmpty())
        data.forEach { println("Byte: 0x${it.toHexString()}") }
    }

    @Ignore // TODO: Deflater only supports raw compression now, framing is done by Archiver
    @Test
    fun `compression sanity check`() {
        val data = Deflater.compress("Hello!".encodeToByteArray(), raw = false)
        assertTrue(data.isNotEmpty())
        data.forEach { println("Byte: 0x${it.toHexString()}") }
    }

    @Test
    fun `raw deflating source buffered immediate read`() {
        val value = "Hello, World!".encodeToByteArray()
        val source = Buffer().apply { write(value) }
        val compressed = (source as RawSource).deflatingSource(raw = true).buffered()
        val compressedBytes = compressed.readByteArray()
        val decompressedBytes = Inflater.decompress(compressedBytes, raw = true)
        assertContentEquals(value, decompressedBytes)
    }

    @Ignore // TODO: Deflater only supports raw compression now, framing is done by Archiver
    @Test
    fun `deflating source buffered immediate read`() {
        val value = "Hello, World!".encodeToByteArray()
        val source = Buffer().apply { write(value) }
        val compressed = (source as RawSource).deflatingSource(raw = false).buffered()
        val compressedBytes = compressed.readByteArray()
        val decompressedBytes = Inflater.decompress(compressedBytes, raw = false)
        assertContentEquals(value, decompressedBytes)
    }
}