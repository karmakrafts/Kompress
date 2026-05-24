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

interface CRC32 {
    companion object {
        const val DEFAULT_INITIAL_VALUE: UInt = 0xFFFFFFFFU
        const val DEFAULT_POLYNOMIAL: UInt = 0xEDB88320U
    }

    val polynomial: UInt
    val initialValue: UInt

    fun reset()
    fun round(data: ByteArray)
    fun round(byte: Byte)
    fun finalize(): UInt
}

fun CRC32.once(data: ByteArray): UInt {
    round(data)
    return finalize()
}

fun CRC32.round(source: Source, size: Long) {
    var index = 0L
    while (!source.exhausted() && index < size) {
        round(source.readByte())
        index++
    }
}

fun CRC32.once(source: Source, size: Long): UInt {
    var index = 0L
    while (!source.exhausted() && index < size) {
        round(source.readByte())
        index++
    }
    return finalize()
}

private class CRC32Impl( // @formatter:off
    override val polynomial: UInt,
    override val initialValue: UInt
) : CRC32 { // @formatter:on
    private var value: UInt = initialValue

    private val table: UIntArray = UIntArray(256) { index ->
        var value = index.toUInt()
        repeat(8) {
            value = when {
                value and 0x1U != 0x0U -> (value shr 1) xor polynomial
                else -> value shr 1
            }
        }
        value
    }

    override fun reset() {
        value = initialValue
    }

    override fun round(byte: Byte) {
        val tableIndex = (value xor (byte.toUInt() and 0xFFU)) and 0xFFU
        value = (value shr 8) xor table[tableIndex.toInt()]
    }

    override fun round(data: ByteArray) {
        if (data.isEmpty()) return
        for (index in data.indices) {
            val tableIndex = (value xor (data[index].toUInt() and 0xFFU)) and 0xFFU
            value = (value shr 8) xor table[tableIndex.toInt()]
        }
    }

    override fun finalize(): UInt = value.inv()
}

fun CRC32( // @formatter:off
    polynomial: UInt = CRC32.DEFAULT_POLYNOMIAL,
    initialValue: UInt = CRC32.DEFAULT_INITIAL_VALUE
): CRC32 = CRC32Impl(polynomial, initialValue) // @formatter:on