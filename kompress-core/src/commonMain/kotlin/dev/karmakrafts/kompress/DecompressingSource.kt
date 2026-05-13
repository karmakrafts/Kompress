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
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.math.min

private class DecompressingSource( // @formatter:off
    private val decompressor: Decompressor,
    private val delegate: RawSource,
    private val bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
) : RawSource { // @formatter:on
    private val buffer: Buffer = Buffer()
    private val chunkBuffer: ByteArray = ByteArray(bufferSize)

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        var totalWritten = 0L
        while (totalWritten < byteCount) {
            // If the inflater needs input, try to read more compressed data from the delegate.
            if (decompressor.needsInput) {
                if (buffer.size == 0L && delegate.readAtMostTo(buffer, bufferSize.toLong()) == -1L) {
                    // No more compressed input available.
                    decompressor.finish()
                }
                else {
                    // Provide a chunk of compressed data to the inflater.
                    val toProvide = min(buffer.size, bufferSize.toLong()).toInt()
                    val provided = buffer.readByteArray(toProvide)
                    decompressor.input = provided
                }
            }
            // Inflate into the output buffer, respecting the requested byteCount.
            val remaining = (byteCount - totalWritten).toInt()
            val outLimit = if (remaining < bufferSize) remaining else bufferSize
            val outBuf = if (outLimit == bufferSize) chunkBuffer else ByteArray(outLimit)
            val written = decompressor.decompress(outBuf)
            if (written > 0) {
                sink.write(outBuf, 0, written)
                totalWritten += written
                continue
            }
            if (decompressor.finished) break
        }
        return if (totalWritten == 0L && decompressor.finished) -1L else totalWritten
    }

    override fun close() = decompressor.close()
}

/**
 * Wraps this [RawSource] into a decompressing source using the given [decompressor].
 *
 * @param decompressor The decompressor to use.
 * @param bufferSize The size of the buffer used for decompression.
 * @return A decompressing [RawSource].
 */
fun RawSource.decompressing( // @formatter:off
    decompressor: Decompressor,
    bufferSize: Int = Decompressor.DEFAULT_BUFFER_SIZE
): RawSource = DecompressingSource(decompressor, this, bufferSize) // @formatter:on