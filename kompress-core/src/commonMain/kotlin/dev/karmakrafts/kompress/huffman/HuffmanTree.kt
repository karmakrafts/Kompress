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
internal class HuffmanTree(lengths: IntArray = IntArray(0)) {
    // TODO: optimize this by using lookup by code prefix length -> benchmark!
    private val decodeTable: HashMap<HuffmanCode, Int> = HashMap()
    private val encodeTable: ArrayList<HuffmanCode> = ArrayList()
    private var maxBits: Int = lengths.maxOrNull() ?: 0

    init {
        repeat(lengths.size) {
            encodeTable.add(HuffmanCode())
        }
        // Determine how many symbols use each bit length.
        // See RFC1951 3.2.2, step 1.
        val bitLengthCounts = IntArray(maxBits + 1)
        for (length in lengths) {
            if (length == 0) continue
            bitLengthCounts[length]++
        }
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
        for (symbol in lengths.indices) {
            val bitLength = lengths[symbol]
            if (bitLength == 0) continue // Skip any 0-length symbols
            val canonical = nextCode[bitLength]++
            val code = HuffmanCode(canonical, bitLength)
            encodeTable[symbol] = code
            decodeTable[code] = symbol
        }
    }

    companion object {
        fun fromFrequencies(frequencies: IntArray): HuffmanTree {
            class Node(val symbol: Int, val frequency: Int, val left: Node? = null, val right: Node? = null)

            val nodes = ArrayList<Node>()
            for (index in frequencies.indices) {
                if (frequencies[index] > 0) {
                    nodes.add(Node(index, frequencies[index]))
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
                nodes.sortBy { it.frequency }
                val left = nodes.removeAt(0)
                val right = nodes.removeAt(0)
                nodes.add(Node(-1, left.frequency + right.frequency, left, right))
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

    fun decodeSymbol(source: BitSource): Int {
        var bits = 0
        for (length in 1..maxBits) {
            bits = (bits shl 1) or source.readBit().toInt()
            val code = HuffmanCode(bits, length) // Attempt to construct a huffman code and check if it exists
            return decodeTable[code] ?: continue
        }
        throw NoSuchCodeException("No symbol for code 0b${bits.toString(2)}")
    }
}