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
import dev.karmakrafts.karbide.peekBitsLsb
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
    /** Width of the decode table index, i.e. the length of the longest codeword. */
    var maxBits: Int = 0
        private set
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
        val decodeTableSize = if (maxBits == 0) 0 else 1 shl maxBits
        decodeTable = IntArray(decodeTableSize) { NO_SYMBOL }
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
            // DEFLATE sends codeword bits most significant first inside an LSB-first stream, so the
            // table is keyed by the reversed codeword. A decoder can then index it with the raw next
            // bits of the stream, with the bits following the codeword landing in the high index
            // bits, which every entry for this codeword covers.
            var index = reverseCode(canonical, bitLength)
            val stride = 1 shl bitLength
            while (index < decodeTableSize) {
                decodeTable[index] = packed
                index += stride
            }
        }
    }

    companion object {
        const val NO_SYMBOL: Int = -1

        private const val LENGTH_BITS: Int = 4
        private const val LENGTH_MASK: Int = (1 shl LENGTH_BITS) - 1

        /** The longest codeword RFC1951 3.2.7 permits. */
        const val MAX_CODE_LENGTH: Int = 15

        init {
            // The packed representation stores the length in LENGTH_BITS bits. Widening the RFC
            // limit without widening the packing would silently truncate lengths.
            check(MAX_CODE_LENGTH <= LENGTH_MASK) {
                "MAX_CODE_LENGTH $MAX_CODE_LENGTH does not fit in $LENGTH_BITS bits"
            }
        }

        fun packSymbol(symbol: Int, length: Int): Int = (symbol shl LENGTH_BITS) or length

        fun unpackSymbol(code: Int): Int = code ushr LENGTH_BITS

        fun unpackLength(code: Int): Int = code and LENGTH_MASK

        /** Reverses the low [length] bits of [value], mapping a canonical codeword to stream order. */
        private fun reverseCode(value: Int, length: Int): Int {
            var result = 0
            var remaining = value
            for (bit in 0 until length) {
                result = (result shl 1) or (remaining and 1)
                remaining = remaining shr 1
            }
            return result
        }

        /**
         * Redistributes [lengths] so that none exceeds [maxCodeLength].
         *
         * An unconstrained Huffman tree can be far deeper than DEFLATE permits: skewed frequencies
         * push the rarest symbols past 15 bits, which neither the code-length alphabet of RFC1951
         * 3.2.7 nor the packed table representation used here can express. Over-long codes are
         * clamped to the limit, which leaves the tree over-subscribed, and the surplus is worked off
         * by lengthening codes until the Kraft sum is back at exactly one. Lengths are then handed
         * back out in order of decreasing frequency, so the most common symbols keep the shortest
         * codes. The result is a valid, marginally sub-optimal code.
         */
        private fun limitCodeLengths(lengths: IntArray, frequencies: IntArray, maxCodeLength: Int) {
            var longest = 0
            for (length in lengths) {
                if (length > longest) longest = length
            }
            if (longest <= maxCodeLength) return

            // Kraft sums are tracked scaled by 2^maxCodeLength so they stay exact in integers.
            val scale = 1L shl maxCodeLength
            val counts = IntArray(maxCodeLength + 1)
            var kraft = 0L
            for (length in lengths) {
                if (length == 0) continue
                val clamped = if (length > maxCodeLength) maxCodeLength else length
                counts[clamped]++
                kraft += 1L shl (maxCodeLength - clamped)
            }

            // Clamping over-subscribes the tree, so lengthen the longest codes that still have room
            // until it fits. Each move frees exactly half of that code's share.
            while (kraft > scale) {
                var bits = maxCodeLength - 1
                while (bits > 0 && counts[bits] == 0) bits--
                // Only reachable if the alphabet cannot fit under maxCodeLength at all, which would
                // mean more than 2^maxCodeLength used symbols. Failing here beats handing back an
                // over-subscribed tree that corrupts the decode table later.
                check(bits > 0) { "Cannot fit alphabet into $maxCodeLength bit codes" }
                counts[bits]--
                counts[bits + 1]++
                kraft -= 1L shl (maxCodeLength - bits - 1)
            }
            // Lengthening can overshoot and leave capacity unused, which would make the code
            // incomplete. Give it back to the deepest codes, smallest step first.
            while (kraft < scale) {
                var bits = maxCodeLength
                while (bits > 1 && counts[bits] == 0) bits--
                if (bits <= 1 || kraft + (1L shl (maxCodeLength - bits)) > scale) break
                counts[bits]--
                counts[bits - 1]++
                kraft += 1L shl (maxCodeLength - bits)
            }

            val symbols = ArrayList<Int>()
            for (symbol in lengths.indices) {
                if (lengths[symbol] > 0) symbols.add(symbol)
            }
            symbols.sortWith(compareByDescending<Int> { frequencies[it] }.thenBy { it })
            var index = 0
            for (bits in 1..maxCodeLength) {
                var remaining = counts[bits]
                while (remaining > 0) {
                    lengths[symbols[index++]] = bits
                    remaining--
                }
            }
        }

        fun fromFrequencies(frequencies: IntArray, maxCodeLength: Int = MAX_CODE_LENGTH): HuffmanTree {
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
            limitCodeLengths(lengths, frequencies, maxCodeLength)

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
        // Reversed keying means the raw low bits address the entry directly.
        val code = decodeTable[bits]
        if (code != NO_SYMBOL && unpackLength(code) == length) return code
        return NO_SYMBOL
    }

    /**
     * Decodes the codeword sitting at the low end of [bits], which must hold at least [maxBits]
     * valid stream bits. Returns [NO_SYMBOL] only for a tree that has no codes at all.
     */
    fun symbolCodeAt(bits: Long): Int {
        val table = decodeTable
        if (table.isEmpty()) return NO_SYMBOL
        return table[bits.toInt() and (table.size - 1)]
    }

    /**
     * Decodes the codeword at the low end of [bits] when only [available] bits are known valid,
     * which is the case at the very end of a stream. Returns [NO_SYMBOL] if no codeword fits.
     */
    fun symbolCodeWithin(bits: Long, available: Int): Int {
        val limit = if (available < maxBits) available else maxBits
        for (length in 1..limit) {
            val code = decodeTable[bits.toInt() and ((1 shl length) - 1)]
            if (code != NO_SYMBOL && unpackLength(code) == length) return code
        }
        return NO_SYMBOL
    }

    fun peekSymbolCode(source: BitSource): Int {
        if (maxBits == 0) throw NoSuchCodeException("No symbol in huffman tree")
        if (source.requestBits(maxBits)) {
            val code = decodeTable[source.peekBitsLsb(maxBits).toInt()]
            if (code != NO_SYMBOL) return code
            throw NoSuchCodeException("No symbol in huffman tree")
        }
        for (length in 1..maxBits) {
            if (!source.requestBits(length)) return NO_SYMBOL
            val bits = source.peekBitsLsb(length).toInt()
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