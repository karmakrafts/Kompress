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

package dev.karmakrafts.kompress.deflate

import dev.karmakrafts.karbide.BitOrder
import dev.karmakrafts.karbide.BitSink
import dev.karmakrafts.karbide.bitSink
import dev.karmakrafts.karbide.writeBit
import dev.karmakrafts.karbide.writeBitsLsb
import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.compressingSource
import dev.karmakrafts.kompress.huffman.HuffmanTree
import dev.karmakrafts.kompress.lz77.LZ77
import dev.karmakrafts.kompress.lz77.Token
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource

/**
 * Streaming compression interface that supports deflate and deflate-raw compression.
 */
interface Deflater : Compressor {
    companion object {
        const val DEFAULT_LEVEL: Int = 6

        /**
         * Compresses the given data in one go using the given
         * compression level and buffer size.
         *
         * @param data The data to compress.
         * @param level The compression level between 0 and 9.
         * @param bufferSize The size of the intermediate buffer used during compression.
         * @return The compressed data.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the compressor encounters invalid data.
         */
        fun compress( // @formatter:off
            data: ByteArray,
            level: Int = DEFAULT_LEVEL,
            bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = Deflater(level).use { deflater -> // @formatter:on
            deflater.compressBulk(data, bufferSize)
        }
    }

    /**
     * The compression level of this deflater instance.
     * - 0 means no compression
     * - 1 is the fastest compression with the lowest ratio
     * - 9 is the slowest compression with the highest ratio
     *
     * **DO NOT change this during compression as it will corrupt your data!**
     */
    var level: Int
}

@Suppress("OVERRIDE_DEPRECATION")
internal class DeflaterImpl( // @formatter:off
    level: Int
) : Deflater { // @formatter:on
    private val lz77: LZ77 = LZ77(level)
    private var isClosed: Boolean = false

    override var level: Int = level
        set(value) {
            lz77.level = value
            field = value
        }


    override var input: ByteArray = ByteArray(0)
        private set
    override var inputOffset: Int = 0
        private set
    override var inputSize: Int = 0
        private set
    override var remaining: Int = 0
        private set

    override var bytesRead: Long = 0L
        private set
    override var bytesWritten: Long = 0L
        private set

    override val needsInput: Boolean get() = !finished && remaining == 0

    private var finishing: Boolean = false
    override var finished: Boolean = false
        private set

    private val buffer: Buffer = Buffer()
    private var bitSink: BitSink = buffer.bitSink(bitOrder = BitOrder.LSB_FIRST)
    private var wroteFinalBlock: Boolean = false

    private val literalFrequencies: IntArray = IntArray(DeflateConstants.LITERAL_ALPHABET_SIZE)
    private val distanceFrequencies: IntArray = IntArray(DeflateConstants.DISTANCE_ALPHABET_SIZE)
    private val tokenBuffer: ArrayList<Token> = ArrayList()

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        input = data
        inputOffset = offset
        inputSize = size
        remaining = size
    }

    /**
     * Computes the HCLEN value for the dynamic Huffman block header.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
     */
    private fun computeCodeLengthCodesCount(lengthTreeLengths: IntArray): Int {
        var codeLengthCodesCount = DeflateConstants.ALPHABET_SIZE
        while (codeLengthCodesCount > DeflateConstants.HCLEN_OFFSET && lengthTreeLengths[DeflateConstants.CODE_LENGTH_ORDER[codeLengthCodesCount - 1]] == 0) {
            codeLengthCodesCount--
        }
        return codeLengthCodesCount
    }

