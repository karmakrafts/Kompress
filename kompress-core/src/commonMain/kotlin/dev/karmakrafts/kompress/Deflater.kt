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

package dev.karmakrafts.kompress

import dev.karmakrafts.karbide.BitOrder
import dev.karmakrafts.karbide.BitSink
import dev.karmakrafts.karbide.bitSink
import dev.karmakrafts.karbide.writeBit
import dev.karmakrafts.kompress.Deflater.Companion.compress
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
         * @param raw If true, the ZLIB header and checksum fields will not be used
         *  in order to support the compression format used in both GZIP and PKZIP.
         * @param level The compression level between 0 and 9.
         * @param bufferSize The size of the intermediate buffer used during compression.
         * @return The compressed data.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the compressor encounters invalid data.
         */
        fun compress( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            level: Int = DEFAULT_LEVEL,
            bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = Deflater(raw, level).use { deflater -> // @formatter:on
            deflater.compressBulk(data, bufferSize)
        }

        /**
         * @see compress
         */
        @Deprecated(
            message = "This API will be removed in 2.0",
            replaceWith = ReplaceWith("compress(data, raw, level, bufferSize)")
        )
        fun deflate( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            level: Int = DEFAULT_LEVEL,
            bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = compress(data, raw, level, bufferSize) // @formatter:on
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

    /**
     * @see compress
     */
    @Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("compress(output)"))
    fun deflate(output: ByteArray): Int = compress(output)
}

