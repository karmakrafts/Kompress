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
 * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
 * start of page 6.
 */
enum class GZipCompressionMethod(val encodedValue: UByte) {
    // @formatter:off
    RESERVED_0(0x00U),
    RESERVED_1(0x01U),
    RESERVED_2(0x02U),
    RESERVED_3(0x03U),
    RESERVED_4(0x04U),
    RESERVED_5(0x05U),
    RESERVED_6(0x06U),
    RESERVED_7(0x07U),
    DEFLATE   (0x08U)
    // @formatter:on
}