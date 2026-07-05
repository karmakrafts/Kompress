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
 * A [Compressor] wrapper that injects wrapper-specific bytes around compressed payload data.
 *
 * Subclasses can provide wrapper prologue/epilogue data and inspect consumed payload bytes.
 *
 * @property compressor The underlying compressor that produces the wrapped payload.
 */
abstract class FramingCompressor(val compressor: Compressor) : Compressor by compressor {
    /**
     * Internal wrapper output buffer.
     */
    protected val buffer: Buffer = Buffer()
    private var wrotePrologue: Boolean = false
    private var finishing: Boolean = false
    private var wroteEpilogue: Boolean = false
    private var wrapperBytesWritten: Long = 0L

    /**
     * Appends wrapper prologue bytes to [buffer].
     */
    protected open fun appendPrologue() = Unit

    /**
     * Called after payload data has been consumed from [input].
     *
     * @param offset The start offset in [input] of consumed payload bytes.
     * @param size The number of consumed payload bytes.
     */
    protected open fun onDataRead(offset: Int, size: Int) = Unit

    /**
     * Appends wrapper epilogue bytes to [buffer].
     */
    protected open fun appendEpilogue() = Unit

    override val bytesWritten: Long
        get() = compressor.bytesWritten + wrapperBytesWritten

    override fun compress(
        output: ByteArray, offset: Int, size: Int, flush: Boolean
    ): Int {
        if (size <= 0) return 0
        if (!wrotePrologue) {
            appendPrologue()
            wrotePrologue = true
        }
        var written = buffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
        if (written > 0) {
            wrapperBytesWritten += written
            return written
        }

        val inputOffset = compressor.inputOffset
        val inputSize = compressor.inputSize
        val remaining = compressor.remaining
        written = compressor.compress(output, offset, size, flush)
        val consumed = remaining - compressor.remaining
        if (consumed > 0) {
            val readOffset = inputOffset + (inputSize - remaining).coerceAtLeast(0)
            onDataRead(readOffset, consumed)
        }
        if (written > 0) {
            return written
        }

        if (finishing && compressor.finished && !wroteEpilogue) {
            appendEpilogue()
            wroteEpilogue = true
        }
        written = buffer.readAtMostTo(output, offset, offset + size).coerceAtLeast(0)
        if (written > 0) {
            wrapperBytesWritten += written
        }
        return written
    }

    override fun finish() {
        finishing = true
        compressor.finish()
    }

    override fun reset() {
        wrotePrologue = false
        finishing = false
        wroteEpilogue = false
        wrapperBytesWritten = 0L
        buffer.clear()
        compressor.reset()
    }

    override fun compressBulk(data: ByteArray, bufferSize: Int): ByteArray {
        setInput(data)
        finish()
        val compressedData = Buffer()
        val chunkBuffer = ByteArray(bufferSize)
        while (true) {
            val bytesCompressed = compress(chunkBuffer)
            if (bytesCompressed == 0) break
            compressedData.write(chunkBuffer, 0, bytesCompressed)
        }
        return compressedData.readByteArray()
    }
}