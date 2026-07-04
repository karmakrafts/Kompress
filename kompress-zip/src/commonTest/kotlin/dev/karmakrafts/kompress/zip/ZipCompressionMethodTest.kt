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
class ZipCompressionMethodTest {
    @Test
    fun `Compression method constants use expected PKWARE values`() {
        assertEquals(0x0000U, ZipCompressionMethod.NONE.encodedValue)
        assertEquals(0x0008U, ZipCompressionMethod.DEFLATE.encodedValue)
        assertEquals(0x000CU, ZipCompressionMethod.BZIP2.encodedValue)
        assertEquals(0x000EU, ZipCompressionMethod.LZMA.encodedValue)
        assertEquals(0x005DU, ZipCompressionMethod.ZSTD.encodedValue)
        assertEquals(0x005FU, ZipCompressionMethod.XZ.encodedValue)
    }

    @Test
    fun `byEncodedValue resolves all known compression methods`() {
        ZipCompressionMethod.entries.forEach { method ->
            assertEquals(method, ZipCompressionMethod.byEncodedValue(method.encodedValue))
        }
    }

    @Test
    fun `byEncodedValue fails for unsupported method ids`() {
        assertFailsWith<NoSuchElementException> {
            ZipCompressionMethod.byEncodedValue(0x0001U)
        }
    }
}
