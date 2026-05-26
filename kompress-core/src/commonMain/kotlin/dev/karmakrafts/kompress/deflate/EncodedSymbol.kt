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

package dev.karmakrafts.kompress.deflate

import kotlin.jvm.JvmInline

@JvmInline
internal value class EncodedSymbol(val value: ULong) {
    inline val symbol: Int
        get() = (value and UInt.MAX_VALUE.toULong()).toInt()

    inline val extraBits: Int
        get() = (value shr UInt.SIZE_BITS).toInt()

    constructor(symbol: Int, extraBits: Int = 0) : this(
        (extraBits.toULong() shl UInt.SIZE_BITS) or symbol.toULong()
    )

    constructor() : this(0UL)

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun component1(): Int = symbol

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun component2(): Int = extraBits
}