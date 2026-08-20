/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kompress.huffman

import dev.karmakrafts.kompress.deflate.DeflateConstants
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
        /** Frequencies that force the deepest possible tree: one extra level per symbol. */
        fun fibonacciFrequencies(count: Int): IntArray = IntArray(count).apply {
            this[0] = 1
            if (count > 1) this[1] = 1
            for (index in 2 until count) this[index] = this[index - 1] + this[index - 2]
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
        for (count in 2..64) {
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
    fun `symbols encode and decode through a limited tree`() {
        val frequencies = fibonacciFrequencies(40)
        val tree = HuffmanTree.fromFrequencies(frequencies)
        for (symbol in frequencies.indices) {
            val code = tree.encodingOf(symbol)
            assertTrue(code.length in 1..HuffmanTree.MAX_CODE_LENGTH, "bad code length for $symbol")
        }
    }
}
