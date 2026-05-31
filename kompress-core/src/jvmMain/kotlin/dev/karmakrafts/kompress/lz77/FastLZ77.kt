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

import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies

internal class FastLZ77( // @formatter:off
    level: Int ,
    override val minMatch: Int,
    override val maxMatch: Int,
    override val windowSize: Int
) : LZ77 { // @formatter:on
    companion object {
        private val byteSpecies: VectorSpecies<Byte> = ByteVector.SPECIES_PREFERRED
        private val laneSize: Int = byteSpecies.length()
    }

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

    private fun matchLength( // @formatter:off
        data: ByteArray,
        candidate: Int,
        position: Int,
        limit: Int
    ): Int { // @formatter:on
        var length = 0
        while (length + laneSize <= limit) {
            val vCandidate = ByteVector.fromArray(byteSpecies, data, candidate + length)
            val vPosition = ByteVector.fromArray(byteSpecies, data, position + length)
            val vMismatch = vCandidate.compare(VectorOperators.NE, vPosition)
            if (vMismatch.anyTrue()) return length + vMismatch.firstTrue()
            length += laneSize
        }
        while (length < limit && data[candidate + length] == data[position + length]) {
            length++
        }
        return length
    }

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

                    val length = matchLength(
                        data, candidate + offset, pos + offset, minOf(maxMatch, size - pos)
                    )
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