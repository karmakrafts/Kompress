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

/**
 * Interface for calculating CRC32 checksums.
 */
interface CRC32 {
    companion object {
        /**
         * The default initial value for CRC32 calculation.
         */
        const val DEFAULT_INITIAL_VALUE: UInt = 0xFFFFFFFFU

        /**
         * The default polynomial used for CRC32 calculation (IEEE 802.3).
         */
        const val DEFAULT_POLYNOMIAL: UInt = 0xEDB88320U
    }

    /**
     * The polynomial used for the CRC32 calculation.
     */
    val polynomial: UInt

    /**
     * The initial value used for the CRC32 calculation.
     */
    val initialValue: UInt

    /**
     * Resets the CRC32 value to the initial value.
     */
    fun reset()

    /**
     * Updates the CRC32 value with the given [data].
     *
     * @param data The data to update the CRC32 value with.
     */
    fun round(data: ByteArray)

    /**
     * Updates the CRC32 value with the given [byte].
     *
     * @param byte The byte to update the CRC32 value with.
     */
    fun round(byte: Byte)

    /**
     * Finalizes the CRC32 calculation and returns the checksum value.
     *
     * @return The finalized CRC32 checksum.
     */
    fun finalize(): UInt
}

/**
 * Calculates the CRC32 checksum for the given [data] in one go.
 *
 * @param data The data to calculate the CRC32 checksum for.
 * @return The calculated CRC32 checksum.
 */
fun CRC32.once(data: ByteArray): UInt {
    round(data)
    return finalize()
}

/**
 * Updates the CRC32 value with [size] bytes from the given [source].
 *
 * @param source The source to read the data from.
 * @param size The number of bytes to read from the source.
 */
fun CRC32.round(source: Source, size: Long) {
    var index = 0L
    while (!source.exhausted() && index < size) {
        round(source.readByte())
        index++
    }
}

/**
 * Calculates the CRC32 checksum for [size] bytes from the given [source] in one go.
 *
 * @param source The source to read the data from.
 * @param size The number of bytes to read from the source.
 * @return The calculated CRC32 checksum.
 */
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

/**
 * Creates a new [CRC32] instance with the given [polynomial] and [initialValue].
 *
 * @param polynomial The polynomial to use for the CRC32 calculation.
 * @param initialValue The initial value to use for the CRC32 calculation.
 * @return A new [CRC32] instance.
 */
fun CRC32( // @formatter:off
    polynomial: UInt = CRC32.DEFAULT_POLYNOMIAL,
    initialValue: UInt = CRC32.DEFAULT_INITIAL_VALUE
): CRC32 = CRC32Impl(polynomial, initialValue) // @formatter:on