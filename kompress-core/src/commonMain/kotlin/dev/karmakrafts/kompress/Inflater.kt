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

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/**
 * Streaming decompression interface that supports inflate and inflate-raw decompression.
 */
interface Inflater : AutoCloseable {
    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 4096

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
        fun inflate( // @formatter:off
            data: ByteArray,
            raw: Boolean = true,
            bufferSize: Int = DEFAULT_BUFFER_SIZE
        ): ByteArray = Inflater(raw).use { inflater -> // @formatter:on
            inflater.input = data
            val buffer = Buffer()
            val chunkBuffer = ByteArray(bufferSize)
            while (!inflater.finished) {
                val bytesDecompressed = inflater.inflate(chunkBuffer)
                buffer.write(chunkBuffer, 0, bytesDecompressed)
            }
            buffer.readByteArray()
        }
    }

    /**
     * The current input data chunk to be decompressed.
     * Should be updated whenever [needsInput] is true.
     */
    var input: ByteArray

    /**
     * True when the input buffer does not contain any more
     * data to decompress.
     */
    val needsInput: Boolean

    /**
     * True when the end of the decompressed data buffer has been reached.
     */
    val finished: Boolean

    /**
     * Uncompresses bytes into specified buffer.
     *
     * @param output The buffer to decompress the data into.
     * @return The actual number of decompressed bytes.
     */
    fun inflate(output: ByteArray): Int
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