    /**
     * Encodes the dynamic Huffman block header (HLIT, HDIST, HCLEN).
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
     */
    private fun encodeDynamicHeader(literalCodesCount: Int, distanceCodesCount: Int, codeLengthCodesCount: Int) {
        bitSink.writeBitsLsb(DeflateConstants.HLIT_SIZE, (literalCodesCount - DeflateConstants.HLIT_OFFSET).toULong())
        bitSink.writeBitsLsb(
            DeflateConstants.HDIST_SIZE, (distanceCodesCount - DeflateConstants.HDIST_OFFSET).toULong()
        )
        bitSink.writeBitsLsb(
            DeflateConstants.HCLEN_SIZE, (codeLengthCodesCount - DeflateConstants.HCLEN_OFFSET).toULong()
        )
    }

    /**
     * Encodes the Huffman trees for the current block using dynamic Huffman coding.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
     */
    private fun encodeDynamicTrees(literalTree: HuffmanTree, distanceTree: HuffmanTree) {
        // Derive the raw code lengths
        val literalLengths = literalTree.codeLengths()
        val distanceLengths = distanceTree.codeLengths()
        val literalCodesCount = literalLengths.size
        val distanceCodesCount = distanceLengths.size

        // We need to count frequencies of symbols used to encode these lengths
        val lengthFrequencies = IntArray(DeflateConstants.CODE_LENGTH_ALPHABET_SIZE)

        // Use a temporary list to store symbols and extra bits
        val encodedSymbols = ArrayList<Long>() // symbol in low 32 bits, extra in high 32 bits

        fun collect(lengths: IntArray) {
            var index = 0
            while (index < lengths.size) {
                var count = 1
                val length = lengths[index]
                while (index + count < lengths.size && lengths[index + count] == length) {
                    count++
                }
                var remaining = count
                if (length == 0) {
                    while (remaining >= DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_MIN) {
                        val runLength = remaining.coerceAtMost(DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_MAX)
                        encodedSymbols += DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN.toLong() or ((runLength - DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_MIN).toLong() shl 32)
                        lengthFrequencies[DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN]++
                        remaining -= runLength
                    }
                    if (remaining >= DeflateConstants.SYM_REPEAT_ZERO_LENGTH_MIN) {
                        encodedSymbols += DeflateConstants.SYM_REPEAT_ZERO_LENGTH.toLong() or ((remaining - DeflateConstants.SYM_REPEAT_ZERO_LENGTH_MIN).toLong() shl 32)
                        lengthFrequencies[DeflateConstants.SYM_REPEAT_ZERO_LENGTH]++
                        remaining = 0
                    }
                    while (remaining > 0) {
                        encodedSymbols += 0L
                        lengthFrequencies[0]++
                        remaining--
                    }
                }
                else {
                    encodedSymbols += length.toLong()
                    lengthFrequencies[length]++
                    remaining--
                    while (remaining >= DeflateConstants.SYM_REPEAT_PREVIOUS_MIN) {
                        val runLength = remaining.coerceAtMost(DeflateConstants.SYM_REPEAT_PREVIOUS_MAX)
                        encodedSymbols += DeflateConstants.SYM_REPEAT_PREVIOUS.toLong() or ((runLength - DeflateConstants.SYM_REPEAT_PREVIOUS_MIN).toLong() shl 32)
                        lengthFrequencies[DeflateConstants.SYM_REPEAT_PREVIOUS]++
                        remaining -= runLength
                    }
                    while (remaining > 0) {
                        encodedSymbols += length.toLong()
                        lengthFrequencies[length]++
                        remaining--
                    }
                }
                index += count
            }
        }

        collect(literalLengths)
        collect(distanceLengths)

        // Derive length tree by frequencies
        val lengthTree = HuffmanTree.fromFrequencies(lengthFrequencies)
        val lengthTreeLengths = lengthTree.codeLengths()
        // Compute HCLEN value and write it out
        val codeLengthCodesCount = computeCodeLengthCodesCount(lengthTreeLengths)
        // Write the actual block header
        encodeDynamicHeader(literalCodesCount, distanceCodesCount, codeLengthCodesCount)
        // Write code-length tree
        for (index in 0..<codeLengthCodesCount) {
            val symbol = DeflateConstants.CODE_LENGTH_ORDER[index]
            bitSink.writeBitsLsb(DeflateConstants.CL_CODE_LENGTH_SIZE, lengthTreeLengths[symbol].toULong())
        }
        // Write literal and distance lengths and encode repeats
        for (packed in encodedSymbols) {
            val symbol = (packed and 0xFFFFFFFFL).toInt()
            val extraBits = (packed ushr 32).toInt()
            val code = lengthTree.encodingOf(symbol)
            bitSink.writeBits(code.length, code.bits.toULong())
            when (symbol) {
                DeflateConstants.SYM_REPEAT_PREVIOUS -> bitSink.writeBitsLsb(
                    DeflateConstants.SYM_REPEAT_PREVIOUS_SIZE, extraBits.toULong()
                )

                DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> bitSink.writeBitsLsb(
                    DeflateConstants.SYM_REPEAT_ZERO_LENGTH_SIZE, extraBits.toULong()
                )

                DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> bitSink.writeBitsLsb(
                    DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_SIZE, extraBits.toULong()
                )
            }
        }
    }

