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
import kotlinx.io.Buffer
import kotlinx.io.writeUShortLe
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompressionApi::class)
class ZipExtraFieldEntryTest {
    @Test
    fun `size includes header id size field and payload`() {
        val entry = ZipExtraFieldEntry(
            headerId = ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, data = ZipExtraFieldEntryData.Zip64(
                uncompressedSize = 1UL, compressedSize = 1UL, lhrOffset = 1UL, startDiskIndex = 0U
            )
        )

        assertEquals(UShort.SIZE_BYTES.toLong() * 2 + ZipExtraFieldEntryData.Zip64.SIZE, entry.size)
    }

    @Test
    fun `encode and decode roundtrip keeps header id and payload`() {
        val expected = ZipExtraFieldEntry(
            headerId = ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, data = ZipExtraFieldEntryData.Zip64(
                uncompressedSize = 12UL, compressedSize = 11UL, lhrOffset = 10UL, startDiskIndex = 9U
            )
        )
        val buffer = Buffer()

        expected.encode(buffer)

        val decoded = ZipExtraFieldEntry.decode(buffer)
        assertEquals(expected, decoded)
    }

    @Test
    fun `decode ignores encoded data size and uses parser for known header`() {
        val expectedData = ZipExtraFieldEntryData.Zip64(
            uncompressedSize = 9UL, compressedSize = 8UL, lhrOffset = 7UL, startDiskIndex = 6U
        )
        val buffer = Buffer()
        buffer.writeUShortLe(ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION.encodedValue)
        buffer.writeUShortLe(0U) // Declared size is intentionally mismatched.
        expectedData.encode(buffer)

        val decoded = ZipExtraFieldEntry.decode(buffer)

        assertEquals(ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, decoded.headerId)
        assertEquals(expectedData, decoded.data)
    }
}
