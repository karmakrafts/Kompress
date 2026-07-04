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

import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.lz77.LZ77
import kotlin.jvm.JvmInline

/**
 * Represents the Zlib `CMF` (compression method and flags) header byte.
 *
 * @property value The raw encoded CMF byte.
 */
@OptIn(InternalCompressionApi::class)
@JvmInline
value class ZlibCMF(val value: UByte) {
    private companion object {
        const val MIN_WINDOW_SIZE: Int = 1 shl 8
        const val MAX_WINDOW_SIZE: Int = 1 shl 15

        fun encodeWindowSize(windowSize: Int): UInt {
            require(windowSize in MIN_WINDOW_SIZE..MAX_WINDOW_SIZE) {
                "Window size must be in [$MIN_WINDOW_SIZE, $MAX_WINDOW_SIZE] bytes"
            }
            require(windowSize.countOneBits() == 1) {
                "Window size must be a power of two"
            }
            return (windowSize.countTrailingZeroBits() - 8).toUInt() and 0b1111U
        }
    }

    /**
     * Creates a CMF byte from a compression method and window size.
     *
     * @param compressionMethod The compression method to encode.
     * @param windowSize The LZ77 window size in bytes.
     */
    constructor(
        compressionMethod: ZlibCompressionMethod = ZlibCompressionMethod.DEFLATE,
        windowSize: Int = LZ77.DEFAULT_WINDOW_SIZE
    ) : this(
        (((encodeWindowSize(windowSize) shl 4) or (compressionMethod.encodedValue.toUInt() and 0b1111U))).toUByte()
    )

    /** The compression method encoded in this CMF byte. */
    inline val compressionMethod: ZlibCompressionMethod
        get() = ZlibCompressionMethod.byEncodedValue(value and 0b1111U)

    /** The encoded LZ77 window size in bytes. */
    inline val windowSize: Int
        get() = 1 shl ((((value.toUInt() shr 4) and 0b1111U).toInt()) + 8)
}