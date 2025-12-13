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
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.math.min

private class DeflatingSource( // @formatter:off
    private val delegate: RawSource,
    raw: Boolean,
    level: Int,
    private val bufferSize: Int
) : RawSource { // @formatter:on
    private val deflater: Deflater = Deflater(raw, level)
    private val buffer: Buffer = Buffer()
    private val chunkBuffer: ByteArray = ByteArray(bufferSize)

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        var totalWritten = 0L
        var inputExhausted = false
        while (totalWritten < byteCount) {
            // If the deflater needs input, try to read more uncompressed data from the delegate.
            if (deflater.needsInput && !inputExhausted) {
                if (buffer.size == 0L && delegate.readAtMostTo(buffer, bufferSize.toLong()) == -1L) {
                    // No more input; finish the stream and drain remaining compressed bytes.
                    inputExhausted = true
                    deflater.finish()
                }
                if (!inputExhausted && buffer.size > 0L) {
                    val toProvide = min(buffer.size, bufferSize.toLong()).toInt()
                    val provided = buffer.readByteArray(toProvide)
                    deflater.input = provided
                }
            }
            // Compress into the output buffer, up to the requested byteCount.
            val remaining = (byteCount - totalWritten).toInt()
            val outLimit = if (remaining < bufferSize) remaining else bufferSize
            val outBuf = if (outLimit == bufferSize) chunkBuffer else ByteArray(outLimit)
            val written = deflater.deflate(outBuf)
            if (written > 0) {
                sink.write(outBuf, 0, written)
                totalWritten += written
                continue
            }
            if (deflater.finished) break
        }
        return if (totalWritten == 0L && deflater.finished) -1L else totalWritten
    }

    override fun close() = deflater.close()
}

fun RawSource.deflating( // @formatter:off
    raw: Boolean = true,
    level: Int = Deflater.DEFAULT_LEVEL,
    bufferSize: Int = Deflater.DEFAULT_BUFFER_SIZE
): RawSource = DeflatingSource(this, raw, level, bufferSize) // @formatter:on