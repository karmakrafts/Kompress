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
import kotlinx.io.RawSource

/**
 * Streaming decompression interface that supports inflate and inflate-raw decompression.
 */
interface Inflater : Decompressor, AutoCloseable {
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
        @Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("decompress"))
        fun inflate( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
        ): ByteArray = decompress(data, raw, bufferSize) // @formatter:on
    }

    /**
     * @see decompress
     */
    @Deprecated(message = "This API will be removed in 2.0", replaceWith = ReplaceWith("decompress"))
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
fun RawSource.inflating( // @formatter:off
    raw: Boolean = true,
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSource = decompressing(Inflater(raw), bufferSize) // @formatter:on