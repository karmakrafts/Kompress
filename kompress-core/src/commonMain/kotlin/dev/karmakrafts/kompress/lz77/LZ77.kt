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

package dev.karmakrafts.kompress.lz77

/**
 * A simple LZ77 implementation which allows
 * overriding the level, match counts and window size.
 */
internal class LZ77( // @formatter:off
    level: Int = DEFAULT_LEVEL,
    val minMatch: Int = DEFAULT_MIN_MATCH,
    val maxMatch: Int = DEFAULT_MAX_MATCH,
    val windowSize: Int = DEFAULT_WINDOW_SIZE
) { // @formatter:on
    companion object {
        const val DEFAULT_LEVEL: Int = 6
        const val DEFAULT_MIN_MATCH: Int = 3
        const val DEFAULT_MAX_MATCH: Int = 258
        const val DEFAULT_WINDOW_SIZE: Int = 32 * 1024

        private const val HASH_SIZE: Int = 1 shl 15
        private const val HASH_MASK: Int = HASH_SIZE - 1
        private const val DEFAULT_HEAD: Int = -1
        private const val DEFAULT_NEXT: Int = -1

        /**
         * Simple rolling hash implementation according to https://en.wikipedia.org/wiki/Rolling_hash
         * for creating 3-byte hashes for indexing into a hash table.
         */
        private fun rollingHash(data: ByteArray, offset: Int): Int {
            require(data.size - offset >= 3) { "Rolling hash offset out of bounds" }
            return ( // @formatter:off
                ((data[offset].toInt() and 0xFF) shl 10) xor
                ((data[offset + 1].toInt() and 0xFF) shl 5) xor
                ((data[offset + 2].toInt() and 0xFF))
            ) and HASH_MASK // @formatter:on
        }

        private fun getMaxChainDepth(level: Int): Int = when (level) {
            in 0..2 -> 4
            in 3..5 -> 16
            in 6..7 -> 64
            else -> 256
        }
    }

    private var maxChain: Int = getMaxChainDepth(level)

    var level: Int = level
        set(value) {
            maxChain = getMaxChainDepth(value)
            field = value
        }

    private val head: IntArray = IntArray(HASH_SIZE) { DEFAULT_HEAD }
    private val decodeBuffer: ArrayList<UByte> = ArrayList()

    /**
     * Decodes the given LZ77 token stream into the raw data it represents.
     *
     * @param tokens The list of tokens to decode.
     * @return A new [ByteArray] containing the raw decompressed data
     *  represented by the given token stream.
     */
    fun decode(tokens: Iterable<Token>): ByteArray {
        decodeBuffer.clear() // Ensure the buffer is cleared before reconstructing data
        for (token in tokens) when (token) {
            is Token.Literal -> decodeBuffer += token.value
            is Token.Match -> {
                val start = decodeBuffer.size - token.distance
                for (offset in 0..<token.length) {
                    decodeBuffer += decodeBuffer[start + offset]
                }
            }
        }
        return decodeBuffer.toUByteArray().asByteArray()
    }

    /**
     * Encodes the given data into an LZ77 token stream.
     *
     * @param data The raw bytes to encode.
     * @param offset The offset into the input array to start reading from.
     * @param size The size of the slice to read from the input array.
     * @return A list of LZ77 tokens corresponding to the original data.
     */
    fun encode( // @formatter:off
        data: ByteArray,
        offset: Int = 0,
        size: Int = data.size - offset
    ): List<Token> { // @formatter:on
        head.fill(DEFAULT_HEAD)
        val tokens = ArrayList<Token>()
        val next = IntArray(size) { DEFAULT_NEXT }
        var pos = 0
        while (pos < size) {
            var bestLength = 0
            var bestDistance = 0
            // Wait until we have enough bytes available to match against
            if (pos + minMatch <= size) {
                val hash = rollingHash(data, pos + offset)
                var candidate = head[hash] // Newest candidate position
                var chain = 0
                // Search backwards through previous matches
                while (candidate >= 0 && chain++ < maxChain) {
                    val distance = pos - candidate // Distance backwards into sliding window
                    if (distance > windowSize) break // Stop searching if we reach window size limit
                    var length = 0
                    // Extend match while bytes remain equal
                    while( // @formatter:off
                        length < maxMatch &&
                        pos + length < size &&
                        distance !in minMatch..length &&
                        data[candidate + offset + length] == data[pos + offset + length]
                    ) { // @formatter:on
                        length++
                    }
                    // Keep the best match
                    if (length >= minMatch && length > bestLength) {
                        bestLength = length
                        bestDistance = distance
                        // Check if this is already a perfect match
                        if (length == maxMatch) break
                    }
                    candidate = next[candidate]
                }
                // Insert current position into the hash chain
                next[pos] = head[hash]
                head[hash] = pos
            }
            // Emit the according token
            tokens += if (bestLength >= minMatch) {
                // Update the hash chain for every extra byte we skip through the match
                for (chainOffset in 1 until bestLength) {
                    val currentPosition = pos + chainOffset
                    if (currentPosition + minMatch <= size) {
                        val currentHash = rollingHash(data, currentPosition + offset)
                        next[currentPosition] = head[currentHash]
                        head[currentHash] = currentPosition
                    }
                }
                pos += bestLength // Increment by total match length
                Token.Match(bestLength, bestDistance)
            }
            else Token.Literal(data[offset + pos++].toUByte())
        }
        return tokens
    }
}