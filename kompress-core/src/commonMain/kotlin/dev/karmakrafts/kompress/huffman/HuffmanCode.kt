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

package dev.karmakrafts.kompress.huffman

import dev.karmakrafts.karbide.BitSink
import kotlin.jvm.JvmInline

@JvmInline
internal value class HuffmanCode(val value: ULong) {
    inline val bits: Int
        get() = (value shr UInt.SIZE_BITS).toInt()

    inline val length: Int
        get() = value.toInt()

    constructor( // @formatter:off
        bits: Int = 0,
        length: Int = 0
    ) : this((bits.toULong() shl UInt.SIZE_BITS) or length.toULong()) // @formatter:on

    @Suppress("NOTHING_TO_INLINE")
    inline fun encode(sink: BitSink) = sink.writeBits(length, bits.toULong())

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun component1(): Int = bits

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun component2(): Int = length
}