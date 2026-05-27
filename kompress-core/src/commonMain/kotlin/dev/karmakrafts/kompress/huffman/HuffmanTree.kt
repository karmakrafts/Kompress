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

/**
 * Implementation of a Huffman tree for Deflate compression and decompression.
 *
 * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.2.
 */
internal class HuffmanTree(
    lengths: IntArray = IntArray(0), offset: Int = 0, size: Int = lengths.size - offset
) {
    private val encodeTable: ArrayList<HuffmanCode> = ArrayList()
    private var maxBits: Int = 0
    private val decodeTable: IntArray

    init {
        repeat(size) {
            encodeTable.add(HuffmanCode())
        }
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
            encodeTable[symbol] = code
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
            class Node(val symbol: Int, val frequency: Int, val left: Node? = null, val right: Node? = null)

            val nodes = ArrayList<Node>()
            for (index in frequencies.indices) {
                if (frequencies[index] > 0) {
                    nodes += Node(index, frequencies[index])
                }
            }

            if (nodes.isEmpty()) {
                return HuffmanTree(IntArray(frequencies.size))
            }

            if (nodes.size == 1) {
                val lengths = IntArray(frequencies.size)
                lengths[nodes[0].symbol] = 1
                return HuffmanTree(lengths)
            }

            while (nodes.size > 1) {
                nodes.sortBy { node -> node.frequency }
                val left = nodes.removeAt(0)
                val right = nodes.removeAt(0)
                nodes += Node(-1, left.frequency + right.frequency, left, right)
            }

            val lengths = IntArray(frequencies.size)
            fun walk(node: Node, depth: Int) {
                if (node.symbol != -1) {
                    lengths[node.symbol] = depth
                    return
                }
                walk(node.left!!, depth + 1)
                walk(node.right!!, depth + 1)
            }
            walk(nodes[0], 0)

            return HuffmanTree(lengths)
        }
    }

    fun codeLengths(): IntArray = IntArray(encodeTable.size) { index ->
        encodeTable[index].length
    }

    fun encodingOf(symbol: Int): HuffmanCode =
        encodeTable.getOrNull(symbol) ?: throw NoSuchSymbolException("No symbol $symbol in huffman tree")

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

    fun decodeSymbol(source: BitSource): Int {
        if (maxBits == 0) throw NoSuchCodeException("No symbol in huffman tree")
        var bits = 0
        for (length in 1..maxBits) {
            bits = (bits shl 1) or source.readBit().toInt()
            val code = exactSymbolCode(bits, length)
            if (code != NO_SYMBOL) return unpackSymbol(code)
        }
        throw NoSuchCodeException("No symbol for code 0b${bits.toString(2)}")
    }
}