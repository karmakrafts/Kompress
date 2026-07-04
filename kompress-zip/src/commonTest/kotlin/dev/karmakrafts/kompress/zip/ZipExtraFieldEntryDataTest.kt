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

@OptIn(ExperimentalCompressionApi::class)
class ZipExtraFieldEntryDataTest {
    @Test
    fun `Zip64 size is fixed and matches the encoded payload length`() {
        assertEquals((ULong.SIZE_BYTES * 3 + UInt.SIZE_BYTES).toLong(), ZipExtraFieldEntryData.Zip64.SIZE)

        val payload = ZipExtraFieldEntryData.Zip64(
            uncompressedSize = 13UL, compressedSize = 7UL, lhrOffset = 1234UL, startDiskIndex = 0U
        )

        assertEquals(ZipExtraFieldEntryData.Zip64.SIZE, payload.size)
    }

    @Test
    fun `Zip64 encode and decode roundtrip preserves all fields`() {
        val expected = ZipExtraFieldEntryData.Zip64(
            uncompressedSize = 4_294_967_297UL, compressedSize = 4_294_967_296UL, lhrOffset = 42UL, startDiskIndex = 5U
        )
        val buffer = Buffer()

        expected.encode(buffer)

        val decoded = ZipExtraFieldEntryData.Zip64.decode(buffer)
        assertEquals(expected, decoded)
    }
}
