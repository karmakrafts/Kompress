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

import kotlin.jvm.JvmInline

@JvmInline
value class ZlibFlags(val fields: UByte) {
    constructor( // @formatter:off
        level: ZlibCompressionLevel = ZlibCompressionLevel.DEFAULT,
        hasDictionary: Boolean = false
    ) : this( // @formatter:on
        ((level.encodedValue.toUInt() shl 6) or (if (hasDictionary) 0x20U else 0U)).toUByte()
    )

    inline val level: ZlibCompressionLevel
        get() = ZlibCompressionLevel.byEncodedValue(((fields.toUInt() shr 6) and 0b11U).toUByte())

    inline val hasDictionary: Boolean
        get() = (fields.toUInt() and 0x20U) != 0U

    fun withCheckBits(cmf: ZlibCMF): UByte {
        val flg = fields.toUInt() and 0xE0U
        val cmfBits = cmf.value.toUInt()
        val fcheck = (31U - (((cmfBits shl 8) or flg) % 31U)) % 31U
        return (flg or fcheck).toUByte()
    }
}