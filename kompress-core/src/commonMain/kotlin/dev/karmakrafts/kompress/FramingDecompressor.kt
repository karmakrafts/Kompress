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

import dev.karmakrafts.kompress.exception.DataFormatException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/**
 * A [Decompressor] wrapper that consumes wrapper-specific bytes around compressed payload data.
 *
 * Subclasses can define how wrapper prologue/epilogue sections are consumed and observe produced payload bytes.
 *
 * @property decompressor The underlying decompressor for payload data.
 */
abstract class FramingDecompressor(
    val decompressor: Decompressor
) : Decompressor by decompressor {
    /**
     * Internal buffer containing wrapped input bytes pending processing.
     */
    protected val buffer: Buffer = Buffer()
    private var readPrologue: Boolean = false
    private var finishing: Boolean = false
    private var readEpilogue: Boolean = false
    private var wrapperBytesRead: Long = 0L

    private var wrappedInput: ByteArray = ByteArray(0)
    private var wrappedInputOffset: Int = 0
    private var wrappedInputSize: Int = 0

    /**
     * Consumes wrapper prologue bytes from [buffer].
     *
     * @return True when the full prologue was consumed, false if more bytes are required.
     */
    protected open fun consumePrologue(): Boolean = true

    /**
     * Called after payload bytes were written to [output].
     *
     * @param output The output buffer containing produced payload bytes.
     * @param offset The start offset in [output] of the produced bytes.
     * @param size The number of produced payload bytes.
     */
    protected open fun onDataWritten(output: ByteArray, offset: Int, size: Int) = Unit

    /**
     * Consumes wrapper epilogue bytes from [buffer].
     *
     * @return True when the full epilogue was consumed, false if more bytes are required.
     */
    protected open fun consumeEpilogue(): Boolean = true

    override val input: ByteArray
        get() = wrappedInput

    override val inputOffset: Int
        get() = wrappedInputOffset

    override val inputSize: Int
        get() = wrappedInputSize

    override val remaining: Int
        get() = (decompressor.remaining.toLong() + buffer.size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    override val bytesRead: Long
        get() = decompressor.bytesRead + wrapperBytesRead

    override val needsInput: Boolean
        get() {
            if (finished) return false
            if (!readPrologue || (decompressor.finished && !readEpilogue)) return true
            if (buffer.size > 0L) return false
            return decompressor.needsInput
        }

    override val finished: Boolean
        get() = decompressor.finished && readEpilogue

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        wrappedInput = data
        wrappedInputOffset = offset
        wrappedInputSize = size
        if (size > 0) {
            buffer.write(data, offset, offset + size)
        }
    }

    override fun decompress(
        output: ByteArray, offset: Int, size: Int, flush: Boolean
    ): Int {
        if (size <= 0 || finished) return 0

        if (!readPrologue) {
            if (!consumeWrapperSection(::consumePrologue)) {
                if (finishing) throw DataFormatException("Incomplete prologue")
                return 0
            }
            readPrologue = true
        }

        offerInputToDelegate()
        val written = decompressor.decompress(output, offset, size, flush)
        if (written > 0) {
            onDataWritten(output, offset, written)
        }

        if (decompressor.finished && !readEpilogue) {
            queueDelegateRemainingInput()
            if (!consumeWrapperSection(::consumeEpilogue)) {
                if (finishing) throw DataFormatException("Incomplete epilogue")
                return written
            }
            readEpilogue = true
        }

        return written
    }

    override fun finish() {
        finishing = true
        decompressor.finish()
    }

    override fun reset() {
        readPrologue = false
        finishing = false
        readEpilogue = false
        wrapperBytesRead = 0L
        wrappedInput = ByteArray(0)
        wrappedInputOffset = 0
        wrappedInputSize = 0
        buffer.clear()
        decompressor.reset()
    }

    override fun decompressBulk(data: ByteArray, bufferSize: Int): ByteArray {
        setInput(data)
        finish()
        val decompressedData = Buffer()
        val chunkBuffer = ByteArray(bufferSize)
        while (true) {
            val bytesDecompressed = decompress(chunkBuffer)
            if (bytesDecompressed == 0) break
            decompressedData.write(chunkBuffer, 0, bytesDecompressed)
        }
        return decompressedData.readByteArray()
    }

    private fun consumeWrapperSection(consumer: () -> Boolean): Boolean {
        val initialSize = buffer.size
        val consumed = consumer()
        val bytesConsumed = (initialSize - buffer.size).coerceAtLeast(0L)
        if (bytesConsumed > 0L) {
            wrapperBytesRead += bytesConsumed
        }
        return consumed
    }

    private fun offerInputToDelegate() {
        if (!decompressor.needsInput || decompressor.finished) return
        if (buffer.size == 0L) return
        val offeredSize = if (finishing) {
            buffer.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        else {
            buffer.size.coerceAtMost(Decompressor.DEFAULT_BUFFER_SIZE.toLong()).toInt()
        }
        decompressor.setInput(buffer.readByteArray(offeredSize))
    }

    private fun queueDelegateRemainingInput() {
        val remainingInput = decompressor.remaining
        if (remainingInput <= 0) return
        val readOffset = decompressor.inputOffset + (decompressor.inputSize - remainingInput).coerceAtLeast(0)
        buffer.write(decompressor.input, readOffset, readOffset + remainingInput)
        decompressor.setInput(ByteArray(0), 0, 0)
    }
}