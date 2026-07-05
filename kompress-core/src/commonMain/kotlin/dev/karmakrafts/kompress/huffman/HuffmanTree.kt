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
import dev.karmakrafts.kompress.exception.NoSuchCodeException
import dev.karmakrafts.kompress.exception.NoSuchSymbolException

/**
 * Implementation of a Huffman tree for Deflate compression and decompression.
 *
 * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.2.
 */
internal class HuffmanTree(
    lengths: IntArray = IntArray(0),
    offset: Int = 0,
    size: Int = lengths.size - offset,
    buildEncodeTable: Boolean = true
) {
    private val encodeTable: LongArray? = if (buildEncodeTable) LongArray(size) else null
    private var maxBits: Int = 0
    private val decodeTable: IntArray

    init {
        for (index in 0 until size) {
            maxBits = maxBits.coerceAtLeast(lengths[offset + index])
        }
        // Determine how many symbols use each bit length.
        // See RFC1951 3.2.2, step 1.
        val bitLengthCounts = IntArray(maxBits + 1)
        for (index in 0 until size) {
            val length = lengths[offset + index]
            if (length == 0) continue
            bitLengthCounts[length]++
        }
        decodeTable = IntArray(if (maxBits == 0) 0 else 1 shl maxBits) { NO_SYMBOL }
        // Compute canonical next code table.
        // See RFC1951 3.2.2, step 2.
        val nextCode = IntArray(maxBits + 1)
        var code = 0
        for (bit in 1..maxBits) {
            code = (code + bitLengthCounts[bit - 1]) shl 1
            nextCode[bit] = code
        }
        // Build decode- and encode-tables by assigning numerical values to all codes.
        // See RFC1951 3.2.2, step 3.
        for (symbol in 0 until size) {
            val bitLength = lengths[offset + symbol]
            if (bitLength == 0) continue // Skip any 0-length symbols
            val canonical = nextCode[bitLength]++
            val code = HuffmanCode(canonical, bitLength)
            encodeTable?.set(symbol, code.value.toLong())
            val packed = packSymbol(symbol, bitLength)
            val suffixBits = maxBits - bitLength
            val endIndex = (canonical + 1) shl suffixBits
            var index = canonical shl suffixBits
            while (index < endIndex) {
                decodeTable[index] = packed
                index++
            }
        }
    }

    companion object {
        const val NO_SYMBOL: Int = -1

        private const val LENGTH_BITS: Int = 4
        private const val LENGTH_MASK: Int = (1 shl LENGTH_BITS) - 1

        fun packSymbol(symbol: Int, length: Int): Int = (symbol shl LENGTH_BITS) or length

        fun unpackSymbol(code: Int): Int = code ushr LENGTH_BITS

        fun unpackLength(code: Int): Int = code and LENGTH_MASK

        fun fromFrequencies(frequencies: IntArray): HuffmanTree {
            var symbols = 0
            var onlySymbol = -1
            for (index in frequencies.indices) {
                if (frequencies[index] > 0) {
                    symbols++
                    onlySymbol = index
                }
            }

            if (symbols == 0) {
                return HuffmanTree(IntArray(frequencies.size))
            }

            if (symbols == 1) {
                val lengths = IntArray(frequencies.size)
                lengths[onlySymbol] = 1
                return HuffmanTree(lengths)
            }

            val maxNodes = frequencies.size * 2
            val nodeFrequencies = IntArray(maxNodes)
            val depths = IntArray(maxNodes)
            val parents = IntArray(maxNodes) { -1 }
            val heap = IntArray(maxNodes + 1)
            var heapSize = 0

            for (index in frequencies.indices) {
                val frequency = frequencies[index]
                if (frequency == 0) continue
                nodeFrequencies[index] = frequency
                heap[++heapSize] = index
            }

            fun smaller(lhs: Int, rhs: Int): Boolean {
                return nodeFrequencies[lhs] < nodeFrequencies[rhs] || nodeFrequencies[lhs] == nodeFrequencies[rhs] && depths[lhs] <= depths[rhs]
            }

            fun restoreHeap(start: Int) {
                var parent = start
                val value = heap[parent]
                var child = parent shl 1
                while (child <= heapSize) {
                    if (child < heapSize && smaller(heap[child + 1], heap[child])) {
                        child++
                    }
                    if (smaller(value, heap[child])) break

                    heap[parent] = heap[child]
                    parent = child
                    child = parent shl 1
                }
                heap[parent] = value
            }

            for (index in heapSize / 2 downTo 1) {
                restoreHeap(index)
            }

            var nextNode = frequencies.size
            while (heapSize >= 2) {
                val left = heap[1]
                heap[1] = heap[heapSize--]
                restoreHeap(1)

                val right = heap[1]
                val parent = nextNode++
                nodeFrequencies[parent] = nodeFrequencies[left] + nodeFrequencies[right]
                depths[parent] = depths[left].coerceAtLeast(depths[right]) + 1
                parents[left] = parent
                parents[right] = parent
                heap[1] = parent
                restoreHeap(1)
            }

            val lengths = IntArray(frequencies.size)
            for (symbol in frequencies.indices) {
                if (frequencies[symbol] == 0) continue
                var node = symbol
                var length = 0
                while (parents[node] != -1) {
                    length++
                    node = parents[node]
                }
                lengths[symbol] = length
            }

            return HuffmanTree(lengths)
        }
    }

    fun codeLengths(): IntArray {
        val table = encodeTable ?: error("Encoding table was not built for this huffman tree")
        return IntArray(table.size) { index ->
            HuffmanCode(table[index].toULong()).length
        }
    }

    fun encodingOf(symbol: Int): HuffmanCode {
        val table = encodeTable ?: error("Encoding table was not built for this huffman tree")
        if (symbol !in table.indices) throw NoSuchSymbolException("No symbol $symbol in huffman tree")
        return HuffmanCode(table[symbol].toULong())
    }

    fun encodeSymbol(sink: BitSink, symbol: Int) {
        val code = encodingOf(symbol)
        code.encode(sink)
    }

    private fun exactSymbolCode(bits: Int, length: Int): Int {
        val code = decodeTable[bits shl (maxBits - length)]
        if (code != NO_SYMBOL && unpackLength(code) == length) return code
        return NO_SYMBOL
    }

    fun peekSymbolCode(source: BitSource): Int {
        if (maxBits == 0) throw NoSuchCodeException("No symbol in huffman tree")
        if (source.requestBits(maxBits)) {
            val code = decodeTable[source.peekBits(maxBits).toInt()]
            if (code != NO_SYMBOL) return code
            throw NoSuchCodeException("No symbol in huffman tree")
        }
        for (length in 1..maxBits) {
            if (!source.requestBits(length)) return NO_SYMBOL
            val bits = source.peekBits(length).toInt()
            val code = exactSymbolCode(bits, length)
            if (code != NO_SYMBOL) return code
        }
        throw NoSuchCodeException("No symbol in huffman tree")
    }

    private fun readSymbolCode(source: BitSource): Int {
        val code = peekSymbolCode(source)
        if (code == NO_SYMBOL) return NO_SYMBOL
        source.skipBits(unpackLength(code))
        return code
    }

    fun decodeSymbol(source: BitSource): Int {
        val code = readSymbolCode(source)
        if (code != NO_SYMBOL) return unpackSymbol(code)
        throw NoSuchCodeException("No symbol in huffman tree")
    }
}