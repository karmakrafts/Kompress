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
import kotlinx.io.Source

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.5.2.
 *
 * @property encodedValue Encoded extra field header identifier.
 */
@ExperimentalCompressionApi
sealed class ZipExtraFieldEntryHeaderId(val encodedValue: UShort, private val parser: (Source, UShort) -> ZipExtraFieldEntryData) {
    constructor(encodedValue: UShort, parser: (Source) -> ZipExtraFieldEntryData) : this(encodedValue, { source, _ -> parser(source) })

    /**
     * Parses this extra field payload from [source].
     *
     * @param source Source positioned at the start of the extra field payload.
     * @param size   The size of the extra field's content in bytes.
     * @return Parsed extra field payload data.
     */
    fun parse(source: Source, size: UShort): ZipExtraFieldEntryData = parser(source, size)

    /** ZIP64 extended information field. */
    @ExperimentalCompressionApi
    object Zip64ExtendedInformation : ZipExtraFieldEntryHeaderId(0x00001U, ZipExtraFieldEntryData.Zip64::decode)

    @ExperimentalCompressionApi
    data class Other(val value: UShort) : ZipExtraFieldEntryHeaderId(value, ZipExtraFieldEntryData.Other::decode)

    companion object {
        val entries: Array<ZipExtraFieldEntryHeaderId> by lazy { arrayOf(Zip64ExtendedInformation) }

        /**
         * Resolves an extra field header identifier by its encoded value.
         *
         * @param encodedValue Encoded extra field header identifier.
         * @return Header identifier matching [encodedValue].
         */
        fun byEncodedValue(encodedValue: UShort): ZipExtraFieldEntryHeaderId =
            entries.firstOrNull { it.encodedValue == encodedValue } ?: Other(encodedValue)
    }
}
