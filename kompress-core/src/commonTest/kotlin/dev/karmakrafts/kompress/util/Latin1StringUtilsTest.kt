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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalCompressionApi::class)
class Latin1StringUtilsTest {
    @Test
    fun `encodeToLatin1 encodes all latin1 bytes and decodeFromLatin1 restores the string`() {
        val latin1Text = "Hello-äöüÿ"

        val encoded = latin1Text.encodeToLatin1()

        assertContentEquals(
            byteArrayOf(
                'H'.code.toByte(),
                'e'.code.toByte(),
                'l'.code.toByte(),
                'l'.code.toByte(),
                'o'.code.toByte(),
                '-'.code.toByte(),
                0xE4.toByte(),
                0xF6.toByte(),
                0xFC.toByte(),
                0xFF.toByte()
            ), encoded
        )
        assertEquals(latin1Text, encoded.decodeFromLatin1())
    }

    @Test
    fun `encodeToLatin1 fails for characters outside latin1 range`() {
        assertFailsWith<DataFormatException> {
            "emoji-🙂".encodeToLatin1()
        }
    }

    @Test
    fun `writeLatin1String writes latin1 encoded bytes`() {
        val sink = Buffer()

        sink.writeLatin1String("Àbÿ")

        assertContentEquals(byteArrayOf(0xC0.toByte(), 'b'.code.toByte(), 0xFF.toByte()), sink.readByteArray())
    }

    @Test
    fun `readLatin1String reads explicit size and consumes following terminator byte`() {
        val source = Buffer().apply {
            write(byteArrayOf('n'.code.toByte(), 0xE7.toByte(), 0xF1.toByte(), 0x00, 0x42.toByte()))
        }

        val value = source.readLatin1String(size = 3)

        assertEquals("nçñ", value)
        assertEquals(0x42.toByte(), source.readByte())
    }

    @Test
    fun `readLatin1String infers size from zero terminator`() {
        val source = Buffer().apply {
            write(byteArrayOf(0xF4.toByte(), 0xE1.toByte(), 0x6C, 0x00, 0x21))
        }

        val value = source.readLatin1String()

        assertEquals("ôál", value)
        assertEquals(0x21.toByte(), source.readByte())
    }
}