    /**
     * Encodes the extra bits for a length symbol.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.5.
     */
    private fun encodeLengthExtra(length: Int, symbol: Int) {
        val index = symbol - DeflateConstants.HLIT_OFFSET
        val baseLength = DeflateConstants.LENGTH_BASE[index]
        val extraBits = DeflateConstants.LENGTH_EXTRA_BITS[index]
        if (extraBits == 0) return
        val extraValue = length - baseLength
        bitSink.writeBitsLsb(extraBits, extraValue.toULong())
    }

    /**
     * Encodes the extra bits for a distance symbol.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.5.
     */
    private fun encodeDistanceExtra(distance: Int, symbol: Int) {
        val baseDistance = DeflateConstants.DIST_BASE[symbol]
        val extraBits = DeflateConstants.DIST_EXTRA_BITS[symbol]
        if (extraBits == 0) return
        val extraValue = distance - baseDistance
        bitSink.writeBitsLsb(extraBits, extraValue.toULong())
    }

    /**
     * Encodes a block of tokens using dynamic Huffman coding.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.3 and 3.2.7.
     */
    private fun encodeDynamicBlock(tokens: List<Token>) {
        bitSink.writeBit(if (finishing) 1U else 0U) // BFINAL
        bitSink.writeBitsLsb(DeflateConstants.BTYPE_SIZE, DeflateConstants.BTYPE_DYNAMIC) // BTYPE
        // Compute frequency of literals and matches
        literalFrequencies.fill(0)
        distanceFrequencies.fill(0)
        for (token in tokens) when (token) {
            is Token.Literal -> literalFrequencies[token.value.toInt() and 0xFF]++
            is Token.Match -> {
                val lengthSymbol = DeflateConstants.computeLengthSymbol(token.length)
                literalFrequencies[lengthSymbol]++
                val distanceSymbol = DeflateConstants.computeDistanceSymbol(token.distance)
                distanceFrequencies[distanceSymbol]++
            }
        }
        literalFrequencies[DeflateConstants.SYM_EOF]++ // EOF always occurs once
        // Construct huffman trees from frequencies and encode them
        val literalTree = HuffmanTree.fromFrequencies(literalFrequencies)
        val distanceTree = HuffmanTree.fromFrequencies(distanceFrequencies)
        encodeDynamicTrees(literalTree, distanceTree)
        // Encode the token stream
        for (token in tokens) when (token) {
            is Token.Literal -> {
                val code = literalTree.encodingOf(token.value.toInt() and 0xFF)
                bitSink.writeBits(code.length, code.bits.toULong())
            }

            is Token.Match -> {
                val (length, distance) = token

                val lengthSymbol = DeflateConstants.computeLengthSymbol(length)
                val lengthCode = literalTree.encodingOf(lengthSymbol)
                bitSink.writeBits(lengthCode.length, lengthCode.bits.toULong())
                encodeLengthExtra(length, lengthSymbol)

                val distanceSymbol = DeflateConstants.computeDistanceSymbol(distance)
                val distanceCode = distanceTree.encodingOf(distanceSymbol)
                bitSink.writeBits(distanceCode.length, distanceCode.bits.toULong())
                encodeDistanceExtra(distance, distanceSymbol)
            }
        }
        val eofCode = literalTree.encodingOf(DeflateConstants.SYM_EOF)
        bitSink.writeBits(eofCode.length, eofCode.bits.toULong())
    }

