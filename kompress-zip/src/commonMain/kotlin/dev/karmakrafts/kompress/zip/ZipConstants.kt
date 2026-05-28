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
import kotlin.math.min

@ExperimentalCompressionApi
object ZipConstants {
    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.7.
     */
    const val LOCAL_FILE_HEADER_MAGIC: UInt = 0x04034B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.12.
     */
    const val CENTRAL_FILE_HEADER_MAGIC: UInt = 0x02014B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.13.
     */
    const val DIGITAL_SIGNATURE_MAGIC: UInt = 0x05054B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.9.
     */
    const val DATA_DESCRIPTOR_MAGIC: UInt = 0x08074B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.14.
     */
    const val END_OF_CENTRAL_DIRECTORY_MAGIC: UInt = 0x06054B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.14.
     */
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_MAGIC: UInt = 0x06064B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.15.
     */
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_MAGIC: UInt = 0x07064B50U

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.3.2.
     */
    val LATEST_ZIP_VERSION: UShort = makeZipVersion(6, 3)

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.3.2.
     */
    val STORED_ZIP_VERSION: UShort = makeZipVersion(1, 0)

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.3.2.
     */
    val DEFLATE_ZIP_VERSION: UShort = makeZipVersion(2, 0)

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.3.2.
     */
    val ZIP64_ZIP_VERSION: UShort = makeZipVersion(4, 5)

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.14.
     */
    const val ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIZE: ULong = 44UL

    /**
     * This ZIP implementation writes single-disk archives only.
     */
    const val FIRST_DISK_NUMBER: UShort = 0U

    /**
     * This ZIP implementation writes single-disk ZIP64 archives only.
     */
    const val ZIP64_FIRST_DISK_NUMBER: UInt = 0U

    /**
     * This ZIP implementation writes single-disk ZIP64 archives only.
     */
    const val ZIP64_DISK_COUNT: UInt = 1U

    /**
     * This ZIP implementation does not write internal file attributes.
     */
    const val NO_INTERNAL_FILE_ATTRIBUTES: UShort = 0U

    /**
     * This ZIP implementation does not write external file attributes.
     */
    const val NO_EXTERNAL_FILE_ATTRIBUTES: UInt = 0U

    /**
     * This ZIP implementation does not write an archive comment.
     */
    const val NO_ARCHIVE_COMMENT_LENGTH: UShort = 0U

    /**
     * Local headers use zeroed checksum fields when the actual value is deferred to a data descriptor.
     */
    const val DEFERRED_CHECKSUM: UInt = 0U

    /**
     * Local headers use zeroed size fields when the actual values are deferred to a data descriptor.
     */
    const val DEFERRED_SIZE: Long = 0L

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.8, 4.4.9 and 4.4.16.
     */
    val STANDARD_FIELD_MAX_VALUE: Long = UInt.MAX_VALUE.toLong()

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.10.
     */
    val STANDARD_ENTRY_COUNT_MAX_VALUE: Long = UShort.MAX_VALUE.toLong()

    /**
     * Marks 32-bit fields whose actual value is stored in a ZIP64 extra field or record.
     */
    val ZIP64_EXTENDED_FIELD_MARKER: UInt = UInt.MAX_VALUE

    /**
     * Marks 16-bit entry count fields whose actual value is stored in a ZIP64 record.
     */
    val ZIP64_ENTRY_COUNT_MARKER: UShort = UShort.MAX_VALUE

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.2.3.
     */
    fun makeZipVersion(major: Int, minor: Int): UShort = min(UShort.MAX_VALUE.toInt(), major * 10 + minor).toUShort()
}