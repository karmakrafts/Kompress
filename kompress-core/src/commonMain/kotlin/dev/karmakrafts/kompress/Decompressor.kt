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
 * Base interface for any type of streaming decompressor.
 */
interface Decompressor : AutoCloseable {
    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 4096
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
    fun decompress(output: ByteArray): Int

    /**
     * Decompresses the given data in one go using the given
     * buffer size.
     *
     * @param data The data to compress.
     * @param bufferSize The size of the intermediate buffer used during compression.
     * @return The decompressed data.
     */
    fun decompressBulk(data: ByteArray, bufferSize: Int = 4096): ByteArray {
        input = data
        finish()
        val buffer = Buffer()
        val chunkBuffer = ByteArray(bufferSize)
        while (!finished) {
            val bytesDecompressed = decompress(chunkBuffer)
            buffer.write(chunkBuffer, 0, bytesDecompressed)
        }
        return buffer.readByteArray()
    }

    /**
     * When called, indicates that decompression should end with the current
     * contents of the input buffer.
     */
    fun finish()
}