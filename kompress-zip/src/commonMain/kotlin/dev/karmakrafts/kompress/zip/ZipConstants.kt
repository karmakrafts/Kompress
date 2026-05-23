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
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.3.2.
     */
    val LATEST_ZIP_VERSION: UShort = makeZipVersion(6, 3)

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.2.3.
     */
    fun makeZipVersion(major: Int, minor: Int): UShort = min(UShort.MAX_VALUE.toInt(), major * 10 + minor).toUShort()
}