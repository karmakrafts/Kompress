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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalCompressionApi::class)
class ZipExtraFieldEntryHeaderIdTest {
    @Test
    fun `byEncodedValue resolves ZIP64 extended information header`() {
        val headerId = ZipExtraFieldEntryHeaderId.byEncodedValue(0x0001U)

        assertEquals(ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, headerId)
    }

    @Test
    fun `byEncodedValue fails for unsupported header ids`() {
        assertFailsWith<NoSuchElementException> {
            ZipExtraFieldEntryHeaderId.byEncodedValue(0x0002U)
        }
    }

    @Test
    fun `parse delegates to configured parser`() {
        val expected = ZipExtraFieldEntryData.Zip64(
            uncompressedSize = 123UL, compressedSize = 120UL, lhrOffset = 99UL, startDiskIndex = 1U
        )
        val buffer = Buffer()
        expected.encode(buffer)

        val parsed = ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION.parse(buffer)

        assertIs<ZipExtraFieldEntryData.Zip64>(parsed)
        assertEquals(expected, parsed)
    }
}
