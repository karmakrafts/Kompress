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

import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kplatform.OsFamily
import dev.karmakrafts.kplatform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalCompressionApi::class)
class GZipOsTest {
    @Test
    fun `byEncodedValue resolves all known OS ids`() {
        GZipOs.entries.forEach { os ->
            assertEquals(os, GZipOs.byEncodedValue(os.encodedValue))
        }
    }

    @Test
    fun `byEncodedValue fails for unsupported OS ids`() {
        assertFailsWith<NoSuchElementException> {
            GZipOs.byEncodedValue(0x10U)
        }
    }

    @Test
    fun `guessCurrent maps current platform to RFC1952 OS id`() {
        val family = Platform.os.family
        val expected = when {
            family == OsFamily.WINDOWS -> GZipOs.NTFS
            family.isApple -> GZipOs.MACINTOSH
            family.isUnixoid -> GZipOs.UNIX
            else -> GZipOs.UNKNOWN
        }

        assertEquals(expected, GZipOs.guessCurrent())
    }
}