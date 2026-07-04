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
 * Constants used by the GZip format implementation.
 */
object GZipConstants {
    /** Size of the fixed GZip header preamble in bytes. */
    const val HEADER_PREAMBLE_SIZE: Int = 10

    /** Size of the GZip trailer in bytes. */
    const val TRAILER_SIZE: Int = 8

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * Start of page 6.
     */
    const val MAGIC: UShort = 0x1F8BU

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * End of page 7.
     */
    const val XFL_MIN_COMPRESSION: UByte = 0x04U

    /** XFL value used when the compressor is configured for maximum compression. */
    const val XFL_MAX_COMPRESSION: UByte = 0x02U

    /** XFL value used when no specific compression hint is provided. */
    const val XFL_NONE: UByte = 0x00U

    /** Minimum supported compression level for GZip deflate. */
    const val MIN_COMPRESSION: Int = 1

    /** Maximum supported compression level for GZip deflate. */
    const val MAX_COMPRESSION: Int = 9
}