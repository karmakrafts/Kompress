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
import kotlin.test.assertTrue

@OptIn(ExperimentalCompressionApi::class)
class ZipGPBFTest {
    @Test
    fun `GPBF constants use expected bit layout`() {
        assertEquals(0b00000000_00001000U.toUShort(), ZipGPBF.OMIT_CHECKSUM_AND_SIZES)
        assertEquals(0b00000000_00010000U.toUShort(), ZipGPBF.PATCHED_DATA)
        assertEquals(0b00000000_01000000U.toUShort(), ZipGPBF.STRONG_ENCRYPTION)
        assertEquals(0b00001000_00000000U.toUShort(), ZipGPBF.LANGUAGE_ENCODING)
        assertEquals(0b00100000_00000000U.toUShort(), ZipGPBF.MASKED_HEADER_VALUES)
    }

    @Test
    fun `Boolean constructor sets patched data flag`() {
        val gpbf = ZipGPBF(
            omitChecksumAndSizes = false,
            isPatchedData = true,
            hasStrongEncryption = false,
            languageEncoding = false,
            maskedHeaderValues = false
        )

        assertEquals(ZipGPBF.PATCHED_DATA, gpbf.value)
        assertFalse(gpbf.omitChecksumAndSizes)
        assertTrue(gpbf.isPatchedData)
        assertFalse(gpbf.hasStrongEncryption)
        assertFalse(gpbf.languageEncoding)
        assertFalse(gpbf.maskedHeaderValues)
    }

    @Test
    fun `Raw mask accessors expose all GPBF bits`() {
        val gpbf = ZipGPBF(ZipGPBF.STRONG_ENCRYPTION or ZipGPBF.LANGUAGE_ENCODING)

        assertFalse(gpbf.omitChecksumAndSizes)
        assertFalse(gpbf.isPatchedData)
        assertTrue(gpbf.hasStrongEncryption)
        assertTrue(gpbf.languageEncoding)
        assertFalse(gpbf.maskedHeaderValues)
    }
}
