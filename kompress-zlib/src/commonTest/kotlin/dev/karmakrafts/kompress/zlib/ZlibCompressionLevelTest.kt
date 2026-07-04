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
import kotlin.test.assertFailsWith

class ZlibCompressionLevelTest {
    @Test
    fun `byEncodedValue resolves all known compression levels`() {
        ZlibCompressionLevel.entries.forEach { level ->
            assertEquals(level, ZlibCompressionLevel.byEncodedValue(level.encodedValue))
        }
    }

    @Test
    fun `byEncodedValue fails for unsupported compression level ids`() {
        assertFailsWith<NoSuchElementException> {
            ZlibCompressionLevel.byEncodedValue(0x04U)
        }
    }

    @Test
    fun `fromDeflaterLevel maps all supported level ranges`() {
        assertEquals(ZlibCompressionLevel.FASTEST, ZlibCompressionLevel.fromDeflaterLevel(0))
        assertEquals(ZlibCompressionLevel.FASTEST, ZlibCompressionLevel.fromDeflaterLevel(1))
        assertEquals(ZlibCompressionLevel.FAST, ZlibCompressionLevel.fromDeflaterLevel(2))
        assertEquals(ZlibCompressionLevel.FAST, ZlibCompressionLevel.fromDeflaterLevel(3))
        assertEquals(ZlibCompressionLevel.DEFAULT, ZlibCompressionLevel.fromDeflaterLevel(4))
        assertEquals(ZlibCompressionLevel.DEFAULT, ZlibCompressionLevel.fromDeflaterLevel(6))
        assertEquals(ZlibCompressionLevel.MAXIMUM, ZlibCompressionLevel.fromDeflaterLevel(7))
        assertEquals(ZlibCompressionLevel.MAXIMUM, ZlibCompressionLevel.fromDeflaterLevel(9))
    }

    @Test
    fun `fromDeflaterLevel fails for unsupported levels`() {
        assertFailsWith<IllegalStateException> {
            ZlibCompressionLevel.fromDeflaterLevel(-1)
        }
        assertFailsWith<IllegalStateException> {
            ZlibCompressionLevel.fromDeflaterLevel(10)
        }
    }
}