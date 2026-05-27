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
import dev.karmakrafts.karbide.bitSource
import dev.karmakrafts.karbide.readBit
import dev.karmakrafts.karbide.readBitsLsb
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

    private companion object {
        val FIXED_LITERAL_TREE: HuffmanTree = HuffmanTree(DeflateConstants.FIXED_LIT_TREE_LENGTHS)
        val FIXED_DISTANCE_TREE: HuffmanTree = HuffmanTree(DeflateConstants.FIXED_DIST_TREE_LENGTHS)
    }

    override var input: ByteArray = ByteArray(0)
        private set
    override var inputOffset: Int = 0
        private set
    override var inputSize: Int = 0
        private set

    override val remaining: Int
        get() {
            val consumed = (bytesRead - inputStart).toInt()
            return (inputSize - consumed).coerceIn(0, inputSize)
        }

    override val bytesRead: Long get() = bitSource.byte + if (bitSource.bit == 0) 0 else 1
    override var bytesWritten: Long = 0L
        private set
    override val needsInput: Boolean get() = !finished && outputBuffer.size == 0L && isInputNeeded
    override val finished: Boolean get() = ended && outputBuffer.size == 0L

    private val inputBuffer: Buffer = Buffer()
    private val bitSource = inputBuffer.bitSource(isSourceOwned = false, bitOrder = BitOrder.LSB_FIRST)
    private val outputBuffer: Buffer = Buffer()
    private val lz77: LZ77 = LZ77(windowSize = windowSize)
    private val codeLengthLengths: IntArray = IntArray(DeflateConstants.CODE_LENGTH_ALPHABET_SIZE)

    private var isClosed: Boolean = false
    private var inputStart: Long = 0L
    private var finishing: Boolean = false
    private var ended: Boolean = false
    private var isInputNeeded: Boolean = true

    private var state: State = State.HEADER
    private var isFinalBlock: Boolean = false
    private var storedRemaining: Int = 0
    private var literalTree: HuffmanTree = FIXED_LITERAL_TREE
    private var distanceTree: HuffmanTree = FIXED_DISTANCE_TREE

    private var dynamicHeaderStage: Int = 0
    private var dynamicLiteralCodesCount: Int = 0
    private var dynamicDistanceCodesCount: Int = 0
    private var dynamicCodeLengthCodesCount: Int = 0
    private var dynamicCodeLengthIndex: Int = 0
    private var dynamicLengthIndex: Int = 0
    private var dynamicPreviousLength: Int = 0
    private var dynamicLengths: IntArray = IntArray(0)
    private var dynamicLengthTree: HuffmanTree = HuffmanTree()

    private var pendingLength: Int = 0

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        input = data
        inputOffset = offset
        inputSize = size
        inputStart = bytesRead
        if (size > 0) {
            inputBuffer.write(data, offset, offset + size)
            isInputNeeded = false
        }
    }

    private fun needBits(count: Int): Boolean {
        if (bitSource.requestBits(count)) return true
        if (finishing) throw DataFormatException("Unexpected end of DEFLATE stream")
        isInputNeeded = true
        return false
    }

    private fun peekSymbol(tree: HuffmanTree): Pair<Int, Int>? {
        return tree.peekSymbol(bitSource) ?: run {
            if (finishing) throw DataFormatException("Unexpected end of DEFLATE stream")
            isInputNeeded = true
            null
        }
    }

    private fun finishBlock() {
        state = State.HEADER
        pendingLength = 0
        if (isFinalBlock) {
            ended = true
        }
    }

    private fun beginDynamicHeader() {
        dynamicHeaderStage = 0
        dynamicLiteralCodesCount = 0
        dynamicDistanceCodesCount = 0
        dynamicCodeLengthCodesCount = 0
        dynamicCodeLengthIndex = 0
        dynamicLengthIndex = 0
        dynamicPreviousLength = 0
        dynamicLengths = IntArray(0)
        dynamicLengthTree = HuffmanTree()
        codeLengthLengths.fill(0)
        state = State.DYNAMIC_HEADER
    }

    private fun decodeBlockHeader(): Boolean {
        if (!needBits(1 + DeflateConstants.BTYPE_SIZE)) return false
        isFinalBlock = bitSource.readBit() != 0.toUByte()
        when (bitSource.readBitsLsb(DeflateConstants.BTYPE_SIZE)) {
            DeflateConstants.BTYPE_STORED -> state = State.STORED_HEADER
            DeflateConstants.BTYPE_STATIC -> {
                literalTree = FIXED_LITERAL_TREE
                distanceTree = FIXED_DISTANCE_TREE
                state = State.COMPRESSED
            }

            DeflateConstants.BTYPE_DYNAMIC -> beginDynamicHeader()
            else -> throw DataFormatException("Invalid DEFLATE block type")
        }
        return true
    }

    private fun decodeStoredHeader(): Boolean {
        val padding = (Byte.SIZE_BITS - bitSource.bit) and 7
        if (!needBits(padding)) return false
        if (padding > 0) {
            bitSource.skipBits(padding)
        }
        if (!needBits(Short.SIZE_BITS * 2)) return false
        val length = bitSource.readBitsLsb(Short.SIZE_BITS).toInt()
        val inverseLength = bitSource.readBitsLsb(Short.SIZE_BITS).toInt()
        if ((length xor 0xFFFF) != inverseLength) {
            throw DataFormatException("Invalid stored block length check")
        }
        storedRemaining = length
        state = State.STORED
        return true
    }

    private fun inflateStoredBlock(targetSize: Long): Boolean {
        while (storedRemaining > 0 && outputBuffer.size < targetSize) {
            if (!needBits(Byte.SIZE_BITS)) return false
            lz77.decodeLiteral(outputBuffer, bitSource.readBitsLsb(Byte.SIZE_BITS).toInt())
            storedRemaining--
        }
        if (storedRemaining == 0) {
            finishBlock()
        }
        return true
    }

    private fun decodeDynamicCodeLengths(): Boolean {
        while (dynamicCodeLengthIndex < dynamicCodeLengthCodesCount) {
            if (!needBits(DeflateConstants.CL_CODE_LENGTH_SIZE)) return false
            val index = DeflateConstants.CODE_LENGTH_ORDER[dynamicCodeLengthIndex++]
            codeLengthLengths[index] = bitSource.readBitsLsb(DeflateConstants.CL_CODE_LENGTH_SIZE).toInt()
        }
        dynamicLengthTree = HuffmanTree(codeLengthLengths)
        dynamicHeaderStage = 2
        return true
    }

    private fun decodeDynamicTreeLengths(): Boolean {
        while (dynamicLengthIndex < dynamicLengths.size) {
            val (symbol, codeLength) = peekSymbol(dynamicLengthTree) ?: return false
            val extraBits = when (symbol) {
                in 0..DeflateConstants.MAX_CODE_LENGTH -> 0
                DeflateConstants.SYM_REPEAT_PREVIOUS -> DeflateConstants.SYM_REPEAT_PREVIOUS_SIZE
                DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> DeflateConstants.SYM_REPEAT_ZERO_LENGTH_SIZE
                DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_SIZE
                else -> throw DataFormatException("Invalid code length symbol: $symbol")
            }
            if (!needBits(codeLength + extraBits)) return false
            bitSource.skipBits(codeLength)
            when (symbol) {
                in 0..DeflateConstants.MAX_CODE_LENGTH -> {
                    dynamicLengths[dynamicLengthIndex++] = symbol
                    dynamicPreviousLength = symbol
                }

                DeflateConstants.SYM_REPEAT_PREVIOUS -> {
                    if (dynamicLengthIndex == 0) {
                        throw DataFormatException("Cannot repeat previous code length before any length was read")
                    }
                    val repeatCount =
                        DeflateConstants.SYM_REPEAT_PREVIOUS_MIN + bitSource.readBitsLsb(extraBits).toInt()
                    if (dynamicLengthIndex + repeatCount > dynamicLengths.size) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    repeat(repeatCount) {
                        dynamicLengths[dynamicLengthIndex++] = dynamicPreviousLength
                    }
                }

                DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> {
                    val repeatCount =
                        DeflateConstants.SYM_REPEAT_ZERO_LENGTH_MIN + bitSource.readBitsLsb(extraBits).toInt()
                    if (dynamicLengthIndex + repeatCount > dynamicLengths.size) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    repeat(repeatCount) {
                        dynamicLengths[dynamicLengthIndex++] = 0
                    }
                    dynamicPreviousLength = 0
                }

                DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> {
                    val repeatCount =
                        DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_MIN + bitSource.readBitsLsb(extraBits).toInt()
                    if (dynamicLengthIndex + repeatCount > dynamicLengths.size) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    repeat(repeatCount) {
                        dynamicLengths[dynamicLengthIndex++] = 0
                    }
                    dynamicPreviousLength = 0
                }
            }
        }
        val literalLengths = dynamicLengths.copyOfRange(0, dynamicLiteralCodesCount)
        val distanceLengths = dynamicLengths.copyOfRange(
            dynamicLiteralCodesCount, dynamicLiteralCodesCount + dynamicDistanceCodesCount
        )
        literalTree = HuffmanTree(literalLengths)
        distanceTree = HuffmanTree(distanceLengths)
        state = State.COMPRESSED
        return true
    }

    private fun decodeDynamicHeader(): Boolean {
        if (dynamicHeaderStage == 0) {
            if (!needBits(DeflateConstants.HLIT_SIZE + DeflateConstants.HDIST_SIZE + DeflateConstants.HCLEN_SIZE)) {
                return false
            }
            dynamicLiteralCodesCount =
                DeflateConstants.HLIT_OFFSET + bitSource.readBitsLsb(DeflateConstants.HLIT_SIZE).toInt()
            dynamicDistanceCodesCount =
                DeflateConstants.HDIST_OFFSET + bitSource.readBitsLsb(DeflateConstants.HDIST_SIZE).toInt()
            dynamicCodeLengthCodesCount =
                DeflateConstants.HCLEN_OFFSET + bitSource.readBitsLsb(DeflateConstants.HCLEN_SIZE).toInt()
            dynamicLengths = IntArray(dynamicLiteralCodesCount + dynamicDistanceCodesCount)
            dynamicHeaderStage = 1
        }
        if (dynamicHeaderStage == 1 && !decodeDynamicCodeLengths()) return false
        return dynamicHeaderStage != 2 || decodeDynamicTreeLengths()
    }

    private fun inflatePendingMatch(): Boolean {
        val (distanceSymbol, codeLength) = peekSymbol(distanceTree) ?: return false
        if (distanceSymbol !in DeflateConstants.DIST_BASE.indices) {
            throw DataFormatException("Invalid distance symbol: $distanceSymbol")
        }
        val extraBits = DeflateConstants.DIST_EXTRA_BITS[distanceSymbol]
        if (!needBits(codeLength + extraBits)) return false
        bitSource.skipBits(codeLength)
        val distance = DeflateConstants.DIST_BASE[distanceSymbol] + bitSource.readBitsLsb(extraBits).toInt()
        lz77.decodeMatch(outputBuffer, pendingLength, distance)
        pendingLength = 0
        return true
    }

    private fun inflateCompressedBlock(targetSize: Long): Boolean {
        while (outputBuffer.size < targetSize) {
            if (pendingLength > 0) {
                return inflatePendingMatch()
            }
            val (symbol, codeLength) = peekSymbol(literalTree) ?: return false
            when (symbol) {
                in 0 until DeflateConstants.SYM_EOF -> {
                    bitSource.skipBits(codeLength)
                    lz77.decodeLiteral(outputBuffer, symbol)
                }

                DeflateConstants.SYM_EOF -> {
                    bitSource.skipBits(codeLength)
                    finishBlock()
                    return true
                }

                in DeflateConstants.HLIT_OFFSET..285 -> {
                    val index = symbol - DeflateConstants.HLIT_OFFSET
                    val extraBits = DeflateConstants.LENGTH_EXTRA_BITS[index]
                    if (!needBits(codeLength + extraBits)) return false
                    bitSource.skipBits(codeLength)
                    pendingLength = DeflateConstants.LENGTH_BASE[index] + bitSource.readBitsLsb(extraBits).toInt()
                    if (!inflatePendingMatch()) return false
                }

                else -> throw DataFormatException("Invalid literal/length symbol: $symbol")
            }
        }
        return true
    }

    private fun inflate(targetSize: Long): Boolean = when (state) {
        State.HEADER -> decodeBlockHeader()
        State.STORED_HEADER -> decodeStoredHeader()
        State.STORED -> inflateStoredBlock(targetSize)
        State.DYNAMIC_HEADER -> decodeDynamicHeader()
        State.COMPRESSED -> inflateCompressedBlock(targetSize)
    }

    override fun decompress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        if (size <= 0) return 0
        var flushed = outputBuffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
        if (flushed > 0) {
            bytesWritten += flushed
            return flushed
        }
        if (finished) return 0
        val targetSize = size.toLong()
        while (outputBuffer.size < targetSize && !ended) {
            if (!inflate(targetSize)) break
        }
        flushed = outputBuffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
        bytesWritten += flushed
        return flushed
    }

    override fun finish() {
        finishing = true
    }

    override fun reset() {
        inputBuffer.clear()
        bitSource.reset()
        outputBuffer.clear()
        input = ByteArray(0)
        inputOffset = 0
        inputSize = 0
        inputStart = 0L
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
        lz77.resetDecoder()
        beginDynamicHeader()
        state = State.HEADER
    }

    override fun close() {
        if (isClosed) return
        bitSource.close()
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