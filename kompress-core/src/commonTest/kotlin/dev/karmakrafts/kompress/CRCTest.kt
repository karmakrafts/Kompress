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
import kotlin.test.Test
import kotlin.test.assertEquals

class CRCTest {
    @Test
    fun `crc32 byte array`() {
        assertEquals(0x00000000U, crc32("".encodeToByteArray()))
        assertEquals(0x83DCEFB7U, crc32("1".encodeToByteArray()))
        assertEquals(0xCBF43926U, crc32("123456789".encodeToByteArray()))
        assertEquals(0x414FA339U, crc32("The quick brown fox jumps over the lazy dog".encodeToByteArray()))
    }

    @Test
    fun `crc32 source`() {
        val buffer = Buffer()
        buffer.write("123456789".encodeToByteArray())
        assertEquals(0xCBF43926U, buffer.crc32(9))
        assertEquals(0, buffer.size) // Ensure it read the bytes
    }

    @Test
    fun `crc32 source partial`() {
        val buffer = Buffer()
        buffer.write("123456789".encodeToByteArray())
        assertEquals(0x83DCEFB7U, buffer.crc32(1))
        assertEquals(8, buffer.size)
    }

    @Test
    fun `crc32 source empty`() {
        val buffer = Buffer()
        assertEquals(0x00000000U, buffer.crc32(0))
        assertEquals(0, buffer.size)
    }
}
