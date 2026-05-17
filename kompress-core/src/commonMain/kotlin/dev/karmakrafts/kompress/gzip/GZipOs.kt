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

import dev.karmakrafts.kompress.util.Platform
import dev.karmakrafts.kompress.util.currentPlatform

/**
 * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
 * end of page 7 to page 8.
 */
enum class GZipOs(val encodedValue: UByte) {
    // @formatter:off
    FAT         (0x00U),
    AMIGA       (0x01U),
    VMS         (0x02U),
    UNIX        (0x03U),
    VM_CMS      (0x04U),
    ATARI_TOS   (0x05U),
    HPFS        (0x06U),
    MACINTOSH   (0x07U),
    Z_SYSTEM    (0x08U),
    CP_M        (0x09U),
    TOPS_20     (0x0AU),
    NTFS        (0x0BU),
    QDOS        (0x0CU),
    RISCOS      (0x0DU),
    UNKNOWN     (0xFFU);
    // @formatter:on

    companion object {
        fun byEncodedValue(encodedValue: UByte): GZipOs = entries.first { os -> os.encodedValue == encodedValue }

        fun guessCurrent(): GZipOs = when (currentPlatform) {
            Platform.WINDOWS -> NTFS
            Platform.MACOS -> MACINTOSH
            else -> UNIX
        }
    }
}