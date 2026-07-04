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

package dev.karmakrafts.kompress.util

import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.exception.DataFormatException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

@OptIn(InternalCompressionApi::class)
class CP437StringUtilsTest {
    @Test
    fun `encodeToCP437 encodes ascii and extended cp437 characters`() {
        val cp437Text = "Hi-ÇüÑ"

        val encoded = cp437Text.encodeToCP437()

        assertContentEquals(
            byteArrayOf(
                'H'.code.toByte(), 'i'.code.toByte(), '-'.code.toByte(), 0x80.toByte(), 0x81.toByte(), 0xA5.toByte()
            ), encoded
        )
    }

    @Test
    fun `encodeToCP437 fails for characters outside cp437 range`() {
        assertFailsWith<DataFormatException> {
            "emoji-🙂".encodeToCP437()
        }
    }

    @Test
    fun `writeCP437String writes cp437 encoded bytes`() {
        val sink = Buffer()

        sink.writeCP437String("Añÿ")

        assertContentEquals(
            byteArrayOf(
                'A'.code.toByte(), 0xA4.toByte(), 0x98.toByte()
            ), sink.readByteArray()
        )
    }
}