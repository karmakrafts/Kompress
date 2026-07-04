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

package dev.karmakrafts.kompress.zip

import dev.karmakrafts.kompress.ExperimentalCompressionApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCompressionApi::class)
class ZipDeflateCompressionTypeTest {
    @Test
    fun `Deflate compression type constants use expected bit patterns`() {
        assertEquals(0b00U, ZipDeflateCompressionType.NORMAL.encodedValue)
        assertEquals(0b01U, ZipDeflateCompressionType.MAXIMUM.encodedValue)
        assertEquals(0b10U, ZipDeflateCompressionType.FAST.encodedValue)
        assertEquals(0b11U, ZipDeflateCompressionType.SUPER_FAST.encodedValue)
    }

    @Test
    fun `byEncodedValue resolves all known deflate compression types`() {
        ZipDeflateCompressionType.entries.forEach { type ->
            assertEquals(type, ZipDeflateCompressionType.byEncodedValue(type.encodedValue))
        }
    }

    @Test
    fun `byEncodedValue fails for unsupported encoded values`() {
        assertFailsWith<NoSuchElementException> {
            ZipDeflateCompressionType.byEncodedValue(0b100U)
        }
    }
}
