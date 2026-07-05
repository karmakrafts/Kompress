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
import kotlinx.io.RawSink
import kotlinx.io.readByteArray
import kotlin.math.min

private class CompressingSink( // @formatter:off
    private val compressor: Compressor,
    private val delegate: RawSink,
    private val bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE,
    private val isSinkOwned: Boolean = true,
    private val isCompressorOwned: Boolean = true
) : RawSink { // @formatter:on
    private val chunkBuffer: ByteArray = ByteArray(bufferSize)
    private val drainBuffer: Buffer = Buffer()
    private var isClosed: Boolean = false

    override fun write(source: Buffer, byteCount: Long) {
        if (byteCount == 0L) return
        var remaining = byteCount
        while (remaining > 0L) {
            if (compressor.needsInput) {
                val toRead = min(remaining, bufferSize.toLong()).toInt()
                val data = source.readByteArray(toRead)
                compressor.setInput(data)
                remaining -= toRead
            }
            drain()
        }
    }

    private fun drain() {
        while (true) {
            val written = compressor.compress(chunkBuffer)
            if (written > 0) {
                drainBuffer.write(chunkBuffer, 0, written)
                delegate.write(drainBuffer, written.toLong())
            }
            else break
        }
    }

    override fun flush() {
        drain()
        delegate.flush()
    }

    override fun close() {
        if (isClosed) return
        compressor.finish()
        while (true) {
            val written = compressor.compress(chunkBuffer)
            if (written > 0) {
                drainBuffer.write(chunkBuffer, 0, written)
                delegate.write(drainBuffer, written.toLong())
            }
            else if (compressor.finished || compressor.needsInput) break
        }
        delegate.flush()
        if (isCompressorOwned) compressor.close()
        if (isSinkOwned) delegate.close()
        isClosed = true
    }
}

/**
 * Wraps this [RawSink] into a compressing sink using the given [compressor].
 *
 * @param compressor The compressor to use.
 * @param bufferSize The size of the buffer used for compression.
 * @param isSinkOwned If the given sink is owned by the wrapper instance.
 *  This will automatically close the wrapped sink when the wrapper is closed.
 * @param isCompressorOwned If the given compressor is owned by the wrapper instance.
 *  This will automatically close the wrapped compressor when the wrapper is closed.
 * @return A compressing [RawSink].
 */
fun RawSink.compressingSink( // @formatter:off
    compressor: Compressor,
    bufferSize: Int = Compressor.DEFAULT_BUFFER_SIZE,
    isSinkOwned: Boolean = true,
    isCompressorOwned: Boolean = true
): RawSink = CompressingSink(compressor, this, bufferSize, isSinkOwned, isCompressorOwned) // @formatter:on