    override fun compress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        if (size <= 0 || finished) return 0
        // If any pending output exists, flush that out first
        var flushed = buffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
        if (flushed > 0) {
            bytesWritten += flushed
            return flushed
        }
        // If no more data is remaining, either flush or return early
        if (remaining == 0) {
            // If we are just finishing up, make sure to flush the last pending byte and return
            if (finishing && !finished) {
                if (!wroteFinalBlock) {
                    encodeDynamicBlock(emptyList())
                    wroteFinalBlock = true
                }
                bitSink.flush()
                flushed = buffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
                if (buffer.size == 0L) {
                    finished = true
                }
                bytesWritten += flushed
                return flushed
            }
            return 0
        }
        // Compress new data
        tokenBuffer.clear()
        lz77.encode(tokenBuffer, input, inputOffset, inputSize)
        bytesRead += inputSize
        if (finishing) {
            wroteFinalBlock = true
        }
        encodeDynamicBlock(tokenBuffer)
        remaining = 0
        // Flush out any pending data before returning
        flushed = buffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
        bytesWritten += flushed
        return flushed
    }

    override fun finish() {
        finishing = true
    }

    override fun reset() {
        finishing = false
        finished = false
        wroteFinalBlock = false
        setInput(ByteArray(0))
        bytesRead = 0L
        bytesWritten = 0L
        buffer.clear()
        bitSink.reset()
        literalFrequencies.fill(0)
        distanceFrequencies.fill(0)
    }

    override fun close() {
        if (isClosed) return
        bitSink.close()
        isClosed = true
    }
}

/**
 * Creates a new compressor using the specified compression level.
 * **Note that [Deflater] instances are NOT threadsafe!**
 *
 * @param level The compression level between 0 and 9.
 * @return A new [Deflater] instance with the given parameters.
 */
fun Deflater( // @formatter:off
    level: Int = Deflater.DEFAULT_LEVEL
): Deflater = DeflaterImpl(level) // @formatter:on

/**
 * Returns a [RawSource] that reads uncompressed bytes from this source and
 * emits their DEFLATE-compressed form.
 *
 * This is a streaming wrapper: bytes are compressed on the fly as you read
 * from the returned source. Close the returned source when finished to free
 * any underlying resources.
 *
 * @param level Compression level in range 0..9. See [Deflater.level].
 * @param bufferSize Size of the internal working buffers used during
 *  compression.
 * @return A [RawSource] that produces compressed data.
 */
fun RawSource.deflatingSource( // @formatter:off
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSource = compressingSource(Deflater(level), bufferSize) // @formatter:on

/**
 * Returns a [RawSink] that compresses written bytes using DEFLATE and
 * writes them to this sink.
 *
 * This is a streaming wrapper: bytes are compressed on the fly as you write
 * to the returned sink. Close the returned sink when finished to free
 * any underlying resources and ensure all data is flushed.
 *
 * @param level Compression level in range 0..9. See [Deflater.level].
 * @param bufferSize Size of the internal working buffers used during
 *  compression.
 * @return A [RawSink] that accepts uncompressed data and writes compressed data.
 */
fun RawSink.deflatingSink( // @formatter:off
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSink = compressingSink(Deflater(level), bufferSize) // @formatter:on