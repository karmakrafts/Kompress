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

package dev.karmakrafts.kompress.gzip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GZipCompressionMethodTest {
    @Test
    fun `compression method constants use expected RFC1952 value`() {
        assertEquals(0x08U, GZipCompressionMethod.DEFLATE.encodedValue)
    }

    @Test
    fun `byEncodedValue resolves all known compression methods`() {
        GZipCompressionMethod.entries.forEach { method ->
            assertEquals(method, GZipCompressionMethod.byEncodedValue(method.encodedValue))
        }
    }

    @Test
    fun `byEncodedValue fails for unsupported compression method ids`() {
        assertFailsWith<NoSuchElementException> {
            GZipCompressionMethod.byEncodedValue(0x00U)
        }
    }
}