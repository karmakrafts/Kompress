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
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompressionApi::class)
class ZipExtraFieldContainerTest {
    @Test
    fun `empty creates mutable container with zero byte size`() {
        val container = ZipExtraFieldContainer.empty()

        assertTrue(container.isEmpty())
        assertEquals(0L, container.byteSize)
    }

    @Test
    fun `byteSize sums sizes of all contained entries`() {
        val entryA = ZipExtraFieldEntry(
            ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, ZipExtraFieldEntryData.Zip64(1UL, 1UL, 1UL, 0U)
        )
        val entryB = ZipExtraFieldEntry(
            ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, ZipExtraFieldEntryData.Zip64(2UL, 2UL, 2UL, 0U)
        )
        val container = ZipExtraFieldContainer.empty().apply {
            add(entryA)
            add(entryB)
        }

        assertEquals(entryA.size + entryB.size, container.byteSize)
    }

    @Test
    fun `encode writes all entries in insertion order`() {
        val first = ZipExtraFieldEntry(
            ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, ZipExtraFieldEntryData.Zip64(10UL, 9UL, 8UL, 0U)
        )
        val second = ZipExtraFieldEntry(
            ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION, ZipExtraFieldEntryData.Zip64(7UL, 6UL, 5UL, 1U)
        )
        val container = ZipExtraFieldContainer.empty().apply {
            add(first)
            add(second)
        }
        val expected = Buffer().also {
            first.encode(it)
            second.encode(it)
        }
        val actual = Buffer()

        container.encode(actual)

        assertContentEquals(expected.readByteArray(), actual.readByteArray())
    }
}
