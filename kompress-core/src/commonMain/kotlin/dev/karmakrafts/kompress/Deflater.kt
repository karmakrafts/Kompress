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

import dev.karmakrafts.karbide.BitSource
import dev.karmakrafts.kompress.Deflater.Companion.compress
import dev.karmakrafts.kompress.huffman.HuffmanTree
import dev.karmakrafts.kompress.lz77.LZ77
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
         * @throws DataFormatException when the compressor encounters invalid data.
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
@OptIn(InternalCompressionApi::class)
private class NewDeflaterImpl(
    private val raw: Boolean, level: Int
) : Deflater {
    companion object {
        /**
         * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
         */
        private val CODE_LENGTH_ORDER: IntArray = intArrayOf( // @formatter:off
            16, 17, 18, 0, 8,  7,
            9,  6,  10, 5, 11, 4,
            12, 3,  13, 2, 14, 1,
            15
        ) // @formatter:on
    }

    private val lz77: LZ77 = LZ77(level)

    override var level: Int = level
        set(value) {
            lz77.level = value
            field = value
        }

    override var input: ByteArray = ByteArray(0)

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

    /**
     * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) 3.2.7.
     */
    private fun decodeDynamicTrees(source: BitSource): Pair<HuffmanTree, HuffmanTree> {
        val hlit = source.readBits(5).toInt() + 257
        val hdist = source.readBits(5).toInt() + 1
        val hclen = source.readBits(4).toInt() + 4
        val codeLengthLengths = IntArray(19)
        for (index in 0..<hclen) {
            codeLengthLengths[CODE_LENGTH_ORDER[index]] = source.readBits(3).toInt()
        }
        val lengthTree = HuffmanTree(codeLengthLengths)
        val lengthsCount = hlit + hdist
        val lengths = IntArray(lengthsCount)
        var index = 0
        while (index < lengthsCount) when (val symbol = lengthTree.decode(source)) {
            // Handle direct code length
            in 0..15 -> lengths[index++] = symbol
            // Repeat previous code length
            16 -> {
                val repeatCount = source.readBits(2).toInt() + 3
                val previous = lengths[index - 1]
                repeat(repeatCount) {
                    lengths[index++] = previous
                }
            }
            // Repeat zero length
            17 -> {
                val repeatCount = source.readBits(3).toInt() + 3
                repeat(repeatCount) {
                    lengths[index++] = 0
                }
            }
            // Long zero length run
            18 -> {
                val repeatCount = source.readBits(7).toInt() + 11
                repeat(repeatCount) {
                    lengths[index++] = 0
                }
            }
        }
        // Split into final trees
        return HuffmanTree(lengths.copyOfRange(0, hlit)) to HuffmanTree(lengths.copyOfRange(hlit, hlit + hdist))
    }

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        TODO("Not yet implemented")
    }

    override fun compress( // @formatter:off
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

    override fun close() = Unit
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