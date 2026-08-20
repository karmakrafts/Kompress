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

import dev.karmakrafts.karbide.BitOrder
import dev.karmakrafts.karbide.bitSink
import dev.karmakrafts.karbide.bitSource
import dev.karmakrafts.kompress.deflate.DeflateConstants
import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.deflate.Inflater
import kotlinx.io.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the RFC1951 code length ceiling.
 *
 * An unconstrained Huffman tree grows one level deeper per Fibonacci step, so skewed frequencies
 * used to produce codes longer than the 15 bits RFC1951 3.2.7 allows, which corrupted the packed
 * table representation and, deep enough, overflowed the decode table index outright.
 */
class HuffmanTreeLengthLimitTest {
    private companion object {
        /**
         * The largest alphabet [fibonacciFrequencies] can describe.
         *
         * Frequencies grow as Fibonacci numbers and the tree builder sums them into a single [Int],
         * so the total has to stay below [Int.MAX_VALUE]. Fib(1..44) already sums to 1836311902,
         * and one step further overflows.
         */
        const val MAX_FIBONACCI_SYMBOLS: Int = 44

        /**
         * Frequencies that force the deepest possible tree: every extra symbol adds a level.
         *
         * [count] must not exceed [MAX_FIBONACCI_SYMBOLS], since silently wrapping into negative
         * frequencies would drop symbols and quietly test a much shallower alphabet instead.
         */
        fun fibonacciFrequencies(count: Int): IntArray {
            require(count in 2..MAX_FIBONACCI_SYMBOLS) { "Unsupported symbol count $count" }
            return IntArray(count).apply {
                this[0] = 1
                this[1] = 1
                for (index in 2 until count) this[index] = this[index - 1] + this[index - 2]
                for (frequency in this) check(frequency > 0) { "Frequency overflowed" }
            }
        }

        /** Bytes drawn from a Fibonacci weighted distribution, which deflates into a very deep tree. */
        fun skewedPayload(size: Int): ByteArray {
            val weights = fibonacciFrequencies(40)
            var total = 0
            for (weight in weights) total += weight
            val random = Random(0x51DE)
            return ByteArray(size) {
                var remaining = random.nextInt(total)
                var symbol = 0
                while (symbol < weights.size - 1 && remaining >= weights[symbol]) {
                    remaining -= weights[symbol]
                    symbol++
                }
                symbol.toByte()
            }
        }

        /**
         * Asserts the lengths describe a usable prefix code: within [maxCodeLength], and with a
         * Kraft sum of exactly one so the canonical assignment neither overruns nor leaves holes.
         */
        fun assertValidPrefixCode(lengths: IntArray, maxCodeLength: Int) {
            var kraftNumerator = 0L
            val scale = 1L shl maxCodeLength
            for (length in lengths) {
                if (length == 0) continue
                assertTrue(length <= maxCodeLength, "code length $length exceeds $maxCodeLength")
                kraftNumerator += scale shr length
            }
            assertEquals(scale, kraftNumerator, "code is not a complete prefix code")
        }
    }

    @Test
    fun `deeply skewed frequencies stay within the code length limit`() {
        for (count in 2..MAX_FIBONACCI_SYMBOLS) {
            val frequencies = fibonacciFrequencies(count)
            val lengths = HuffmanTree.fromFrequencies(frequencies).codeLengths()
            assertValidPrefixCode(lengths, HuffmanTree.MAX_CODE_LENGTH)
        }
    }

    @Test
    fun `code length alphabet trees stay within the three bit ceiling`() {
        val frequencies = fibonacciFrequencies(DeflateConstants.CODE_LENGTH_ALPHABET_SIZE)
        val lengths = HuffmanTree
            .fromFrequencies(frequencies, DeflateConstants.MAX_CL_CODE_LENGTH)
            .codeLengths()
        assertValidPrefixCode(lengths, DeflateConstants.MAX_CL_CODE_LENGTH)
    }

    @Test
    fun `limiting keeps the shortest codes on the most frequent symbols`() {
        val frequencies = fibonacciFrequencies(40)
        val lengths = HuffmanTree.fromFrequencies(frequencies).codeLengths()
        // Symbol 39 is by far the most frequent, symbol 0 the rarest.
        assertTrue(lengths[39] <= lengths[0], "frequent symbol got a longer code than a rare one")
        assertValidPrefixCode(lengths, HuffmanTree.MAX_CODE_LENGTH)
    }

    @Test
    fun `unskewed frequencies are left untouched`() {
        val frequencies = IntArray(16) { 1 }
        val lengths = HuffmanTree.fromFrequencies(frequencies).codeLengths()
        // A balanced alphabet of 16 needs exactly four bits per symbol and no repair at all.
        assertContentEquals(IntArray(16) { 4 }, lengths)
    }

    @Test
    fun `symbols round trip through a limited tree`() {
        val frequencies = fibonacciFrequencies(40)
        val tree = HuffmanTree.fromFrequencies(frequencies)
        val buffer = Buffer()
        buffer.bitSink(bitOrder = BitOrder.LSB_FIRST).use { sink ->
            for (symbol in frequencies.indices) tree.encodeSymbol(sink, symbol)
        }
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            for (symbol in frequencies.indices) assertEquals(symbol, tree.decodeSymbol(source))
        }
    }

    @Test
    fun `heavily skewed data survives a deflate inflate round trip`() {
        // Regression test for the reported symptom: a single block over enough skewed data used to
        // need codes longer than 15 bits, producing a stream that could not be read back at all.
        val data = skewedPayload(256 * 1024)
        val compressed = Deflater.compress(data)
        assertContentEquals(data, Inflater.decompress(compressed))
    }
}
