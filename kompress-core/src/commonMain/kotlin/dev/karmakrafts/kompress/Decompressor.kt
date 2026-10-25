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
public interface Decompressor : AutoCloseable {
    public companion object {
        public const val DEFAULT_BUFFER_SIZE: Int = 4096
    }

    /**
     * The current input buffer.
     */
    public val input: ByteArray

    /**
     * The offset into the current input buffer in bytes.
     */
    public val inputOffset: Int

    /**
     * The size of the current input data in bytes.
     */
    public val inputSize: Int

    /**
     * The number of bytes not consumed by the decompressor
     * remaining in the current input buffer.
     */
    public val remaining: Int

    /**
     * The total number of bytes read in by this compressor instance since its last reset.
     */
    public val bytesRead: Long

    /**
     * The total number of bytes written out by this compressor instance since its last reset.
     */
    public val bytesWritten: Long

    /**
     * True when the input buffer does not contain any more
     * data to decompress.
     */
    public val needsInput: Boolean

    /**
     * True when the end of the decompressed data buffer has been reached.
     */
    public val finished: Boolean

    /**
     * Update the current input buffer, offset and size of the input data.
     *
     * @param data The new input buffer to read from.
     * @param offset The offset into the given buffer to start reading from in bytes.
     * @param size The number of bytes to read from the given buffer.
     */
    public fun setInput( // @formatter:off
        data: ByteArray,
        offset: Int = 0,
        size: Int = data.size
    ) // @formatter:on

    /**
     * Uncompresses bytes into specified buffer.
     *
     * @param output The buffer to decompress the data into.
     * @return The actual number of decompressed bytes.
     * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
     */
    public fun decompress( // @formatter:off
        output: ByteArray,
        offset: Int = 0,
        size: Int = output.size,
        flush: Boolean = false
    ): Int // @formatter:on

    /**
     * Decompresses the given data in one go using the given
     * buffer size.
     *
     * @param data The data to compress.
     * @param bufferSize The size of the intermediate buffer used during compression.
     * @return The decompressed data.
     * @throws dev.karmakrafts.kompress.exception.DataFormatException when the decompressor encounters invalid data.
     */
    public fun decompressBulk(data: ByteArray, bufferSize: Int = 4096): ByteArray {
        setInput(data)
        finish()
        val buffer = Buffer()
        val chunkBuffer = ByteArray(bufferSize)
        while (true) {
            val bytesDecompressed = decompress(chunkBuffer)
            if (bytesDecompressed == 0) break
            buffer.write(chunkBuffer, 0, bytesDecompressed)
        }
        return buffer.readByteArray()
    }

    /**
     * When called, indicates that decompression should end with the current
     * contents of the input buffer.
     */
    public fun finish()

    /**
     * Reset the internal state of this decompressor so it can be reused
     * for a new decompression cycle.
     */
    public fun reset()
}