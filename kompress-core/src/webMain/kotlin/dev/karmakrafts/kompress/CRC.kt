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

@file:OptIn(ExperimentalUnsignedTypes::class)

package dev.karmakrafts.kompress

private const val CRC32_POLYNOMIAL: UInt = 0xEDB88320U

private val crc32Table: UIntArray = UIntArray(256) { index ->
    var value = index.toUInt()
    for (i in 0..<8) {
        value = when {
            value and 0x1U != 0x0U -> (value shr 1) xor CRC32_POLYNOMIAL
            else -> value shr 1
        }
    }
    value
}

actual fun crc32(data: ByteArray): UInt {
    var crc = 0xFFFFFFFFU
    for (index in 0..<data.size) {
        val tableIndex = (crc xor data[index].toUByte().toUInt()) and 0xFFU
        crc = (crc shr 8) xor crc32Table[tableIndex.toInt()]
    }
    return crc
}