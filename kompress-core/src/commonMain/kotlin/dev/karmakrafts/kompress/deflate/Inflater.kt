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

import dev.karmakrafts.karbide.BitSource
import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.decompressingSink
import dev.karmakrafts.kompress.decompressingSource
import dev.karmakrafts.kompress.deflate.Inflater.Companion.decompress
import dev.karmakrafts.kompress.huffman.HuffmanTree
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
         * @param raw If true, the ZLIB header and checksum fields will not be used
         *  in order to support the compression format used in both GZIP and PKZIP.
         * @param bufferSize The size of the intermediate buffer used during compression.
         * @return The decompressed data.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun decompress( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = Inflater(raw).use { inflater -> // @formatter:on
            inflater.decompressBulk(data, bufferSize)
        }

        /**
         * @see decompress
         */
        @Deprecated( // @formatter:off
            message = "This API will be removed in 2.0",
            replaceWith = ReplaceWith("decompress(data, raw, bufferSize)")
        ) // @formatter:on
        fun inflate( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = decompress(data, raw, bufferSize) // @formatter:on

        /**
         * Computes the compressed size of the given data by inflating it and
         * subtracting the remaining bytes from the original size.
         *
         * @param data The compressed data.
         * @param raw If true, the ZLIB header and checksum fields will not be used.
         * @param bufferSize The size of the intermediate buffer used during decompression.
         * @return The size of the compressed data that was actually consumed.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun computeCompressedSize( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): Int = Inflater(raw).use { inflater -> // @formatter:on
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
         * @param raw If true, the ZLIB header and checksum fields will not be used.
         * @param bufferSize The size of the intermediate buffer used during decompression.
         * @return The size of the compressed data that was actually consumed from the source.
         * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
         */
        fun computeCompressedSize( // @formatter:off
            source: RawSource,
            raw: Boolean = true,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): Long = Inflater(raw).use { inflater -> // @formatter:on
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
                if (inflater.decompress(outputBuffer) == 0) break
            }
            totalRead - inflater.remaining
        }
    }
}

private class NewInflaterImpl : Inflater {
    override var input: ByteArray
        get() = TODO("Not yet implemented")
        set(value) {}
    override val inputOffset: Int
        get() = TODO("Not yet implemented")
    override val inputSize: Int
        get() = TODO("Not yet implemented")
    override val remaining: Int
        get() = TODO("Not yet implemented")
    override val bytesRead: Long
        get() = TODO("Not yet implemented")
    override val bytesWritten: Long
        get() = TODO("Not yet implemented")
    override val needsInput: Boolean
        get() = TODO("Not yet implemented")
    override val finished: Boolean
        get() = TODO("Not yet implemented")

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        TODO("Not yet implemented")
    }

    /**
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
     */
    private fun decodeDynamicTrees(source: BitSource): Pair<HuffmanTree, HuffmanTree> {
        val hlit = source.readBits(5).toInt() + 257
        val hdist = source.readBits(5).toInt() + 1
        val hclen = source.readBits(4).toInt() + 4
        val codeLengthLengths = IntArray(19)
        for (index in 0..<hclen) {
            codeLengthLengths[DeflateConstants.CODE_LENGTH_ORDER[index]] = source.readBits(3).toInt()
        }
        val lengthTree = HuffmanTree(codeLengthLengths)
        val lengthsCount = hlit + hdist
        val lengths = IntArray(lengthsCount)
        var index = 0
        while (index < lengthsCount) when (val symbol = lengthTree.decodeSymbol(source)) {
            // Handle direct code length
            in 0..15 -> lengths[index++] = symbol
            // Repeat previous code length
            DeflateConstants.SYM_REPEAT_PREVIOUS -> {
                val repeatCount = source.readBits(DeflateConstants.SYM_REPEAT_PREVIOUS_SIZE).toInt() + 3
                val previous = lengths[index - 1]
                repeat(repeatCount) {
                    lengths[index++] = previous
                }
            }
            // Repeat zero length
            DeflateConstants.SYM_REPEAT_ZERO_LENGTH -> {
                val repeatCount = source.readBits(DeflateConstants.SYM_REPEAT_ZERO_LENGTH_SIZE).toInt() + 3
                repeat(repeatCount) {
                    lengths[index++] = 0
                }
            }
            // Long zero length run
            DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN -> {
                val repeatCount = source.readBits(DeflateConstants.SYM_LONG_ZERO_LENGTH_RUN_SIZE).toInt() + 11
                repeat(repeatCount) {
                    lengths[index++] = 0
                }
            }
        }
        // Split into final trees
        return HuffmanTree(lengths.copyOfRange(0, hlit)) to HuffmanTree(lengths.copyOfRange(hlit, hlit + hdist))
    }

    override fun decompress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        TODO("Not yet implemented")
    }

    override fun finish() {
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}

/**
 * Creates a new decompressor using the specified compression level.
 * **Note that [Inflater] instances are NOT threadsafe!**
 *
 * @param raw If true, the ZLIB header and checksum fields will not be used
 *  in order to support the compression format used in both GZIP and PKZIP.
 * @return A new [Inflater] instance with the given parameters.
 */
expect fun Inflater(raw: Boolean = true): Inflater

/**
 * Returns a [RawSource] that reads DEFLATE-compressed bytes from this source
 * and emits their uncompressed form.
 *
 * This is a streaming wrapper: bytes are decompressed on the fly as you read
 * from the returned source. Close the returned source when finished to free
 * any underlying resources.
 *
 * @param raw If true (default), expects "deflate-raw" input without ZLIB
 *  header/footer. Set to false if the compressed input is ZLIB-wrapped and
 *  includes header and checksum fields.
 * @param bufferSize Size of the internal working buffers used during
 *  decompression.
 * @return A [RawSource] that produces decompressed data.
 */
fun RawSource.inflatingSource( // @formatter:off
    raw: Boolean = true,
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSource = decompressingSource(Inflater(raw), bufferSize) // @formatter:on

/**
 * Returns a [RawSink] that decompresses written bytes using DEFLATE and
 * writes them to this sink.
 *
 * This is a streaming wrapper: bytes are decompressed on the fly as you write
 * to the returned sink. Close the returned sink when finished to free
 * any underlying resources and ensure all data is flushed.
 *
 * @param raw If true (default), expects "deflate-raw" input without ZLIB
 *  header/footer. Set to false if the compressed input is ZLIB-wrapped and
 *  includes header and checksum fields.
 * @param bufferSize Size of the internal working buffers used during
 *  decompression.
 * @return A [RawSink] that accepts compressed data and writes decompressed data.
 */
fun RawSink.inflatingSink( // @formatter:off
    raw: Boolean = true,
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSink = decompressingSink(Inflater(raw), bufferSize) // @formatter:on