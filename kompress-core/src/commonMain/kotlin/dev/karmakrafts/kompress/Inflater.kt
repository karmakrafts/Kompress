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

import dev.karmakrafts.kompress.Inflater.Companion.decompress
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Source
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

        // TODO: document this
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

        // TODO: document this
        fun computeCompressedSize( // @formatter:off
            source: Source,
            raw: Boolean = true,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): Long = Inflater(raw).use { inflater -> // @formatter:on
            val outputBuffer = ByteArray(bufferSize)
            var totalRead = 0L
            while (!inflater.finished) {
                if (inflater.needsInput) {
                    val buffer = Buffer()
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

    /**
     * @see decompress
     */
    @Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("decompress(output)"))
    fun inflate(output: ByteArray): Int = decompress(output)
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
 * @see inflatingSource
 */
@Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("inflatingWith(raw, bufferSize)"))
fun RawSource.inflating( // @formatter:off
    raw: Boolean = true,
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSource = inflatingSource(raw, bufferSize) // @formatter:on

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