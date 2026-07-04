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

package dev.karmakrafts.kompress.zlib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZlibFlagsTest {
    @Test
    fun `flags constructor encodes level and dictionary flag`() {
        val flags = ZlibFlags(level = ZlibCompressionLevel.MAXIMUM, hasDictionary = true)

        assertEquals(0xE0.toUByte(), flags.fields)
        assertEquals(ZlibCompressionLevel.MAXIMUM, flags.level)
        assertTrue(flags.hasDictionary)
    }

    @Test
    fun `flags value constructor decodes level and dictionary flag`() {
        val flags = ZlibFlags(0xA0U)

        assertEquals(ZlibCompressionLevel.DEFAULT, flags.level)
        assertTrue(flags.hasDictionary)
    }

    @Test
    fun `withCheckBits makes header divisible by 31 and keeps level bits`() {
        val cmf = ZlibCMF()
        val flags = ZlibFlags(level = ZlibCompressionLevel.FAST)

        val withCheckBits = flags.withCheckBits(cmf)
        val normalizedFlags = ZlibFlags(withCheckBits)
        val header = (cmf.value.toUInt() shl 8) or withCheckBits.toUInt()

        assertEquals(0U, header % 31U)
        assertEquals(ZlibCompressionLevel.FAST, normalizedFlags.level)
        assertFalse(normalizedFlags.hasDictionary)
    }

    @Test
    fun `withCheckBits keeps dictionary bit`() {
        val cmf = ZlibCMF(windowSize = 4096)
        val flags = ZlibFlags(level = ZlibCompressionLevel.FASTEST, hasDictionary = true)

        val withCheckBits = flags.withCheckBits(cmf)
        val normalizedFlags = ZlibFlags(withCheckBits)
        val header = (cmf.value.toUInt() shl 8) or withCheckBits.toUInt()

        assertEquals(0U, header % 31U)
        assertEquals(ZlibCompressionLevel.FASTEST, normalizedFlags.level)
        assertTrue(normalizedFlags.hasDictionary)
    }
}