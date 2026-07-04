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

@OptIn(ExperimentalCompressionApi::class)
class ZipConstantsTest {
    @Test
    fun `makeZipVersion encodes major and minor version digits`() {
        assertEquals(10U, ZipConstants.makeZipVersion(1, 0))
        assertEquals(20U, ZipConstants.makeZipVersion(2, 0))
        assertEquals(45U, ZipConstants.makeZipVersion(4, 5))
        assertEquals(63U, ZipConstants.makeZipVersion(6, 3))
    }

    @Test
    fun `makeZipVersion saturates to UShort max for oversized versions`() {
        assertEquals(UShort.MAX_VALUE, ZipConstants.makeZipVersion(7000, 0))
    }

    @Test
    fun `predefined version constants are derived via makeZipVersion`() {
        assertEquals(ZipConstants.makeZipVersion(6, 3), ZipConstants.LATEST_ZIP_VERSION)
        assertEquals(ZipConstants.makeZipVersion(1, 0), ZipConstants.STORED_ZIP_VERSION)
        assertEquals(ZipConstants.makeZipVersion(2, 0), ZipConstants.DEFLATE_ZIP_VERSION)
        assertEquals(ZipConstants.makeZipVersion(4, 5), ZipConstants.ZIP64_ZIP_VERSION)
    }
}
