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

import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.decompressingSink
import dev.karmakrafts.kompress.decompressingSource
import dev.karmakrafts.kompress.exception.DataFormatException
import dev.karmakrafts.kompress.huffman.HuffmanTree
import dev.karmakrafts.kompress.lz77.LZ77
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray

/**
 * Streaming decompression interface that supports inflate and inflate-raw decompression.
 */
interface Inflater : Decompressor {
    companion object {
        /**
         * Decompresses the given data in one go using the given
         * buffer size.
         *
         * @param data The data to compress.
         * @param bufferSize The size of the intermediate buffer used during compression.
         * @return The decompressed data.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun decompress( // @formatter:off
            data: ByteArray,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = Inflater().use { inflater -> // @formatter:on
            inflater.decompressBulk(data, bufferSize)
        }

        /**
         * Computes the compressed size of the given data by inflating it and
         * subtracting the remaining bytes from the original size.
         *
         * @param data The compressed data.
         * @param bufferSize The size of the intermediate buffer used during decompression.
         * @return The size of the compressed data that was actually consumed.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun computeCompressedSize( // @formatter:off
            data: ByteArray,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): Int = Inflater().use { inflater -> // @formatter:on
            inflater.setInput(data)
            inflater.finish()
            val outputBuffer = ByteArray(bufferSize)
            while (true) {
                if (inflater.decompress(outputBuffer) == 0) break // Reached EOF early
            }
            data.size - inflater.remaining
        }

        /**
         * Computes the compressed size of the data from the given source by inflating it and
         * subtracting the remaining bytes from the total bytes read.
         *
         * @param source The source to read compressed data from.
         * @param bufferSize The size of the intermediate buffer used during decompression.
         * @return The size of the compressed data that was actually consumed from the source.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun computeCompressedSize( // @formatter:off
            source: RawSource,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): Long = Inflater().use { inflater -> // @formatter:on
            val outputBuffer = ByteArray(bufferSize)
            val buffer = Buffer()
            var totalRead = 0L
            while (!inflater.finished) {
                if (inflater.needsInput) {
                    val read = source.readAtMostTo(buffer, bufferSize.toLong())
                    if (read == -1L) {
                        inflater.finish()
                    }
                    else {
                        totalRead += read
                        inflater.setInput(buffer.readByteArray())
                    }
                }
                if (inflater.decompress(outputBuffer) == 0 && !inflater.needsInput) break
            }
            totalRead - inflater.remaining
        }
    }
}

internal class InflaterImpl(
    windowSize: Int = LZ77.DEFAULT_WINDOW_SIZE
) : Inflater {
    private enum class State { // @formatter:off
        HEADER,
        STORED_HEADER,
        STORED,
        DYNAMIC_HEADER,
        COMPRESSED
    } // @formatter:on

    private enum class DynamicHeaderStage { // @formatter:off
        COUNTS,
        CODE_LENGTH_CODES,
        TREE_LENGTHS
    } // @formatter:on

    private companion object {
        val FIXED_LITERAL_TREE: HuffmanTree = HuffmanTree(
            DeflateConstants.FIXED_LIT_TREE_LENGTHS, buildEncodeTable = false
        )
        val FIXED_DISTANCE_TREE: HuffmanTree = HuffmanTree(
            DeflateConstants.FIXED_DIST_TREE_LENGTHS, buildEncodeTable = false
        )
    }

    override var input: ByteArray = ByteArray(0)
        private set
    override var inputOffset: Int = 0
        private set
    override var inputSize: Int = 0
        private set

    override val remaining: Int
        get() {
            val consumed = (bytesRead - inputStart).toInt() - if (ended) bitCount / Byte.SIZE_BITS else 0
            return (inputSize - consumed).coerceIn(0, inputSize)
        }

    override val bytesRead: Long get() = totalBytesRead
    override var bytesWritten: Long = 0L
        private set
    override val needsInput: Boolean get() = !finished && isInputNeeded
    override val finished: Boolean get() = ended

    private val window: ByteArray = ByteArray(windowSize)
    private val codeLengthLengths: IntArray = IntArray(DeflateConstants.CODE_LENGTH_ALPHABET_SIZE)

    private var isClosed: Boolean = false
    private var inputStart: Long = 0L
    private var inputPosition: Int = 0
    private var inputEnd: Int = 0
    private var totalBytesRead: Long = 0L
    private var bitBuffer: Int = 0
    private var bitCount: Int = 0
    private var windowPosition: Int = 0
    private var windowFilled: Int = 0
    private var finishing: Boolean = false
    private var ended: Boolean = false
    private var isInputNeeded: Boolean = true

    private var state: State = State.HEADER
    private var isFinalBlock: Boolean = false
    private var storedRemaining: Int = 0
    private var literalTree: HuffmanTree = FIXED_LITERAL_TREE
    private var distanceTree: HuffmanTree = FIXED_DISTANCE_TREE

    private var dynamicHeaderStage: DynamicHeaderStage = DynamicHeaderStage.COUNTS
    private var dynamicLiteralCodesCount: Int = 0
    private var dynamicDistanceCodesCount: Int = 0
    private var dynamicCodeLengthCodesCount: Int = 0
    private var dynamicCodeLengthIndex: Int = 0
    private var dynamicLengthIndex: Int = 0
    private var dynamicLengthsCount: Int = 0
    private var dynamicPreviousLength: Int = 0
    private val dynamicLengths: IntArray = IntArray(
        DeflateConstants.FIXED_LIT_TREE_LENGTHS.size + DeflateConstants.FIXED_DIST_TREE_LENGTHS.size
    )
    private var dynamicLengthTree: HuffmanTree = HuffmanTree(buildEncodeTable = false)

    private var pendingLength: Int = 0
    private var pendingDistance: Int = 0

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        input = data
        inputOffset = offset
        inputSize = size
        inputStart = bytesRead
        inputPosition = offset
        inputEnd = offset + size
        if (size > 0) {
            isInputNeeded = false
        }
    }

    private fun fillBits(count: Int): Boolean {
        while (bitCount < count) {
            if (inputPosition >= inputEnd) return false
            bitBuffer = bitBuffer or ((input[inputPosition++].toInt() and 0xFF) shl bitCount)
            totalBytesRead++
            bitCount += Byte.SIZE_BITS
        }
        return true
    }

    private fun needBits(count: Int): Boolean {
        if (fillBits(count)) return true
        if (finishing) throw DataFormatException("Unexpected end of DEFLATE stream")
        isInputNeeded = true
        return false
    }

    private fun readBits(count: Int): Int {
        if (count == 0) return 0
        val bits = bitBuffer and ((1 shl count) - 1)
        skipBits(count)
        return bits
    }

    private fun skipBits(count: Int) {
        bitBuffer = bitBuffer ushr count
        bitCount -= count
    }

    private fun peekSymbol(tree: HuffmanTree): Int {
        fillBits(DeflateConstants.MAX_CODE_LENGTH)
        val code = tree.peekSymbolCode(bitBuffer, bitCount)
        if (code != HuffmanTree.NO_SYMBOL) return code
        if (finishing) throw DataFormatException("Unexpected end of DEFLATE stream")
        isInputNeeded = true
        return HuffmanTree.NO_SYMBOL
    }

    private fun writeLiteral(output: ByteArray, position: Int, value: Int): Int {
        val byte = value.toByte()
        output[position] = byte
        window[windowPosition] = byte
        windowPosition++
        if (windowPosition == window.size) {
            windowPosition = 0
        }
        if (windowFilled < window.size) {
            windowFilled++
        }
        return position + 1
    }

    private fun writeMatch(output: ByteArray, position: Int, limit: Int, length: Int, distance: Int): Int {
        if (distance !in 1..windowFilled) {
            throw DataFormatException("Invalid backwards distance: $distance")
        }
        pendingLength = length
        pendingDistance = distance
        var outputPosition = position
        var readPosition = windowPosition - distance
        if (readPosition < 0) {
            readPosition += window.size
        }
        while (pendingLength > 0 && outputPosition < limit) {
            val byte = window[readPosition]
            output[outputPosition++] = byte
            window[windowPosition] = byte
            readPosition++
            if (readPosition == window.size) {
                readPosition = 0
            }
            windowPosition++
            if (windowPosition == window.size) {
                windowPosition = 0
            }
            if (windowFilled < window.size) {
                windowFilled++
            }
            pendingLength--
        }
        if (pendingLength == 0) {
            pendingDistance = 0
        }
        return outputPosition
    }

    private fun finishBlock() {
        state = State.HEADER
        pendingLength = 0
        pendingDistance = 0
        if (isFinalBlock) ended = true
    }

    private fun beginDynamicHeader() {
        dynamicHeaderStage = DynamicHeaderStage.COUNTS
        dynamicLiteralCodesCount = 0
        dynamicDistanceCodesCount = 0
        dynamicCodeLengthCodesCount = 0
        dynamicCodeLengthIndex = 0
        dynamicLengthIndex = 0
        dynamicLengthsCount = 0
        dynamicPreviousLength = 0
        codeLengthLengths.fill(0)
        state = State.DYNAMIC_HEADER
    }

    private fun decodeBlockHeader(): Boolean {
        if (!needBits(1 + DeflateConstants.BTYPE_SIZE)) return false
        val header = readBits(1 + DeflateConstants.BTYPE_SIZE)
        isFinalBlock = (header and 1) != 0
        when (header ushr 1) {
            DeflateConstants.BTYPE_STORED.toInt() -> state = State.STORED_HEADER
            DeflateConstants.BTYPE_STATIC.toInt() -> {
                literalTree = FIXED_LITERAL_TREE
                distanceTree = FIXED_DISTANCE_TREE
                state = State.COMPRESSED
            }

            DeflateConstants.BTYPE_DYNAMIC.toInt() -> beginDynamicHeader()
            else -> throw DataFormatException("Invalid DEFLATE block type")
        }
        return true
    }

    private fun decodeStoredHeader(): Boolean {
        val padding = bitCount and 7
        if (!needBits(padding)) return false
        if (padding > 0) {
            skipBits(padding)
        }
        if (!needBits(Short.SIZE_BITS * 2)) return false
        val length = readBits(Short.SIZE_BITS)
        val inverseLength = readBits(Short.SIZE_BITS)
        if ((length xor 0xFFFF) != inverseLength) {
            throw DataFormatException("Invalid stored block length check")
        }
        storedRemaining = length
        state = State.STORED
        return true
    }

    private fun inflateStoredBlock(output: ByteArray, position: Int, limit: Int): Int {
        var outputPosition = position
        while (storedRemaining > 0 && outputPosition < limit) {
            if (!needBits(Byte.SIZE_BITS)) return outputPosition
            outputPosition = writeLiteral(output, outputPosition, readBits(Byte.SIZE_BITS))
            storedRemaining--
        }
        if (storedRemaining == 0) {
            finishBlock()
        }
        return outputPosition
    }

    private fun decodeDynamicCodeLengths(): Boolean {
        while (dynamicCodeLengthIndex < dynamicCodeLengthCodesCount) {
            if (!needBits(DeflateConstants.CL_CODE_LENGTH_SIZE)) return false
            val index = DeflateConstants.CODE_LENGTH_ORDER[dynamicCodeLengthIndex++]
            codeLengthLengths[index] = readBits(DeflateConstants.CL_CODE_LENGTH_SIZE)
        }
        dynamicLengthTree = HuffmanTree(codeLengthLengths, buildEncodeTable = false)
        dynamicHeaderStage = DynamicHeaderStage.TREE_LENGTHS
        return true
    }

    private fun decodeDynamicTreeLengths(): Boolean {
        while (dynamicLengthIndex < dynamicLengthsCount) {
            val code = peekSymbol(dynamicLengthTree)
            if (code == HuffmanTree.NO_SYMBOL) return false
            val symbol = HuffmanTree.unpackSymbol(code)
            val codeLength = HuffmanTree.unpackLength(code)
            val extraBits = when (symbol) {
                in 0..DeflateConstants.MAX_CODE_LENGTH -> 0
                DeflateConstants.SYM_REPEAT_PREVIOUS -> DeflateConstants.SYM_REPEAT_PREVIOUS_SIZE
                DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> DeflateConstants.SYM_REPEAT_ZERO_LENGTH_SIZE
                DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_SIZE
                else -> throw DataFormatException("Invalid code length symbol: $symbol")
            }
            if (extraBits > 0 && !needBits(codeLength + extraBits)) return false
            skipBits(codeLength)
            when (symbol) {
                in 0..DeflateConstants.MAX_CODE_LENGTH -> {
                    dynamicLengths[dynamicLengthIndex++] = symbol
                    dynamicPreviousLength = symbol
                }

                DeflateConstants.SYM_REPEAT_PREVIOUS -> {
                    if (dynamicLengthIndex == 0) {
                        throw DataFormatException("Cannot repeat previous code length before any length was read")
                    }
                    val repeatCount = DeflateConstants.SYM_REPEAT_PREVIOUS_MIN + readBits(extraBits)
                    if (dynamicLengthIndex + repeatCount > dynamicLengthsCount) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    val endIndex = dynamicLengthIndex + repeatCount
                    while (dynamicLengthIndex < endIndex) {
                        dynamicLengths[dynamicLengthIndex++] = dynamicPreviousLength
                    }
                }

                DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> {
                    val repeatCount = DeflateConstants.SYM_REPEAT_ZERO_LENGTH_MIN + readBits(extraBits)
                    if (dynamicLengthIndex + repeatCount > dynamicLengthsCount) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    val endIndex = dynamicLengthIndex + repeatCount
                    while (dynamicLengthIndex < endIndex) {
                        dynamicLengths[dynamicLengthIndex++] = 0
                    }
                    dynamicPreviousLength = 0
                }

                DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> {
                    val repeatCount = DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_MIN + readBits(extraBits)
                    if (dynamicLengthIndex + repeatCount > dynamicLengthsCount) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    val endIndex = dynamicLengthIndex + repeatCount
                    while (dynamicLengthIndex < endIndex) {
                        dynamicLengths[dynamicLengthIndex++] = 0
                    }
                    dynamicPreviousLength = 0
                }
            }
        }
        literalTree = HuffmanTree(dynamicLengths, size = dynamicLiteralCodesCount, buildEncodeTable = false)
        distanceTree = HuffmanTree(
            dynamicLengths, dynamicLiteralCodesCount, dynamicDistanceCodesCount, buildEncodeTable = false
        )
        state = State.COMPRESSED
        return true
    }

    private fun decodeDynamicHeader(): Boolean {
        if (dynamicHeaderStage == DynamicHeaderStage.COUNTS) {
            if (!needBits(DeflateConstants.HLIT_SIZE + DeflateConstants.HDIST_SIZE + DeflateConstants.HCLEN_SIZE)) {
                return false
            }
            dynamicLiteralCodesCount = DeflateConstants.HLIT_OFFSET + readBits(DeflateConstants.HLIT_SIZE)
            dynamicDistanceCodesCount = DeflateConstants.HDIST_OFFSET + readBits(DeflateConstants.HDIST_SIZE)
            dynamicCodeLengthCodesCount = DeflateConstants.HCLEN_OFFSET + readBits(DeflateConstants.HCLEN_SIZE)
            dynamicLengthsCount = dynamicLiteralCodesCount + dynamicDistanceCodesCount
            if (dynamicLengthsCount > dynamicLengths.size) {
                throw DataFormatException("Dynamic tree size exceeds maximum")
            }
            dynamicLengths.fill(0, 0, dynamicLengthsCount)
            dynamicHeaderStage = DynamicHeaderStage.CODE_LENGTH_CODES
        }
        if (dynamicHeaderStage == DynamicHeaderStage.CODE_LENGTH_CODES && !decodeDynamicCodeLengths()) return false
        return dynamicHeaderStage != DynamicHeaderStage.TREE_LENGTHS || decodeDynamicTreeLengths()
    }

    private fun decodeMatch(output: ByteArray, position: Int, limit: Int): Int {
        val code = peekSymbol(distanceTree)
        if (code == HuffmanTree.NO_SYMBOL) return position
        val distanceSymbol = HuffmanTree.unpackSymbol(code)
        val codeLength = HuffmanTree.unpackLength(code)
        if (distanceSymbol !in DeflateConstants.DIST_BASE.indices) {
            throw DataFormatException("Invalid distance symbol: $distanceSymbol")
        }
        val extraBits = DeflateConstants.DIST_EXTRA_BITS[distanceSymbol]
        if (extraBits > 0 && !needBits(codeLength + extraBits)) return position
        skipBits(codeLength)
        val distance = DeflateConstants.DIST_BASE[distanceSymbol] + readBits(extraBits)
        return writeMatch(output, position, limit, pendingLength, distance)
    }

    private fun inflateCompressedBlock(output: ByteArray, position: Int, limit: Int): Int {
        var outputPosition = position
        while (outputPosition < limit) {
            if (pendingLength > 0) {
                outputPosition = if (pendingDistance == 0) {
                    decodeMatch(output, outputPosition, limit)
                }
                else {
                    writeMatch(output, outputPosition, limit, pendingLength, pendingDistance)
                }
                if (pendingLength > 0) return outputPosition
                continue
            }
            val code = peekSymbol(literalTree)
            if (code == HuffmanTree.NO_SYMBOL) return outputPosition
            val symbol = HuffmanTree.unpackSymbol(code)
            val codeLength = HuffmanTree.unpackLength(code)
            when (symbol) {
                in 0 until DeflateConstants.SYM_EOF -> {
                    skipBits(codeLength)
                    outputPosition = writeLiteral(output, outputPosition, symbol)
                }

                DeflateConstants.SYM_EOF -> {
                    skipBits(codeLength)
                    finishBlock()
                    return outputPosition
                }

                in DeflateConstants.HLIT_OFFSET..285 -> {
                    val index = symbol - DeflateConstants.HLIT_OFFSET
                    val extraBits = DeflateConstants.LENGTH_EXTRA_BITS[index]
                    if (extraBits > 0 && !needBits(codeLength + extraBits)) return outputPosition
                    skipBits(codeLength)
                    pendingLength = DeflateConstants.LENGTH_BASE[index] + readBits(extraBits)
                    outputPosition = decodeMatch(output, outputPosition, limit)
                    if (pendingLength > 0) return outputPosition
                }

                else -> throw DataFormatException("Invalid literal/length symbol: $symbol")
            }
        }
        return outputPosition
    }

    private fun inflate(output: ByteArray, position: Int, limit: Int): Int = when (state) {
        State.HEADER -> if (decodeBlockHeader()) position else position
        State.STORED_HEADER -> if (decodeStoredHeader()) position else position
        State.STORED -> inflateStoredBlock(output, position, limit)
        State.DYNAMIC_HEADER -> if (decodeDynamicHeader()) position else position
        State.COMPRESSED -> inflateCompressedBlock(output, position, limit)
    }

    override fun decompress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        if (size <= 0) return 0
        if (finished) return 0
        val limit = offset + size
        var outputPosition = offset
        while (outputPosition < limit && !ended) {
            val previousPosition = outputPosition
            outputPosition = inflate(output, outputPosition, limit)
            if (outputPosition == previousPosition && isInputNeeded) break
        }
        val flushed = outputPosition - offset
        bytesWritten += flushed
        return flushed
    }

    override fun finish() {
        finishing = true
    }

    override fun reset() {
        input = ByteArray(0)
        inputOffset = 0
        inputSize = 0
        inputPosition = 0
        inputEnd = 0
        inputStart = 0L
        totalBytesRead = 0L
        bitBuffer = 0
        bitCount = 0
        window.fill(0)
        windowPosition = 0
        windowFilled = 0
        bytesWritten = 0L
        finishing = false
        ended = false
        isInputNeeded = true
        state = State.HEADER
        isFinalBlock = false
        storedRemaining = 0
        literalTree = FIXED_LITERAL_TREE
        distanceTree = FIXED_DISTANCE_TREE
        pendingLength = 0
        pendingDistance = 0
        beginDynamicHeader()
        state = State.HEADER
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
    }
}

/**
 * Creates a new decompressor using the specified compression level.
 * **Note that [Inflater] instances are NOT threadsafe!**
 *
 * @return A new [Inflater] instance with the given parameters.
 */
fun Inflater(): Inflater = InflaterImpl()

/**
 * Returns a [RawSource] that reads DEFLATE-compressed bytes from this source
 * and emits their uncompressed form.
 *
 * This is a streaming wrapper: bytes are decompressed on the fly as you read
 * from the returned source. Close the returned source when finished to free
 * any underlying resources.
 *
 * @param bufferSize Size of the internal working buffers used during
 *  decompression.
 * @return A [RawSource] that produces decompressed data.
 */
fun RawSource.inflatingSource( // @formatter:off
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSource = decompressingSource(Inflater(), bufferSize) // @formatter:on

/**
 * Returns a [RawSink] that decompresses written bytes using DEFLATE and
 * writes them to this sink.
 *
 * This is a streaming wrapper: bytes are decompressed on the fly as you write
 * to the returned sink. Close the returned sink when finished to free
 * any underlying resources and ensure all data is flushed.
 *
 * @param bufferSize Size of the internal working buffers used during
 *  decompression.
 * @return A [RawSink] that accepts compressed data and writes decompressed data.
 */
fun RawSink.inflatingSink( // @formatter:off
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSink = decompressingSink(Inflater(), bufferSize) // @formatter:on