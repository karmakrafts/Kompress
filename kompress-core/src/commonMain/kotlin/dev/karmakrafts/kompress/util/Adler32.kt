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

package dev.karmakrafts.kompress.util

import dev.karmakrafts.kompress.InternalCompressionApi

@InternalCompressionApi
class Adler32(private val mod: Int = DEFAULT_MOD) {
    companion object {
        private const val DEFAULT_MOD: Int = 0xFFF1
    }

    var checksum: UInt = 0U
        private set

    fun round(bytes: ByteArray, offset: Int = 0, size: Int = bytes.size) {
        for (index in offset..<(offset + size)) round(bytes[index])
    }

    fun round(byte: Byte) {
        val a = ((checksum.toInt() and 0xFFFF) + byte.toInt()) % mod
        val b = (((checksum.toInt() shr 16) and 0xFFFF) + a) % mod
        checksum = (b.toUInt() shl 16) or a.toUInt()
    }

    fun reset() {
        checksum = 0U
    }
}