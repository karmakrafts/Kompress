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

/**
 * DEFLATE compression level hints encoded in ZIP general purpose bit flags.
 *
 * @property encodedValue Encoded DEFLATE compression type value.
 */
@ExperimentalCompressionApi
enum class ZipDeflateCompressionType(val encodedValue: UShort) {
    // @formatter:off
    /** Normal DEFLATE compression. */
    NORMAL    (0b00U),
    /** Maximum DEFLATE compression. */
    MAXIMUM   (0b01U),
    /** Fast DEFLATE compression. */
    FAST      (0b10U),
    /** Super-fast DEFLATE compression. */
    SUPER_FAST(0b11U);
    // @formatter:on

    /**
     * Utilities for resolving encoded DEFLATE compression types.
     */
    companion object {
        /**
         * Resolves a DEFLATE compression type by its encoded GPBF value.
         *
         * @param encodedValue Encoded DEFLATE compression type.
         * @return Compression type matching [encodedValue].
         */
        fun byEncodedValue(encodedValue: UShort): ZipDeflateCompressionType = entries.first { type ->
            type.encodedValue == encodedValue
        }
    }
}