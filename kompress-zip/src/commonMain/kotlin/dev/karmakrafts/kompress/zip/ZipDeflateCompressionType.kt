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

@ExperimentalCompressionApi
enum class ZipDeflateCompressionType(val encodedValue: UShort) {
    // @formatter:off
    NORMAL    (0b00U),
    MAXIMUM   (0b01U),
    FAST      (0b10U),
    SUPER_FAST(0b11U);
    // @formatter:on

    companion object {
        fun byEncodedValue(encodedValue: UShort): ZipDeflateCompressionType = entries.first { type ->
            type.encodedValue == encodedValue
        }
    }
}