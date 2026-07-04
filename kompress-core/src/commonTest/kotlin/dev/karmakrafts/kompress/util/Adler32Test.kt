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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalCompressionApi::class)
class Adler32Test {
    @Test
    fun `initial checksum is one`() {
        val adler32 = Adler32()
        assertEquals(1u, adler32.checksum)
    }

    @Test
    fun `round single byte updates checksum`() {
        val adler32 = Adler32()
        adler32.round('a'.code.toByte())
        assertEquals(0x0062_0062u, adler32.checksum)
    }

    @Test
    fun `round byte array computes known checksum`() {
        val adler32 = Adler32()
        adler32.round("Wikipedia".encodeToByteArray())
        assertEquals(0x11E6_0398u, adler32.checksum)
    }

    @Test
    fun `round with offset and size processes only selected subrange`() {
        val bytes = "xxWikipediayy".encodeToByteArray()
        val withRange = Adler32()
        withRange.round(bytes, offset = 2, size = 9)

        val reference = Adler32()
        for (index in 2..10) reference.round(bytes[index])

        assertEquals(reference.checksum, withRange.checksum)
        assertEquals(0x11E6_0398u, withRange.checksum)
    }

    @Test
    fun `round with zero size keeps checksum unchanged`() {
        val adler32 = Adler32()
        adler32.round("seed".encodeToByteArray())
        val before = adler32.checksum

        adler32.round("ignored".encodeToByteArray(), offset = 2, size = 0)

        assertEquals(before, adler32.checksum)
    }

    @Test
    fun `reset returns to initial state`() {
        val adler32 = Adler32()
        adler32.round("hello world".encodeToByteArray())
        adler32.reset()
        assertEquals(1u, adler32.checksum)

        adler32.round("abc".encodeToByteArray())
        assertEquals(0x024D_0127u, adler32.checksum)
    }

    @Test
    fun `custom mod constructor changes accumulation behavior`() {
        val adler32 = Adler32(mod = 7)
        adler32.round(byteArrayOf(1, 2, 3))
        assertEquals(0x0006_0000u, adler32.checksum)
    }
}