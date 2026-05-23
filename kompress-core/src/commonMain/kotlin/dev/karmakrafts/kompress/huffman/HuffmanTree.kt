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
import dev.karmakrafts.karbide.BitSource
import dev.karmakrafts.karbide.readBit
import dev.karmakrafts.kompress.exception.NoSuchCodeException
import dev.karmakrafts.kompress.exception.NoSuchSymbolException

internal class HuffmanTree(lengths: IntArray) {
    private val decodeTable: HashMap<HuffmanCode, Int> = HashMap()
    private val encodeTable: Array<HuffmanCode> = Array(lengths.size) { HuffmanCode() }
    private val maxBits: Int = lengths.maxOrNull() ?: 0

    init {
        // Determine how many symbols use each bit length
        val bitLengthCounts = IntArray(maxBits + 1)
        for (length in lengths) {
            if (length == 0) continue
            bitLengthCounts[length]++
        }
        // Compute canonical next code table
        val nextCode = IntArray(maxBits + 1)
        var code = 0
        for (bit in 1..maxBits) {
            code = (code + bitLengthCounts[bit - 1]) shl 1
            nextCode[bit] = code
        }
        // Build decode- and encode-tables
        for (symbol in lengths.indices) {
            val bitLength = lengths[symbol]
            if (bitLength == 0) continue // Skip any 0-length symbols
            val canonical = nextCode[bitLength]++
            val code = HuffmanCode(canonical, bitLength)
            encodeTable[symbol] = code
            decodeTable[code] = symbol
        }
    }

    fun encode(sink: BitSink, symbol: Int) {
        val code = encodeTable.getOrNull(symbol) ?: throw NoSuchSymbolException("No symbol $symbol in huffman tree")
        sink.writeBits(code.length, code.bits.toULong())
    }

    fun decode(source: BitSource): Int {
        var bits = 0
        for (length in 1..maxBits) {
            bits = (bits shl 1) or source.readBit().toInt()
            val code = HuffmanCode(bits, length) // Attempt to construct a huffman code and check if it exists
            return decodeTable[code] ?: continue
        }
        throw NoSuchCodeException("No symbol for code 0b${bits.toString(2)}")
    }
}