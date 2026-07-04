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

import dev.karmakrafts.kompress.InternalCompressionApi

@InternalCompressionApi
interface LZ77 {
    companion object {
        const val DEFAULT_LEVEL: Int = 6
        const val DEFAULT_MIN_MATCH: Int = 3
        const val DEFAULT_MAX_MATCH: Int = 258
        const val DEFAULT_WINDOW_SIZE: Int = 32 * 1024

        const val HASH_SIZE: Int = 1 shl 15
        const val HASH_MASK: Int = HASH_SIZE - 1
        const val DEFAULT_HEAD: Int = -1
        const val DEFAULT_NEXT: Int = -1

        fun getMaxChainDepth(level: Int): Int = when (level) {
            in 0..2 -> 4
            in 3..5 -> 16
            in 6..7 -> 64
            else -> 256
        }

        fun rollingHash(data: ByteArray, offset: Int): Int {
            require(data.size - offset >= 3) { "Rolling hash offset out of bounds" }
            return ( // @formatter:off
                ((data[offset].toInt() and 0xFF) shl 10) xor
                    ((data[offset + 1].toInt() and 0xFF) shl 5) xor
                    ((data[offset + 2].toInt() and 0xFF))
                ) and HASH_MASK // @formatter:on
        }
    }

    var level: Int
    val minMatch: Int
    val maxMatch: Int
    val windowSize: Int

    /**
     * Encodes the given data into an LZ77 token stream.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.5.
     *
     * @param data The raw bytes to encode.
     * @param offset The offset into the input array to start reading from.
     * @param size The size of the slice to read from the input array.
     * @return A list of LZ77 tokens corresponding to the original data.
     */
    fun encode( // @formatter:off
        tokens: MutableList<Token>,
        data: ByteArray,
        offset: Int = 0,
        size: Int = data.size - offset
    )

    fun reset()
}

/**
 * A simple LZ77 implementation which allows
 * overriding the level, match counts and window size.
 */
@InternalCompressionApi
internal class LZ77Impl( // @formatter:off
    level: Int = LZ77.DEFAULT_LEVEL,
    override val minMatch: Int = LZ77.DEFAULT_MIN_MATCH,
    override val maxMatch: Int = LZ77.DEFAULT_MAX_MATCH,
    override val windowSize: Int = LZ77.DEFAULT_WINDOW_SIZE
) : LZ77 { // @formatter:on
    private var maxChain: Int = LZ77.getMaxChainDepth(level)

    override var level: Int = level
        set(value) {
            maxChain = LZ77.getMaxChainDepth(value)
            field = value
        }

    private val head: IntArray = IntArray(LZ77.HASH_SIZE) { LZ77.DEFAULT_HEAD }
    private val window: ByteArray = ByteArray(windowSize)

    private var windowPosition: Int = 0
    private var windowFilled: Int = 0

    override fun encode( // @formatter:off
        tokens: MutableList<Token>,
        data: ByteArray,
        offset: Int,
        size: Int
    ) { // @formatter:on
        // Reset the hash table for each encoding run
        head.fill(LZ77.DEFAULT_HEAD)
        // 'next' stores the previous position in the hash chain for each input position
        val next = IntArray(size) { LZ77.DEFAULT_NEXT }
        var pos = 0
        while (pos < size) {
            var bestLength = 0
            var bestDistance = 0
            // Wait until we have enough bytes available to match against (minimum match length)
            if (pos + minMatch <= size) {
                val hash = LZ77.rollingHash(data, pos + offset)
                var candidate = head[hash] // Newest candidate position in hash chain
                var chain = 0
                // Search backwards through previous matches in the hash chain
                while (candidate >= 0 && chain++ < maxChain) {
                    val distance = pos - candidate // Distance backwards into sliding window
                    if (distance > windowSize) break // Stop searching if we reach window size limit

                    var length = 0
                    // Extend match while bytes remain equal and we haven't exceeded maxMatch
                    while( // @formatter:off
                        length < maxMatch &&
                        pos + length < size &&
                        data[candidate + offset + length] == data[pos + offset + length]
                    ) { // @formatter:on
                        length++
                    }
                    // Keep the best match found so far
                    if (length >= minMatch && length > bestLength) {
                        bestLength = length
                        bestDistance = distance
                        // Check if this is already a perfect (maximum length) match
                        if (length == maxMatch) break
                    }
                    // Follow the chain to the next (older) candidate
                    candidate = if (candidate < size) next[candidate] else LZ77.DEFAULT_NEXT
                }
                // Insert current position into the hash chain
                next[pos] = head[hash]
                head[hash] = pos
            }
            // Emit the according token
            tokens += if (bestLength >= minMatch) {
                // Update the hash chain for every extra byte we skip through the match
                // to ensure we can match against these positions later
                for (chainOffset in 1..<bestLength) {
                    val currentPosition = pos + chainOffset
                    if (currentPosition + minMatch <= size) {
                        val currentHash = LZ77.rollingHash(data, currentPosition + offset)
                        next[currentPosition] = head[currentHash]
                        head[currentHash] = currentPosition
                    }
                }
                pos += bestLength // Increment by total match length
                Token.Match(bestLength, bestDistance)
            }
            else {
                // No match found or match too short, emit a literal byte
                Token.Literal(data[offset + pos++].toUByte())
            }
        }
    }

    override fun reset() {
        window.fill(0)
        windowPosition = 0
        windowFilled = 0
    }
}

@InternalCompressionApi
expect fun LZ77(
    level: Int = LZ77.DEFAULT_LEVEL,
    minMatch: Int = LZ77.DEFAULT_MIN_MATCH,
    maxMatch: Int = LZ77.DEFAULT_MAX_MATCH,
    windowSize: Int = LZ77.DEFAULT_WINDOW_SIZE
): LZ77