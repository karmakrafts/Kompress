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

package dev.karmakrafts.kompress.crc

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
    companion object {
        private const val TABLE_SIZE: Int = 256
        private const val TABLE_SHIFT: Int = 8
        private const val SLICING_FACTOR: Int = 8
    }

    private var value: Int = initialValue.toInt()

    private val baseTable: IntArray = IntArray(TABLE_SIZE) { index ->
        var value = index
        repeat(Byte.SIZE_BITS) {
            value = when {
                value and 0x1 != 0x0 -> (value ushr 1) xor polynomial.toInt()
                else -> value ushr 1
            }
        }
        value
    }

    private val parallelTable: IntArray = IntArray(TABLE_SIZE * SLICING_FACTOR) { index ->
        val sliceIndex = index / TABLE_SIZE
        val tableIndex = index % TABLE_SIZE
        when (sliceIndex) {
            0 -> baseTable[tableIndex]
            else -> 0
        }
    }

    init {
        // Generate parallel tables for slicing
        for (sliceIndex in 1..<SLICING_FACTOR) {
            for (tableIndex in 0..<TABLE_SIZE) {
                val index = tableIndex + sliceIndex * TABLE_SIZE
                val previousIndex = tableIndex + (sliceIndex - 1) * TABLE_SIZE
                val previous = parallelTable[previousIndex]
                parallelTable[index] = (previous ushr 8) xor baseTable[previous and 0xFF]
            }
        }
    }

    override fun reset() {
        value = initialValue.toInt()
    }

    override fun round(byte: Byte) {
        val tableIndex = (value xor (byte.toInt() and 0xFF)) and 0xFF
        value = (value ushr 8) xor baseTable[tableIndex]
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun roundSlice8(data: ByteArray, offset: Int) {
        val b0 = data[offset + 0].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF
        val b4 = data[offset + 4].toInt() and 0xFF
        val b5 = data[offset + 5].toInt() and 0xFF
        val b6 = data[offset + 6].toInt() and 0xFF
        val b7 = data[offset + 7].toInt() and 0xFF
        // XOR first 4 bytes into CRC
        val base = value xor b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        // Fold 8 bytes using the parallel table
        // @formatter:off
        value = parallelTable[(7 shl TABLE_SHIFT) or (base and 0xFF)] xor
            parallelTable[(6 shl TABLE_SHIFT) or ((base ushr 8) and 0xFF)] xor
            parallelTable[(5 shl TABLE_SHIFT) or ((base ushr 16) and 0xFF)] xor
            parallelTable[(4 shl TABLE_SHIFT) or ((base ushr 24) and 0xFF)] xor
            parallelTable[(3 shl TABLE_SHIFT) or b4] xor
            parallelTable[(2 shl TABLE_SHIFT) or b5] xor
            parallelTable[(1 shl TABLE_SHIFT) or b6] xor
            parallelTable[b7]
        // @formatter:on
    }

    override fun round(data: ByteArray) {
        if (data.isEmpty()) return
        // Compute round for bulk data using slicing
        var remaining = data.size
        var index = 0
        while (remaining > 0) when {
            remaining >= 8 -> {
                roundSlice8(data, index)
                index += 8
                remaining -= 8
            }

            else -> { // Single bytes
                round(data[index++])
                remaining--
            }
        }
    }

    override fun finalize(): UInt = value.toUInt().inv()
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