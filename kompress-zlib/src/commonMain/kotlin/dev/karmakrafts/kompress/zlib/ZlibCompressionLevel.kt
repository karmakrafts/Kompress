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

package dev.karmakrafts.kompress.zlib

enum class ZlibCompressionLevel(
    val encodedValue: UByte, val deflateLevel: Int
) {
    // @formatter:off
    FASTEST(0x00U, 1),
    FAST   (0x01U, 3),
    DEFAULT(0x02U, 6),
    MAXIMUM(0x03U, 9);
    // @formatter:on

    companion object {
        fun byEncodedValue(encodedValue: UByte): ZlibCompressionLevel = entries.first { level ->
            level.encodedValue == encodedValue
        }

        fun fromDeflaterLevel(level: Int): ZlibCompressionLevel = when (level) {
            in 0..1 -> FASTEST
            in 2..3 -> FAST
            in 4..6 -> DEFAULT
            in 7..9 -> MAXIMUM
            else -> error("Unsupported deflater level $level")
        }
    }
}