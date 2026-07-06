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

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.IntVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorShape
import jdk.incubator.vector.VectorSpecies

internal class FastCRC32( // @formatter:off
    override val polynomial: UInt,
    override val initialValue: UInt
) : CRC32 { // @formatter:on
    companion object {
        private const val TABLE_SIZE: Int = 256
        private const val TABLE_SHIFT: Int = 8

        private val intSpecies: VectorSpecies<Int> = IntVector.SPECIES_PREFERRED
        private val laneSize: Int = intSpecies.length()
        private val byteSpecies: VectorSpecies<Byte> =
            VectorShape.forBitSize(laneSize * Byte.SIZE_BITS).withLanes(Byte::class.javaPrimitiveType)
        private val tableOffsets: IntArray = IntArray(laneSize) { index ->
            (laneSize - 1 - index) shl TABLE_SHIFT
        }
        private val vTableOffsets: IntVector = IntVector.fromArray(intSpecies, tableOffsets, 0)
        private val baseShiftOffsets: IntArray = IntArray(laneSize) { index ->
            when {
                index < Int.SIZE_BYTES -> index * Byte.SIZE_BITS
                else -> 0
            }
        }
        private val vBaseShiftOffsets: IntVector = IntVector.fromArray(intSpecies, baseShiftOffsets, 0)
        private val baseMask = intSpecies.indexInRange(0, Int.SIZE_BYTES)
    }

    private var value: Int = initialValue.toInt()
    private val tableIndices: IntArray = IntArray(laneSize)

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

    private val parallelTable: IntArray = IntArray(TABLE_SIZE * laneSize) { index ->
        val sliceIndex = index / TABLE_SIZE
        val tableIndex = index % TABLE_SIZE
        when (sliceIndex) {
            0 -> baseTable[tableIndex]
            else -> 0
        }
    }

    init {
        // Generate parallel tables for slicing
        for (sliceIndex in 1..<laneSize) {
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

    private fun roundSliceSimd(data: ByteArray, offset: Int) {
        // @formatter:off
        val vData = (ByteVector.fromArray(byteSpecies, data, offset)
            .convertShape(VectorOperators.B2I, intSpecies, 0) as IntVector)
            .and(0xFF)
        val vBase = IntVector.broadcast(intSpecies, value)
            .lanewise(VectorOperators.LSHR, vBaseShiftOffsets)
            .and(0xFF)
        val vTableIndices = vData.blend(vBase.lanewise(VectorOperators.XOR, vData), baseMask)
            .or(vTableOffsets)
        // @formatter:on
        vTableIndices.intoArray(tableIndices, 0)
        val vEntries = IntVector.fromArray(intSpecies, parallelTable, 0, tableIndices, 0)
        value = vEntries.reduceLanes(VectorOperators.XOR)
    }

    override fun round(data: ByteArray, offset: Int, size: Int) {
        if (data.isEmpty()) return
        // Compute round for bulk data using slicing
        var remaining = size
        var index = 0
        while (remaining > 0) when {
            // If we have enough bytes to fill at least one SIMD lane that's >= 256 bits..
            remaining >= laneSize -> {
                roundSliceSimd(data, offset + index)
                index += laneSize
                remaining -= laneSize
            }
            // Otherwise process a single byte
            else -> {
                round(data[offset + index++])
                remaining--
            }
        }
    }

    override fun finalize(): UInt = value.toUInt().inv()
}