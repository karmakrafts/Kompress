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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class GZipEntryFlagsTest {
    @Test
    fun `Boolean constructor sets only selected bits`() {
        val flags = GZipEntryFlags(ftext = true, fextra = true, fcomment = true)

        assertEquals(GZipEntryFlags.FTEXT or GZipEntryFlags.FEXTRA or GZipEntryFlags.FCOMMENT, flags.value)
        assertTrue(flags.ftext)
        assertFalse(flags.fhcrc)
        assertTrue(flags.fextra)
        assertFalse(flags.fname)
        assertTrue(flags.fcomment)
    }

    @Test
    fun `Flag accessors reflect raw bit mask`() {
        val flags = GZipEntryFlags(GZipEntryFlags.FHCRC or GZipEntryFlags.FNAME)

        assertFalse(flags.ftext)
        assertTrue(flags.fhcrc)
        assertFalse(flags.fextra)
        assertTrue(flags.fname)
        assertFalse(flags.fcomment)
    }

    @Test
    fun `computeFlags always sets header checksum and optional metadata bits`() {
        val emptyEntry = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(0), os = GZipOs.UNKNOWN
        )
        val emptyFlags = emptyEntry.computeFlags()

        assertFalse(emptyFlags.ftext)
        assertTrue(emptyFlags.fhcrc)
        assertFalse(emptyFlags.fextra)
        assertFalse(emptyFlags.fname)
        assertFalse(emptyFlags.fcomment)

        val fullEntry = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(0),
            os = GZipOs.UNIX,
            isText = true,
            name = "entry.txt",
            comment = "comment",
            extraField = byteArrayOf(0x01, 0x02)
        )
        val fullFlags = fullEntry.computeFlags()

        assertTrue(fullFlags.ftext)
        assertTrue(fullFlags.fhcrc)
        assertTrue(fullFlags.fextra)
        assertTrue(fullFlags.fname)
        assertTrue(fullFlags.fcomment)
    }
}
