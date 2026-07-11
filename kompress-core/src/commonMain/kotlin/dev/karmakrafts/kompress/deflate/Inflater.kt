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
import dev.karmakrafts.karbide.BitSource
import dev.karmakrafts.karbide.bitSource
import dev.karmakrafts.karbide.readBitsLsb
import dev.karmakrafts.karbide.source
import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.decompressingSink
import dev.karmakrafts.kompress.decompressingSource
import dev.karmakrafts.kompress.exception.DataFormatException
import dev.karmakrafts.kompress.huffman.HuffmanTree
import dev.karmakrafts.kompress.lz77.LZ77
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.buffered
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

@OptIn(InternalCompressionApi::class)
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

    override val remaining: Int get() = (inputSize - bytesReadInCurrentInput()).coerceIn(0, inputSize)

    override val bytesRead: Long get() = bitSource.byte + if (bitSource.bit > 0) 1 else 0
    override var bytesWritten: Long = 0L
        private set
    override val needsInput: Boolean get() = !finished && isInputNeeded
    override val finished: Boolean get() = ended

    private val window: ByteArray = ByteArray(windowSize)
    private val windowSize: Int = window.size
    private val windowMask: Int = if (windowSize > 0 && windowSize and (windowSize - 1) == 0) windowSize - 1 else -1
    private val codeLengthLengths: IntArray = IntArray(DeflateConstants.CODE_LENGTH_ALPHABET_SIZE)
    private val inputSource = input.source()
    private val bitSource: BitSource = inputSource.buffered().bitSource(bitOrder = BitOrder.LSB_FIRST)

    private var isClosed: Boolean = false
    private var inputStart: Long = 0L
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

    private fun bytesReadInCurrentInput(): Int = (bytesRead - inputStart).toInt()

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        input = data
        inputOffset = offset
        inputSize = size
        inputStart = bytesRead
        inputSource.array = data
        inputSource.offset = offset
        inputSource.size = size
        inputSource.reset()
        if (size > 0) {
            isInputNeeded = false
        }
    }

    private fun needBits(count: Int): Boolean {
        if (bitSource.requestBits(count)) return true
        if (finishing) throw DataFormatException("Unexpected end of DEFLATE stream")
        isInputNeeded = true
        return false
    }

    private fun peekSymbol(tree: HuffmanTree): Int {
        val code = tree.peekSymbolCode(bitSource)
        if (code != HuffmanTree.NO_SYMBOL) return code
        if (finishing) throw DataFormatException("Unexpected end of DEFLATE stream")
        isInputNeeded = true
        return HuffmanTree.NO_SYMBOL
    }

    private fun writeLiteral(output: ByteArray, position: Int, value: Int): Int {
        val byte = value.toByte()
        output[position] = byte
        val window = window
        val windowSize = windowSize
        val writePosition = windowPosition
        window[writePosition] = byte
        if (windowMask != -1) {
            windowPosition = (writePosition + 1) and windowMask
        }
        else {
            val nextPosition = writePosition + 1
            windowPosition = if (nextPosition == windowSize) 0 else nextPosition
        }
        if (windowFilled < windowSize) {
            windowFilled++
        }
        return position + 1
    }

    /**
     * Copies a length/distance pair from the sliding history window into the output.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.3.
     */
    private fun writeMatch(output: ByteArray, position: Int, limit: Int, length: Int, distance: Int): Int {
        if (distance !in 1..windowFilled) {
            throw DataFormatException("Invalid backwards distance: $distance")
        }
        pendingDistance = distance
        var readPosition = windowPosition - distance
        if (readPosition < 0) {
            readPosition += windowSize
        }

        val copyLimit = minOf(length, limit - position)
        return when {
            distance == 1 -> writeRepeatedMatch(output, position, copyLimit, length, readPosition)
            distance >= copyLimit -> writeBulkMatch(output, position, copyLimit, length, readPosition)
            else -> writeOverlappingMatch(output, position, copyLimit, length, distance, readPosition)
        }
    }

    private fun writeRepeatedMatch(
        output: ByteArray, position: Int, copyLimit: Int, length: Int, readPosition: Int
    ): Int {
        val window = window
        val windowSize = windowSize
        var writePosition = windowPosition
        val byte = window[readPosition]
        output.fill(byte, position, position + copyLimit)
        var copied = 0
        while (copied < copyLimit) {
            val writeChunk = minOf(copyLimit - copied, windowSize - writePosition)
            window.fill(byte, writePosition, writePosition + writeChunk)
            copied += writeChunk
            writePosition += writeChunk
            if (writePosition == windowSize) {
                writePosition = 0
            }
        }

        finishMatchWrite(writePosition, copyLimit, length)
        return position + copyLimit
    }

    private fun writeBulkMatch(
        output: ByteArray, position: Int, copyLimit: Int, length: Int, initialReadPosition: Int
    ): Int {
        val window = window
        val windowSize = windowSize
        var readPosition = initialReadPosition
        var writePosition = windowPosition
        var outputPosition = position
        var copied = 0
        while (copied < copyLimit) {
            val readChunk = minOf(copyLimit - copied, windowSize - readPosition)
            window.copyInto(output, outputPosition, readPosition, readPosition + readChunk)
            val writeChunk = minOf(readChunk, windowSize - writePosition)
            output.copyInto(window, writePosition, outputPosition, outputPosition + writeChunk)
            if (writeChunk < readChunk) {
                output.copyInto(window, 0, outputPosition + writeChunk, outputPosition + readChunk)
            }
            writePosition += readChunk
            if (writePosition >= windowSize) {
                writePosition -= windowSize
            }
            copied += readChunk
            outputPosition += readChunk
            readPosition += readChunk
            if (readPosition == windowSize) {
                readPosition = 0
            }
        }

        finishMatchWrite(writePosition, copied, length)
        return outputPosition
    }

    private fun writeOverlappingMatch(
        output: ByteArray,
        position: Int,
        copyLimit: Int,
        length: Int,
        distance: Int,
        initialReadPosition: Int
    ): Int {
        val window = window
        val windowSize = windowSize
        var readPosition = initialReadPosition
        var writePosition = windowPosition

        if (copyLimit < 8) {
            var outputPosition = position
            val endPosition = position + copyLimit
            if (windowMask != -1) {
                val windowMask = windowMask
                while (outputPosition < endPosition) {
                    val byte = window[readPosition]
                    output[outputPosition++] = byte
                    window[writePosition] = byte
                    readPosition = (readPosition + 1) and windowMask
                    writePosition = (writePosition + 1) and windowMask
                }
            }
            else {
                while (outputPosition < endPosition) {
                    val byte = window[readPosition]
                    output[outputPosition++] = byte
                    window[writePosition] = byte
                    readPosition++
                    if (readPosition == windowSize) {
                        readPosition = 0
                    }
                    writePosition++
                    if (writePosition == windowSize) {
                        writePosition = 0
                    }
                }
            }

            finishMatchWrite(writePosition, copyLimit, length)
            return outputPosition
        }

        val firstChunk = minOf(distance, windowSize - readPosition)
        window.copyInto(output, position, readPosition, readPosition + firstChunk)
        var copied = firstChunk
        if (copied < distance) {
            val secondChunk = distance - copied
            window.copyInto(output, position + copied, 0, secondChunk)
            copied += secondChunk
        }
        while (copied < copyLimit) {
            val copyChunk = minOf(copied, copyLimit - copied)
            output.copyInto(output, position + copied, position, position + copyChunk)
            copied += copyChunk
        }
        copied = 0
        while (copied < copyLimit) {
            val writeChunk = minOf(copyLimit - copied, windowSize - writePosition)
            output.copyInto(window, writePosition, position + copied, position + copied + writeChunk)
            copied += writeChunk
            writePosition += writeChunk
            if (writePosition == windowSize) {
                writePosition = 0
            }
        }

        finishMatchWrite(writePosition, copyLimit, length)
        return position + copyLimit
    }

    private fun finishMatchWrite(writePosition: Int, copied: Int, length: Int) {
        windowPosition = writePosition
        val filled = windowFilled
        windowFilled = if (windowSize - filled > copied) filled + copied else windowSize
        pendingLength = length - copied
        if (pendingLength == 0) {
            pendingDistance = 0
        }
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

    /**
     * Decodes the DEFLATE block header containing the final-block flag and block type.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.3.
     */
    private fun decodeBlockHeader(): Boolean {
        if (!needBits(1 + DeflateConstants.BTYPE_SIZE)) return false
        val header = bitSource.readBitsLsb(1 + DeflateConstants.BTYPE_SIZE).toInt()
        // RFC1951 3.2.3: BFINAL marks the last block, followed by two BTYPE bits.
        isFinalBlock = (header and 1) != 0
        when (header ushr 1) {
            DeflateConstants.BTYPE_STORED.toInt() -> state = State.STORED_HEADER
            DeflateConstants.BTYPE_STATIC.toInt() -> {
                // RFC1951 3.2.6: fixed Huffman blocks use the predefined literal/length and
                // distance trees.
                literalTree = FIXED_LITERAL_TREE
                distanceTree = FIXED_DISTANCE_TREE
                state = State.COMPRESSED
            }

            DeflateConstants.BTYPE_DYNAMIC.toInt() -> beginDynamicHeader()
            else -> throw DataFormatException("Invalid DEFLATE block type")
        }
        return true
    }

    /**
     * Decodes a stored block header by aligning to the next byte and validating LEN/NLEN.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.4.
     */
    private fun decodeStoredHeader(): Boolean {
        val padding = (Byte.SIZE_BITS - bitSource.bit) and 7
        if (!needBits(padding)) return false
        if (padding > 0) {
            bitSource.skipBits(padding)
        }
        if (!needBits(Short.SIZE_BITS * 2)) return false
        val length = bitSource.readBitsLsb(Short.SIZE_BITS).toInt()
        val inverseLength = bitSource.readBitsLsb(Short.SIZE_BITS).toInt()
        // RFC1951 3.2.4: NLEN is the one's-complement of LEN.
        if ((length xor 0xFFFF) != inverseLength) {
            throw DataFormatException("Invalid stored block length check")
        }
        storedRemaining = length
        state = State.STORED
        return true
    }

    /**
     * Copies the uncompressed bytes from a stored block.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.4.
     */
    private fun inflateStoredBlock(output: ByteArray, position: Int, limit: Int): Int {
        var outputPosition = position
        var remaining = storedRemaining
        val window = window
        val windowSize = windowSize
        val windowMask = windowMask
        var writePosition = windowPosition
        val startPosition = outputPosition
        while (remaining > 0 && outputPosition < limit) {
            if (!needBits(Byte.SIZE_BITS)) break
            val byte = bitSource.readBitsLsb(Byte.SIZE_BITS).toByte()
            output[outputPosition++] = byte
            window[writePosition] = byte
            if (windowMask != -1) {
                writePosition = (writePosition + 1) and windowMask
            }
            else {
                writePosition++
                if (writePosition == windowSize) {
                    writePosition = 0
                }
            }
            remaining--
        }
        storedRemaining = remaining
        windowPosition = writePosition
        val copied = outputPosition - startPosition
        if (copied > 0) {
            val filled = windowFilled
            windowFilled = if (windowSize - filled > copied) filled + copied else windowSize
        }
        if (remaining == 0) {
            finishBlock()
        }
        return outputPosition
    }

    /**
     * Decodes the code-length alphabet for a dynamic Huffman block.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.7.
     */
    private fun decodeDynamicCodeLengths(): Boolean {
        while (dynamicCodeLengthIndex < dynamicCodeLengthCodesCount) {
            if (!needBits(DeflateConstants.CL_CODE_LENGTH_SIZE)) return false
            // RFC1951 3.2.7: code-length code lengths are serialized in CODE_LENGTH_ORDER.
            val index = DeflateConstants.CODE_LENGTH_ORDER[dynamicCodeLengthIndex++]
            codeLengthLengths[index] = bitSource.readBitsLsb(DeflateConstants.CL_CODE_LENGTH_SIZE).toInt()
        }
        // RFC1951 3.2.7: this tree decodes the literal/length and distance code lengths.
        dynamicLengthTree = HuffmanTree(codeLengthLengths, buildEncodeTable = false)
        dynamicHeaderStage = DynamicHeaderStage.TREE_LENGTHS
        return true
    }

    /**
     * Decodes the literal/length and distance Huffman tree lengths, including repeat symbols 16,
     * 17 and 18.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.7.
     */
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
                    // RFC1951 3.2.7: code 16 repeats the previous non-zero length 3-6 times using
                    // 2 extra bits.
                    val repeatCount =
                        DeflateConstants.SYM_REPEAT_PREVIOUS_MIN + bitSource.readBitsLsb(extraBits).toInt()
                    if (dynamicLengthIndex + repeatCount > dynamicLengthsCount) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    val endIndex = dynamicLengthIndex + repeatCount
                    dynamicLengths.fill(dynamicPreviousLength, dynamicLengthIndex, endIndex)
                    dynamicLengthIndex = endIndex
                }

                DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> {
                    // RFC1951 3.2.7: code 17 repeats zero lengths 3-10 times using 3 extra bits.
                    val repeatCount =
                        DeflateConstants.SYM_REPEAT_ZERO_LENGTH_MIN + bitSource.readBitsLsb(extraBits).toInt()
                    if (dynamicLengthIndex + repeatCount > dynamicLengthsCount) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    val endIndex = dynamicLengthIndex + repeatCount
                    dynamicLengthIndex = endIndex
                    dynamicPreviousLength = 0
                }

                DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> {
                    // RFC1951 3.2.7: code 18 repeats zero lengths 11-138 times using 7 extra bits.
                    val repeatCount =
                        DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_MIN + bitSource.readBitsLsb(extraBits).toInt()
                    if (dynamicLengthIndex + repeatCount > dynamicLengthsCount) {
                        throw DataFormatException("Code length repeat exceeds dynamic tree size")
                    }
                    val endIndex = dynamicLengthIndex + repeatCount
                    dynamicLengthIndex = endIndex
                    dynamicPreviousLength = 0
                }
            }
        }
        // RFC1951 3.2.7: the first HLIT lengths build the literal/length tree; the following
        // HDIST build distances.
        literalTree = HuffmanTree(dynamicLengths, size = dynamicLiteralCodesCount, buildEncodeTable = false)
        distanceTree = HuffmanTree(
            dynamicLengths, dynamicLiteralCodesCount, dynamicDistanceCodesCount, buildEncodeTable = false
        )
        state = State.COMPRESSED
        return true
    }

    /**
     * Decodes the dynamic Huffman block header fields (HLIT, HDIST, HCLEN) and trees.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.7.
     */
    private fun decodeDynamicHeader(): Boolean {
        if (dynamicHeaderStage == DynamicHeaderStage.COUNTS) {
            if (!needBits(DeflateConstants.HLIT_SIZE + DeflateConstants.HDIST_SIZE + DeflateConstants.HCLEN_SIZE)) {
                return false
            }
            // RFC1951 3.2.7: HLIT, HDIST and HCLEN store count values minus their RFC-defined
            // offsets.
            dynamicLiteralCodesCount =
                DeflateConstants.HLIT_OFFSET + bitSource.readBitsLsb(DeflateConstants.HLIT_SIZE).toInt()
            dynamicDistanceCodesCount =
                DeflateConstants.HDIST_OFFSET + bitSource.readBitsLsb(DeflateConstants.HDIST_SIZE).toInt()
            dynamicCodeLengthCodesCount =
                DeflateConstants.HCLEN_OFFSET + bitSource.readBitsLsb(DeflateConstants.HCLEN_SIZE).toInt()
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

    /**
     * Decodes the distance part of a length/distance pair and copies the referenced match.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) sections 3.2.3 and 3.2.5.
     */
    private fun decodeMatch(output: ByteArray, position: Int, limit: Int): Int {
        val code = peekSymbol(distanceTree)
        if (code == HuffmanTree.NO_SYMBOL) return position
        val distanceSymbol = HuffmanTree.unpackSymbol(code)
        val codeLength = HuffmanTree.unpackLength(code)
        if (distanceSymbol < 0 || distanceSymbol >= DeflateConstants.DIST_BASE.size) {
            throw DataFormatException("Invalid distance symbol: $distanceSymbol")
        }
        // RFC1951 3.2.5: distance symbols may be followed by extra bits added to the base
        // distance.
        val extraBits = DeflateConstants.DIST_EXTRA_BITS[distanceSymbol]
        if (extraBits > 0 && !needBits(codeLength + extraBits)) return position
        bitSource.skipBits(codeLength)
        val distance = DeflateConstants.DIST_BASE[distanceSymbol] + if (extraBits == 0) 0
        else bitSource.readBitsLsb(extraBits).toInt()
        return writeMatch(output, position, limit, pendingLength, distance)
    }

    /**
     * Decodes compressed block data using the current literal/length and distance Huffman trees.
     *
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) sections 3.2.3 and 3.2.5.
     */
    private fun inflateCompressedBlock(output: ByteArray, position: Int, limit: Int): Int {
        var outputPosition = position
        while (outputPosition < limit) {
            if (pendingLength > 0) {
                outputPosition = if (pendingDistance == 0) decodeMatch(output, outputPosition, limit)
                else writeMatch(output, outputPosition, limit, pendingLength, pendingDistance)
                if (pendingLength > 0) return outputPosition
                continue
            }
            val code = peekSymbol(literalTree)
            if (code == HuffmanTree.NO_SYMBOL) return outputPosition
            val symbol = HuffmanTree.unpackSymbol(code)
            val codeLength = HuffmanTree.unpackLength(code)
            when {
                symbol < DeflateConstants.SYM_EOF -> {
                    bitSource.skipBits(codeLength)
                    outputPosition = writeLiteral(output, outputPosition, symbol)
                }

                symbol == DeflateConstants.SYM_EOF -> {
                    // RFC1951 3.2.3: literal/length symbol 256 marks the end of the block.
                    bitSource.skipBits(codeLength)
                    finishBlock()
                    return outputPosition
                }

                symbol <= 285 -> {
                    val index = symbol - DeflateConstants.HLIT_OFFSET
                    // RFC1951 3.2.5: length symbols may be followed by extra bits added to the
                    // base length.
                    val extraBits = DeflateConstants.LENGTH_EXTRA_BITS[index]
                    if (extraBits > 0 && !needBits(codeLength + extraBits)) return outputPosition
                    bitSource.skipBits(codeLength)
                    pendingLength = DeflateConstants.LENGTH_BASE[index] + if (extraBits == 0) 0
                    else bitSource.readBitsLsb(extraBits).toInt()
                    outputPosition = decodeMatch(output, outputPosition, limit)
                    if (pendingLength > 0) return outputPosition
                }

                else -> throw DataFormatException("Invalid literal/length symbol: $symbol")
            }
        }
        return outputPosition
    }

    private fun inflate(output: ByteArray, position: Int, limit: Int): Int = when (state) { // @formatter:off
        State.HEADER -> {
            decodeBlockHeader()
            position
        }
        State.STORED_HEADER -> {
            decodeStoredHeader()
            position
        }
        State.STORED -> inflateStoredBlock(output, position, limit)
        State.DYNAMIC_HEADER -> {
            decodeDynamicHeader()
            position
        }
        State.COMPRESSED -> inflateCompressedBlock(output, position, limit)
    } // @formatter:on

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
        inputSource.array = input
        inputSource.offset = 0
        inputSource.size = 0
        inputSource.reset()
        bitSource.reset()
        inputStart = 0L
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