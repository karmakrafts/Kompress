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

package dev.karmakrafts.kompress

import dev.karmakrafts.kompress.exception.NoSuchSymbolException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

internal object DeflateConstants {
    const val SYM_EOF: Int = 256
    const val SYM_REPEAT_PREVIOUS: Int = 16
    const val SYM_REPEAT_PREVIOUS_SIZE: Int = 2
    const val SYM_REPEAT_ZERO_LENGTH: Int = 17
    const val SYM_REPEAT_ZERO_LENGTH_SIZE: Int = 3
    const val SYM_LONG_ZERO_LENGTH_RUN: Int = 18
    const val SYM_LONG_ZERO_LENGTH_RUN_SIZE: Int = 7

    const val BTYPE_SIZE: Int = 2
    const val BTYPE_DYNAMIC: ULong = 0b10UL

    const val ALPHABET_SIZE: Int = 19
    const val HLIT_OFFSET: Int = 257
    const val HDIST_OFFSET: Int = 1
    const val HCLEN_OFFSET: Int = 4

    /**
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
     */
    @JvmStatic
    @JvmField
    val CODE_LENGTH_ORDER: IntArray = intArrayOf( // @formatter:off
        16, 17, 18, 0, 8,  7,
        9,  6,  10, 5, 11, 4,
        12, 3,  13, 2, 14, 1,
        15
    ) // @formatter:on

    @JvmStatic
    @JvmField
    val LENGTH_BASE: IntArray = intArrayOf( // @formatter:off
        3, 4, 5, 6, 7, 8, 9, 10,
        11, 13, 15, 17,
        19, 23, 27, 31,
        35, 43, 51, 59,
        67, 83, 99, 115,
        131, 163, 195, 227,
        258
    ) // @formatter:on

    @JvmStatic
    @JvmField
    val LENGTH_EXTRA_BITS: IntArray = intArrayOf( // @formatter:off
        0, 0, 0, 0, 0, 0, 0, 0,
        1, 1, 1, 1,
        2, 2, 2, 2,
        3, 3, 3, 3,
        4, 4, 4, 4,
        5, 5, 5, 5,
        0
    ) // @formatter:on

    @JvmStatic
    @JvmField
    val DIST_BASE: IntArray = intArrayOf( // @formatter:off
        1, 2, 3, 4,
        5, 7, 9, 13,
        17, 25, 33, 49,
        65, 97, 129, 193,
        257, 385, 513, 769,
        1025, 1537, 2049, 3073,
        4097, 6145, 8193, 12289,
        16385, 24577
    ) // @formatter:on

    @JvmStatic
    @JvmField
    val DIST_EXTRA_BITS: IntArray = intArrayOf( // @formatter:off
        0, 0, 0, 0,
        1, 1, 2, 2,
        3, 3, 4, 4,
        5, 5, 6, 6,
        7, 7, 8, 8,
        9, 9, 10, 10,
        11, 11, 12, 12,
        13, 13
    ) // @formatter:on

    /**
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.5.
     */
    @JvmStatic
    fun computeSymbol(length: Int): Int = when (length) {
        in 3..10 -> 257 + (length - 3)
        in 11..18 -> 265 + ((length - 11) / 2)
        in 19..34 -> 269 + ((length - 19) / 4)
        in 35..66 -> 273 + ((length - 35) / 8)
        in 67..130 -> 277 + ((length - 67) / 16)
        in 131..257 -> 281 + ((length - 131) / 32)
        258 -> 285
        else -> throw NoSuchSymbolException("Cannot compute symbol for length $length")
    }
}