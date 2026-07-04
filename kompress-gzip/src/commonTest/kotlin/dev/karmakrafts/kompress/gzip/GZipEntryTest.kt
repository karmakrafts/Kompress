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
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class GZipEntryTest {
    @Test
    fun `entries compare equal when metadata and extra field bytes match`() {
        val first = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(12),
            os = GZipOs.UNIX,
            isText = true,
            name = "entry.txt",
            comment = "test",
            extraField = byteArrayOf(0x01, 0x02)
        )
        val second = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(12),
            os = GZipOs.UNIX,
            isText = true,
            name = "entry.txt",
            comment = "test",
            extraField = byteArrayOf(0x01, 0x02)
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `entries with different extra field content are not equal`() {
        val first = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(12), os = GZipOs.UNIX, extraField = byteArrayOf(0x01, 0x02)
        )
        val second = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(12), os = GZipOs.UNIX, extraField = byteArrayOf(0x01, 0x03)
        )

        assertNotEquals(first, second)
    }

    @Test
    fun `entries with null and empty extra fields are not equal`() {
        val withNullExtra = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(0), os = GZipOs.UNKNOWN, extraField = null
        )
        val withEmptyExtra = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(0), os = GZipOs.UNKNOWN, extraField = byteArrayOf()
        )

        assertNotEquals(withNullExtra, withEmptyExtra)
    }
}