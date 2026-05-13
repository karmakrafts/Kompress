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

object GZipConstants {
    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * start of page 6.
     */
    const val MAGIC: UShort = 0x1F8BU

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * end of page 7.
     */
    const val XFL_MIN_COMPRESSION: UByte = 0x04U
    const val XFL_MAX_COMPRESSION: UByte = 0x02U

    const val NO_COMPRESSION: Int = 0
    const val MIN_COMPRESSION: Int = 1
    const val MAX_COMPRESSION: Int = 9
}