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

class ZlibCMFTest {
    @Test
    fun `default cmf uses default lz77 window size in bytes`() {
        val cmf = ZlibCMF()

        assertEquals(0x78.toUByte(), cmf.value)
        assertEquals(32 * 1024, cmf.windowSize)
    }

    @Test
    fun `cmf constructor interprets window size as bytes`() {
        val cmf = ZlibCMF(windowSize = 4096)

        assertEquals(0x48.toUByte(), cmf.value)
        assertEquals(ZlibCompressionMethod.DEFLATE, cmf.compressionMethod)
        assertEquals(4096, cmf.windowSize)
    }

    @Test
    fun `cmf rejects non power of two window size in bytes`() {
        assertFailsWith<IllegalArgumentException> {
            ZlibCMF(windowSize = 30 * 1024)
        }
    }

    @Test
    fun `cmf rejects window sizes below 256 bytes`() {
        assertFailsWith<IllegalArgumentException> {
            ZlibCMF(windowSize = 128)
        }
    }

    @Test
    fun `cmf rejects window sizes above 32768 bytes`() {
        assertFailsWith<IllegalArgumentException> {
            ZlibCMF(windowSize = 64 * 1024)
        }
    }

    @Test
    fun `cmf value constructor decodes compression method and window size`() {
        val cmf = ZlibCMF(0x28U)

        assertEquals(ZlibCompressionMethod.DEFLATE, cmf.compressionMethod)
        assertEquals(1024, cmf.windowSize)
    }
}