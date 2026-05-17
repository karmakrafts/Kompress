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

import kotlinx.io.Source

private const val CRC32_POLYNOMIAL: UInt = 0xEDB88320U

/**
 * Initial value for CRC32 calculations.
 */
const val CRC32_INITIAL_VALUE: UInt = 0xFFFFFFFFU

private val crc32Table: UIntArray = UIntArray(256) { index ->
    var value = index.toUInt()
    repeat(8) {
        value = when {
            value and 0x1U != 0x0U -> (value shr 1) xor CRC32_POLYNOMIAL
            else -> value shr 1
        }
    }
    value
}

/**
 * Calculates the CRC32 checksum for the given [data].
 *
 * @param data the data to calculate the checksum for.
 * @param initialValue the initial value for the CRC32 calculation.
 * @return the calculated CRC32 checksum.
 */
fun crc32( // @formatter:off
    data: ByteArray,
    initialValue: UInt = CRC32_INITIAL_VALUE
): UInt { // @formatter:on
    if (data.isEmpty()) return 0U
    var crc = initialValue
    for (index in data.indices) {
        val tableIndex = (crc xor (data[index].toUInt() and 0xFFU)) and 0xFFU
        crc = (crc shr 8) xor crc32Table[tableIndex.toInt()]
    }
    return crc.inv()
}

/**
 * Calculates the CRC32 checksum for the given [size] of bytes from this [Source].
 *
 * Calling this function will consume the specified number of bytes from the source.
 *
 * @param size the number of bytes to read from the source.
 * @param initialValue the initial value for the CRC32 calculation.
 * @return the calculated CRC32 checksum.
 */
fun Source.crc32( // @formatter:off
    size: Long = Long.MAX_VALUE,
    initialValue: UInt = CRC32_INITIAL_VALUE
): UInt { // @formatter:on
    if (exhausted()) return 0U
    var crc = initialValue
    var index = 0
    while (!exhausted() && index < size) {
        val tableIndex = (crc xor (readByte().toUInt() and 0xFFU)) and 0xFFU
        crc = (crc shr 8) xor crc32Table[tableIndex.toInt()]
        index++
    }
    return crc.inv()
}