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
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.5.
 *
 * @property encodedValue Encoded compression method identifier stored in ZIP headers.
 */
@ExperimentalCompressionApi
enum class ZipCompressionMethod(val encodedValue: UShort) {
    // @formatter:off
    /** No compression is applied to entry data. */
    NONE     (0x0000U),
    /** Entry data is compressed using DEFLATE. */
    DEFLATE  (0x0008U),
    /** Entry data is compressed using BZIP2. */
    BZIP2    (0x000CU),
    /** Entry data is compressed using LZMA. */
    LZMA     (0x000EU),
    /** Entry data is compressed using Zstandard. */
    ZSTD     (0x005DU),
    /** Entry data is compressed using XZ. */
    XZ       (0x005FU);
    // @formatter:on

    /**
     * Utilities for resolving encoded compression method identifiers.
     */
    companion object {
        /**
         * Resolves a compression method by its encoded ZIP header value.
         *
         * @param encodedValue Encoded compression method identifier.
         * @return Compression method matching [encodedValue].
         */
        fun byEncodedValue(encodedValue: UShort): ZipCompressionMethod = entries.first { method ->
            method.encodedValue == encodedValue
        }
    }
}