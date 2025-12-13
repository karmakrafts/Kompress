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

import dev.karmakrafts.kompress.fflate.Deflate
import dev.karmakrafts.kompress.fflate.DeflateOptions
import dev.karmakrafts.kompress.fflate.Zlib
import dev.karmakrafts.kompress.fflate.ZlibOptions
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

private class DeflaterImpl( // @formatter:off
    private val raw: Boolean,
    initialLevel: Int
) : Deflater { // @formatter:on
    private var impl: FlateStreamWrapper = createImpl(initialLevel)
    private var onDataFinalSeen: Boolean = false
    private var finishRequested: Boolean = false
    private var inputPending: Boolean = false

    // Queue of compressed chunks waiting to be consumed by deflate(output)
    private val outQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var outOffset: Int = 0

    init {
        impl.ondata = ::onData
    }

    override var level: Int = initialLevel
        set(value) {
            if (field == value) return
            // Re-create deflate instance with new level. Changing level during compression is unsupported.
            impl = createImpl(value)
            // Reattach handler for the new instance
            onDataFinalSeen = false
            outQueue.clear()
            outOffset = 0
            impl.ondata = ::onData
            field = value
        }

    override var input: ByteArray = ByteArray(0)
        set(value) {
            inputPending = true
            field = value
        }

    override val needsInput: Boolean
        get() = !inputPending

    override val finished: Boolean
        get() = onDataFinalSeen && outQueue.isEmpty()

    private fun onData(data: Uint8Array, isFinal: Boolean) {
        if (data.length > 0) {
            val chunk = ByteArray(data.length)
            // NOTE: Direct index access on Uint8Array is not available in this target, so we use asDynamic() here.
            for (i in 0 until data.length) {
                chunk[i] = (data[i].toInt() and 0xFF).toByte()
            }
            outQueue.addLast(chunk)
        }
        if (isFinal) onDataFinalSeen = true
    }

    private fun createImpl(level: Int): FlateStreamWrapper = FlateStreamWrapper(
        if (raw) Deflate(DeflateOptions(level, 6), null)
        else Zlib(ZlibOptions(level, 6))
    )

    override fun deflate(output: ByteArray): Int {
        // Push any pending input/finalization into the underlying stream to generate output
        if ((inputPending || finishRequested) && !onDataFinalSeen) {
            val dataToPush: Uint8Array = if (inputPending && input.isNotEmpty()) {
                val data = Uint8Array(input.size)
                for (i in input.indices) {
                    data[i] = (input[i].toInt() and 0xFF).toByte()
                }
                data
            }
            else Uint8Array(0)
            impl.push(dataToPush, finishRequested)
            inputPending = false
        }

        if (outQueue.isEmpty()) return 0

        var written = 0
        var remaining = output.size
        while (remaining > 0 && outQueue.isNotEmpty()) {
            val head = outQueue.first()
            val available = head.size - outOffset
            val toCopy = minOf(available, remaining)
            if (toCopy > 0) {
                head.copyInto( // @formatter:off
                    destination = output,
                    destinationOffset = written,
                    startIndex = outOffset,
                    endIndex = outOffset + toCopy
                ) // @formatter:on
                written += toCopy
                remaining -= toCopy
                outOffset += toCopy
            }
            if (outOffset >= head.size) {
                outQueue.removeFirst()
                outOffset = 0
            }
        }
        return written
    }

    override fun finish() {
        // Signal that the next push should finalize the stream
        finishRequested = true
        // If no input is pending, we still need to flush trailer; the actual push happens on next deflate()
    }

    override fun close() {
        // Best-effort cleanup
        impl.ondata = null
        outQueue.clear()
        outOffset = 0
        inputPending = false
        finishRequested = false
        onDataFinalSeen = true
    }
}

actual fun Deflater(raw: Boolean, level: Int): Deflater = DeflaterImpl(raw, level)