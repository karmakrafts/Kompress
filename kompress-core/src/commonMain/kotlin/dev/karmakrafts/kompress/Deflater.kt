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

import dev.karmakrafts.kompress.Deflater.Companion.compress
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
        @Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("compress"))
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
    @Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("compress"))
    fun deflate(output: ByteArray): Int = compress(output)
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
fun RawSource.deflating( // @formatter:off
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE
): RawSource = compressing(Deflater(raw, level), bufferSize)