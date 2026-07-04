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

@OptIn(InternalCompressionApi::class)
@JvmInline
value class ZlibCMF(val value: UByte) {
    constructor(
        compressionMethod: ZlibCompressionMethod = ZlibCompressionMethod.DEFLATE,
        windowSize: Int = LZ77.DEFAULT_WINDOW_SIZE
    ) : this((((windowSize.toUInt() shl 4) and 0b1111U) or (compressionMethod.encodedValue.toUInt() and 0b1111U)).toUByte())

    inline val compressionMethod: ZlibCompressionMethod
        get() = ZlibCompressionMethod.byEncodedValue(value and 0b1111U)

    inline val windowSize: Int
        get() = ((value.toUInt() shr 4) and 0b1111U).toInt()
}