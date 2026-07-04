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

package dev.karmakrafts.kompress.gzip

/**
 * Compression methods supported by GZip entries.
 *
 * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1,
 * start of page 6.
 *
 * @param encodedValue The encoded method value stored in the GZip header.
 */
enum class GZipCompressionMethod(val encodedValue: UByte) {
    // @formatter:off
    /** Deflate compression. */
    DEFLATE(0x08U);
    // @formatter:on

    companion object {
        /**
         * Resolves a [GZipCompressionMethod] by its encoded value.
         *
         * @param encodedValue The encoded method value from a GZip header.
         * @return The matching [GZipCompressionMethod].
         */
        fun byEncodedValue(encodedValue: UByte): GZipCompressionMethod = entries.first { method ->
            method.encodedValue == encodedValue
        }
    }
}