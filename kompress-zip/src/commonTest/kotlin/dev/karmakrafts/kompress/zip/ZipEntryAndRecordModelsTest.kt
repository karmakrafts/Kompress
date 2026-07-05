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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCompressionApi::class)
class ZipEntryAndRecordModelsTest {
    @Test
    fun `ZipEntry defaults to no comment DEFLATE and non-ZIP64`() {
        val entry = ZipEntry(
            modificationTime = Instant.fromEpochSeconds(0), name = "entry.bin"
        )

        assertNull(entry.comment)
        assertEquals(ZipCompressionMethod.DEFLATE, entry.compressionMethod)
        assertEquals(ZipGPBF(), entry.gpbf)
        assertFalse(entry.isZip64)
    }

    @Test
    fun `ZipEntry isZip64 is true when ZIP64 extra field is present`() {
        val zip64Field = ZipExtraFieldEntry(
            headerId = ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, data = ZipExtraFieldEntryData.Zip64(
                uncompressedSize = 100UL, compressedSize = 80UL, lhrOffset = 7UL, startDiskIndex = 0U
            )
        )
        val entry = ZipEntry(
            modificationTime = Instant.fromEpochSeconds(0),
            name = "entry.bin",
            extraFields = ZipExtraFieldContainer.empty().apply { add(zip64Field) })

        assertTrue(entry.isZip64)
    }

    @Test
    fun `ZipDataDescriptor stores checksum and data sizes`() {
        val descriptor = ZipDataDescriptor(
            checksum = 0xCAFE_BABEU, compressedSize = 120L, uncompressedSize = 256L
        )

        assertEquals(0xCAFE_BABEU, descriptor.checksum)
        assertEquals(120L, descriptor.compressedSize)
        assertEquals(256L, descriptor.uncompressedSize)
    }

    @Test
    fun `ZipLocalFileHeader stores entry and size metadata`() {
        val entry = ZipEntry(
            modificationTime = Instant.fromEpochSeconds(0), name = "header.txt"
        )
        val header = ZipLocalFileHeader(
            entry = entry, checksum = 0x1234_5678U, compressedSize = 10L, uncompressedSize = 20L
        )

        assertEquals(entry, header.entry)
        assertEquals(0x1234_5678U, header.checksum)
        assertEquals(10L, header.compressedSize)
        assertEquals(20L, header.uncompressedSize)
    }
}