@Suppress("OVERRIDE_DEPRECATION")
private class NewDeflaterImpl( // @formatter:off
    level: Int
) : Deflater { // @formatter:on
    private val lz77: LZ77 = LZ77(level)
    private var isClosed: Boolean = false

    override var level: Int = level
        set(value) {
            lz77.level = value
            field = value
        }

    private var _input: ByteArray = ByteArray(0)
    override var input: ByteArray
        get() = _input
        set(value) {
            setInput(value)
        }

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
    private val bitSink: BitSink = buffer.bitSink(bitOrder = BitOrder.LSB_FIRST)
    private val literalTree: HuffmanTree = HuffmanTree()
    private val distanceTree: HuffmanTree = HuffmanTree()

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        _input = data
        inputOffset = offset
        inputSize = size
        remaining = size
    }

    // TODO: could probably cache this and reuse it -> pooling?
    private fun buildCodeLengthAlphabet(hlit: Int, hdist: Int): IntArray {
        val combinedLengths = IntArray(hlit + hdist)
        var index = 0
        for (literalLength in literalTree.lengths) combinedLengths[index++] = literalLength
        for (distanceLength in distanceTree.lengths) combinedLengths[index++] = distanceLength
        return combinedLengths
    }

    private fun computeHclen(lengthTreeLengths: IntArray): Int {
        var hclen = DeflateConstants.ALPHABET_SIZE
        while (hclen > DeflateConstants.HCLEN_OFFSET && lengthTreeLengths[DeflateConstants.CODE_LENGTH_ORDER[hclen - 1]] == 0) {
            hclen--
        }
        return hclen
    }

    private fun encodeLengths(codeLengths: IntArray, tree: HuffmanTree) {
        var previous = -1
        for (length in codeLengths) {
            when (length) { // @formatter:off
                0 if previous == 0 -> {
                    tree.encodeSymbol(bitSink, DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN)
                    bitSink.writeBits(DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_SIZE, 0UL)
                }
                0 -> {
                    tree.encodeSymbol(bitSink, DeflateConstants.SYM_REPEAT_ZERO_LENGTH)
                    bitSink.writeBits(DeflateConstants.SYM_REPEAT_ZERO_LENGTH_SIZE, 0UL)
                }
                previous -> {
                    tree.encodeSymbol(bitSink, DeflateConstants.SYM_REPEAT_PREVIOUS)
                    bitSink.writeBits(DeflateConstants.SYM_REPEAT_PREVIOUS_SIZE, 0UL)
                }
                else -> tree.encodeSymbol(bitSink, length)
            } // @formatter:on
            previous = length
        }
    }

    private fun encodeDynamicHeader(hlit: Int, hdist: Int, hclen: Int) {
        bitSink.writeBits(DeflateConstants.HLIT_SIZE, (hlit - DeflateConstants.HLIT_OFFSET).toULong())
        bitSink.writeBits(DeflateConstants.HDIST_SIZE, (hdist - DeflateConstants.HDIST_OFFSET).toULong())
        bitSink.writeBits(DeflateConstants.HCLEN_SIZE, (hclen - DeflateConstants.HCLEN_OFFSET).toULong())
    }

    private fun encodeDynamicTrees() {
        // Derive the raw code lengths
        val literalLengths = literalTree.codeLengths()
        val distanceLengths = distanceTree.codeLengths()
        val hlit = literalLengths.size
        val hdist = distanceLengths.size
        // Build the code length alphabet
        val combinedLengths = buildCodeLengthAlphabet(hlit, hdist)
        // Derive length tree by frequencies
        val lengthFrequencies = IntArray(DeflateConstants.ALPHABET_SIZE)
        for (length in combinedLengths) lengthFrequencies[length]++
        val lengthTree = HuffmanTree(lengthFrequencies)
        val lengthTreeLengths = lengthTree.codeLengths()
        // Compute HCLEN value and write it out
        val hclen = computeHclen(lengthTreeLengths)
        // Write the actual block header
        encodeDynamicHeader(hlit, hdist, hclen)
        // Write code-length tree
        for (index in 0..<hclen) {
            val symbol = DeflateConstants.CODE_LENGTH_ORDER[index]
            bitSink.writeBits(3, lengthTreeLengths[symbol].toULong())
        }
        // Write literal and distance lengths and encode repeats
        encodeLengths(literalLengths, lengthTree)
        encodeLengths(distanceLengths, lengthTree)
    }

    private fun encodeLengthExtra(length: Int, symbol: Int) {
        val index = symbol - DeflateConstants.HLIT_OFFSET
        val base = DeflateConstants.LENGTH_BASE[index]
        val extraBits = DeflateConstants.LENGTH_EXTRA_BITS[index]
        if (extraBits == 0) return
        val value = length - base
        bitSink.writeBits(extraBits, value.toULong())
    }

    private fun encodeDistanceExtra(distance: Int, symbol: Int) {
        val base = DeflateConstants.DIST_BASE[symbol]
        val extraBits = DeflateConstants.DIST_EXTRA_BITS[symbol]
        if (extraBits == 0) return
        val value = distance - base
        bitSink.writeBits(extraBits, value.toULong())
    }

    private fun encodeDynamicBlock(tokens: List<Token>) {
        bitSink.writeBit(if (finishing) 1U else 0U) // BFINAL
        bitSink.writeBits(DeflateConstants.BTYPE_SIZE, DeflateConstants.BTYPE_DYNAMIC) // BTYPE
        // Compute frequency of literals and matches
        val literalFrequencies = IntArray(286)
        val distanceFrequencies = IntArray(30)
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
        literalTree.lengths = literalFrequencies
        distanceTree.lengths = distanceFrequencies
        encodeDynamicTrees()
        // Encode the token stream
        for (token in tokens) when (token) {
            is Token.Literal -> literalTree.encodeSymbol(bitSink, token.value.toInt() and 0xFF)
            is Token.Match -> {
                val (length, distance) = token

                val lengthSymbol = DeflateConstants.computeLengthSymbol(length)
                literalTree.encodeSymbol(bitSink, lengthSymbol)
                encodeLengthExtra(length, lengthSymbol)

                val distanceSymbol = DeflateConstants.computeDistanceSymbol(distance)
                distanceTree.encodeSymbol(bitSink, distanceSymbol)
                encodeDistanceExtra(distance, distanceSymbol)
            }
        }
    }

    override fun compress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        if (size <= 0) return 0
        // If any pending output exists, flush that out first
        var flushed = buffer.readAtMostTo(output, offset, offset + size)
        if (flushed > 0) {
            bytesWritten += flushed
            return flushed
        }
        // If no more data is remaining, either flush or return early
        if (remaining == 0) {
            // If we are just finishing up, make sure to flush the last pending byte and return
            if (finishing && !finished) {
                bitSink.flush()
                finished = true
                flushed = buffer.readAtMostTo(output, offset, offset + size)
                bytesWritten += flushed
                return flushed
            }
            return 0
        }
        // Compress new data
        // TODO: re-use an ArrayList as a buffer
        val tokens = lz77.encode(_input, inputOffset, inputSize)
        bytesRead += inputSize
        encodeDynamicBlock(tokens)
        remaining = 0
        // Flush out any pending data before returning
        flushed = buffer.readAtMostTo(output, offset, offset + size)
        bytesWritten += flushed
        return flushed
    }

    override fun finish() {
        finishing = true
    }

    override fun reset() {
        finishing = false
        finished = false
        setInput(ByteArray(0))
        buffer.clear()
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
 * @param raw If true, the ZLIB header and checksum fields will not be used
 *  in order to support the compression format used in both GZIP and PKZIP.
 * @param level The compression level between 0 and 9.
 * @return A new [Deflater] instance with the given parameters.
 */
expect fun Deflater( // @formatter:off
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL
): Deflater // @formatter:on

/**
 * Returns a [RawSource] that reads uncompressed bytes from this source and
 * emits their DEFLATE-compressed form.
 *
 * This is a streaming wrapper: bytes are compressed on the fly as you read
 * from the returned source. Close the returned source when finished to free
 * any underlying resources.
 *
 * @param raw If true (default), the compressed stream is in "deflate-raw"
 *  format without ZLIB header/footer. Set to false to include ZLIB wrapper
 *  fields, which some consumers may require.
 * @param level Compression level in range 0..9. See [Deflater.level].
 * @param bufferSize Size of the internal working buffers used during
 *  compression.
 * @return A [RawSource] that produces compressed data.
 */
fun RawSource.deflatingSource( // @formatter:off
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSource = compressingSource(Deflater(raw, level), bufferSize) // @formatter:on

/**
 * @see deflatingSource
 */
@Deprecated( // @formatter:off
    message = "This API will be removed in 2.0",
    replaceWith = ReplaceWith("deflatingSource(raw, level, bufferSize)")
) // @formatter:on
fun RawSource.deflating( // @formatter:off
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSource = deflatingSource(raw, level, bufferSize) // @formatter:on

/**
 * Returns a [RawSink] that compresses written bytes using DEFLATE and
 * writes them to this sink.
 *
 * This is a streaming wrapper: bytes are compressed on the fly as you write
 * to the returned sink. Close the returned sink when finished to free
 * any underlying resources and ensure all data is flushed.
 *
 * @param raw If true (default), the compressed stream is in "deflate-raw"
 *  format without ZLIB header/footer. Set to false to include ZLIB wrapper
 *  fields, which some consumers may require.
 * @param level Compression level in range 0..9. See [Deflater.level].
 * @param bufferSize Size of the internal working buffers used during
 *  compression.
 * @return A [RawSink] that accepts uncompressed data and writes compressed data.
 */
fun RawSink.deflatingSink( // @formatter:off
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSink = compressingSink(Deflater(raw, level), bufferSize) // @formatter:on