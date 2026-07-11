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

import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kplatform.OsFamily
import dev.karmakrafts.kplatform.Platform

/**
 * Operating-system identifiers used in GZip headers.
 *
 * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1,
 * end of page 7 to page 8.
 *
 * @param encodedValue The encoded OS value stored in the GZip header.
 */
enum class GZipOs(val encodedValue: UByte) {
    // @formatter:off
    /** FAT filesystem (MS-DOS, OS/2, NT/Win32). */
    FAT         (0x00U),
    /** Amiga. */
    AMIGA       (0x01U),
    /** OpenVMS. */
    VMS         (0x02U),
    /** Unix-like systems. */
    UNIX        (0x03U),
    /** VM/CMS. */
    VM_CMS      (0x04U),
    /** Atari TOS. */
    ATARI_TOS   (0x05U),
    /** HPFS filesystem. */
    HPFS        (0x06U),
    /** Macintosh. */
    MACINTOSH   (0x07U),
    /** Z-System. */
    Z_SYSTEM    (0x08U),
    /** CP/M. */
    CP_M        (0x09U),
    /** TOPS-20. */
    TOPS_20     (0x0AU),
    /** NTFS filesystem. */
    NTFS        (0x0BU),
    /** QDOS. */
    QDOS        (0x0CU),
    /** Acorn RISC OS. */
    RISCOS      (0x0DU),
    /** Unknown or unspecified operating system. */
    UNKNOWN     (0xFFU);
    // @formatter:on

    companion object {
        /**
         * Resolves a [GZipOs] by its encoded value.
         *
         * @param encodedValue The encoded OS value from a GZip header.
         * @return The matching [GZipOs].
         */
        fun byEncodedValue(encodedValue: UByte): GZipOs = entries.first { os -> os.encodedValue == encodedValue }

        /**
         * Guesses the current host operating-system marker for new entries.
         *
         * @return The best matching [GZipOs] for the current platform.
         */
        @OptIn(InternalCompressionApi::class)
        fun guessCurrent(): GZipOs {
            val family = Platform.os.family
            return when {
                family == OsFamily.WINDOWS -> NTFS
                family.isApple -> MACINTOSH
                family.isUnixoid -> UNIX
                else -> UNKNOWN
            }
        }
    }
}