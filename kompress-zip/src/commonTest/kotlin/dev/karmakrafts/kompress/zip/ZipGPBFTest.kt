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
    fun `Boolean constructor combines multiple true flags`() {
        // Regression test: chaining `if(a) A else 0 or if(b) B else 0 or ...` without parentheses
        // let each `else` branch swallow the rest of the `or` chain — so whenever the *first*
        // condition was true, every subsequent flag was silently dropped instead of combined.
        val gpbf = ZipGPBF(omitChecksumAndSizes = true, languageEncoding = true)

        assertEquals(ZipGPBF.OMIT_CHECKSUM_AND_SIZES or ZipGPBF.LANGUAGE_ENCODING, gpbf.value)
        assertTrue(gpbf.omitChecksumAndSizes)
        assertTrue(gpbf.languageEncoding)
    }

    @Test
    fun `Boolean constructor defaults combine omitChecksumAndSizes and languageEncoding`() {
        // Both boolean parameter defaults are `true` — this is what a bare `ZipGPBF()` produces,
        // and exactly the case the bug above silently broke (dropped LANGUAGE_ENCODING).
        val gpbf = ZipGPBF()

        assertEquals(ZipGPBF.OMIT_CHECKSUM_AND_SIZES or ZipGPBF.LANGUAGE_ENCODING, gpbf.value)
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
