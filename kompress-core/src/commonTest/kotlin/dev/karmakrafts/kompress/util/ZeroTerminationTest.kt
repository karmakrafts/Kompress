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
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(InternalCompressionApi::class)
class ZeroTerminationTest {
    @Test
    fun `bytesUntilZeroTerminator returns byte count before zero and consumes terminator`() {
        val source = Buffer().apply {
            write("hello".encodeToByteArray())
            writeByte(0.toByte())
            writeByte(0x42.toByte())
        }

        val length = source.bytesUntilZeroTerminator()

        assertEquals(5L, length)
        assertEquals(0x42.toByte(), source.readByte())
    }

    @Test
    fun `bytesUntilZeroTerminator returns zero for immediate terminator`() {
        val suffix = "tail".encodeToByteArray()
        val source = Buffer().apply {
            writeByte(0.toByte())
            write(suffix)
        }

        val length = source.bytesUntilZeroTerminator()

        assertEquals(0L, length)
        assertContentEquals(suffix, source.readByteArray())
    }

    @Test
    fun `zeroTerminate writes terminator after function output and returns result`() {
        val sink = Buffer()

        val result = sink.zeroTerminate {
            write("abc".encodeToByteArray())
            123
        }

        assertEquals(123, result)
        assertContentEquals(
            byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0), sink.readByteArray()
        )
    }
}
