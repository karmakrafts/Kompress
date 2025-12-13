/*
 * Copyright (C) 2025 Karma Krafts & associates
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

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/**
 * Streaming compression interface that supports deflate and deflate-raw compression.
 */
interface Deflater : AutoCloseable {
    companion object {
        const val DEFAULT_LEVEL: Int = 6
        const val DEFAULT_BUFFER_SIZE: Int = 4096

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
        fun deflate( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            level: Int = DEFAULT_LEVEL,
            bufferSize: Int = DEFAULT_BUFFER_SIZE
        ): ByteArray = Deflater(raw, level).use { deflater -> // @formatter:on
            deflater.input = data
            deflater.finish() // We only handle a single input chunk in this case
            val buffer = Buffer()
            val chunkBuffer = ByteArray(bufferSize)
            while (!deflater.finished) {
                val bytesCompressed = deflater.deflate(chunkBuffer)
                buffer.write(chunkBuffer, 0, bytesCompressed)
            }
            buffer.readByteArray()
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

    /**
     * The current input data chunk to be compressed.
     * Should be updated whenever [needsInput] is true.
     */
    var input: ByteArray

    /**
     * True when the input buffer does not contain any more
     * data to compress.
     */
    val needsInput: Boolean

    /**
     * True when the end of the compressed data buffer has been reached.
     */
    val finished: Boolean

    /**
     * Compresses the input data and fills specified buffer with compressed data.
     * Returns actual number of bytes of compressed data.
     * A return value of 0 indicates that needsInput should be called in order
     * to determine if more input data is required.
     *
     * @param output The buffer to compress the data into.
     * @return The actual number of compressed bytes.
     */
    fun deflate(output: ByteArray): Int

    /**
     * When called, indicates that compression should end with the current
     * contents of the input buffer.
     */
    fun finish()
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