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
 * Base interface for any type of streaming compressor.
 */
interface Compressor : AutoCloseable {
    companion object {
        const val DEFAULT_BUFFER_SIZE: Int = 4096
    }

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
    fun compress(output: ByteArray): Int

    /**
     * Compresses the given data in one go using the given
     * compression level and buffer size.
     *
     * @param data The data to compress.
     * @param bufferSize The size of the intermediate buffer used during compression.
     * @return The compressed data.
     */
    fun compressBulk(data: ByteArray, bufferSize: Int = 4096): ByteArray {
        input = data
        finish()
        val buffer = Buffer()
        val chunkBuffer = ByteArray(bufferSize)
        while (!finished) {
            val bytesCompressed = compress(chunkBuffer)
            buffer.write(chunkBuffer, 0, bytesCompressed)
        }
        return buffer.readByteArray()
    }

    /**
     * When called, indicates that compression should end with the current
     * contents of the input buffer.
     */
    fun finish()
}