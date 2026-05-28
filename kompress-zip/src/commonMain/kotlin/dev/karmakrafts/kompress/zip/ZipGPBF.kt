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
import kotlin.jvm.JvmInline

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.4.
 */
@ExperimentalCompressionApi
@JvmInline
value class ZipGPBF(val value: UShort) {
    companion object {
        const val OMIT_CHECKSUM_AND_SIZES: UShort = 0b00000000_00001000U
        const val PATCHED_DATA: UShort = 0b00000000_00010000U
        const val STRONG_ENCRYPTION: UShort = 0b00000000_01000000U
        const val LANGUAGE_ENCODING: UShort = 0b00001000_00000000U
        const val MASKED_HEADER_VALUES: UShort = 0b00100000_00000000U
    }

    constructor(
        omitChecksumAndSizes: Boolean = true,
        isPatchedData: Boolean = false,
        hasStrongEncryption: Boolean = false,
        languageEncoding: Boolean = true,
        maskedHeaderValues: Boolean = false
    ) : this( // @formatter:off
        if(omitChecksumAndSizes) OMIT_CHECKSUM_AND_SIZES else 0U.toUShort()
            or if(isPatchedData) PATCHED_DATA else 0U.toUShort()
            or if(hasStrongEncryption) STRONG_ENCRYPTION else 0U.toUShort()
            or if(languageEncoding) LANGUAGE_ENCODING else 0U.toUShort()
            or if(maskedHeaderValues) MASKED_HEADER_VALUES else 0U.toUShort()
    ) // @formatter:on

    inline val omitChecksumAndSizes: Boolean
        get() = value and OMIT_CHECKSUM_AND_SIZES != 0U.toUShort()

    inline val isPatchedData: Boolean
        get() = value and PATCHED_DATA != 0U.toUShort()

    inline val hasStrongEncryption: Boolean
        get() = value and STRONG_ENCRYPTION != 0U.toUShort()

    inline val languageEncoding: Boolean
        get() = value and LANGUAGE_ENCODING != 0U.toUShort()

    inline val maskedHeaderValues: Boolean
        get() = value and MASKED_HEADER_VALUES != 0U.toUShort()
}