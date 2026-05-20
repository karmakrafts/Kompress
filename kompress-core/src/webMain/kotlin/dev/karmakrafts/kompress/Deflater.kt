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

import dev.karmakrafts.kompress.fflate.Deflate
import dev.karmakrafts.kompress.fflate.DeflateOptions
import dev.karmakrafts.kompress.fflate.FlateStream
import dev.karmakrafts.kompress.fflate.Zlib
import dev.karmakrafts.kompress.fflate.ZlibOptions
import js.buffer.ArrayBufferLike
import js.typedarrays.Uint8Array
import js.typedarrays.toUByteArray
import js.typedarrays.toUint8Array
import kotlin.math.min

@Suppress("OVERRIDE_DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
private class DeflaterImpl( // @formatter:off
    private val raw: Boolean,
    initialLevel: Int
) : Deflater { // @formatter:on
    private var impl: FlateStream = createImpl(initialLevel)
    private var finalRequested: Boolean = false
    private var finalSeen: Boolean = false
    private var inputPending: Boolean = false
    private val outQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var outOffset: Int = 0
    private val emptyUint8Array: Uint8Array<ArrayBufferLike> = Uint8Array(0)

    override var level: Int = initialLevel
        set(value) {
            field = value
            impl = createImpl(value)
        }

    override var inputOffset: Int = 0
    override var inputSize: Int = 0
    override var remaining: Int = 0
        private set

    private var _input: ByteArray = ByteArray(0)
    override var input: ByteArray
        get() = _input
        set(value) {
            setInput(value)
        }

    override val needsInput: Boolean get() = !inputPending
    override val finished: Boolean get() = finalSeen && outQueue.isEmpty()
    override val bytesRead: Long = 0L // TODO: implement this
    override val bytesWritten: Long = 0L // TODO: implement this

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        inputPending = true
        _input = data
        inputOffset = offset
        inputSize = size
        remaining = size
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun compress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
        if (output.isEmpty() || size <= 0) return 0
        if (inputPending && !finalSeen) {
            val dataToPush = if (input.isNotEmpty() && inputSize > 0) {
                input.asUByteArray().toUint8Array().subarray(inputOffset, inputOffset + inputSize)
            }
            else {
                emptyUint8Array
            }
            impl.push(dataToPush, finalRequested)
            inputPending = false
        }

        if (!inputPending && finalRequested && !finalSeen) {
            impl.push(emptyUint8Array, true)
        }

        if (outQueue.isEmpty()) return 0

        var written = 0
        var remaining = size
        var currentOutputOffset = offset
        while (remaining > 0 && outQueue.isNotEmpty()) {
            val head = outQueue.first()
            val available = head.size - outOffset
            val toCopy = min(available, remaining)
            if (toCopy > 0) {
                head.copyInto(
                    destination = output,
                    destinationOffset = currentOutputOffset,
                    startIndex = outOffset,
                    endIndex = outOffset + toCopy
                )
                written += toCopy
                remaining -= toCopy
                outOffset += toCopy
                currentOutputOffset += toCopy
            }
            if (outOffset >= head.size) {
                outQueue.removeFirst()
                outOffset = 0
            }
        }
        return written
    }

    override fun finish() {
        finalRequested = true
    }

    override fun close() {
        impl.ondata = null
        outQueue.clear()
        outOffset = 0
        inputPending = false
        finalRequested = true
        finalSeen = true
        input = ByteArray(0)
    }

    override fun reset() {
        impl = createImpl(level)
        outQueue.clear()
        outOffset = 0
        inputPending = false
        finalRequested = false
        finalSeen = false
        inputOffset = 0
        inputSize = 0
        _input = ByteArray(0)
    }

    private fun createImpl(level: Int): FlateStream {
        return (if (raw) Deflate(DeflateOptions(level, 6), null)
        else Zlib(ZlibOptions(level, 6))).apply {
            ondata = ::onData
        }
    }

    private fun onData(data: Uint8Array<*>, isFinal: Boolean) {
        if (data.length > 0) outQueue.addLast(data.toUByteArray().asByteArray())
        if (isFinal) finalSeen = true
    }
}

actual fun Deflater(raw: Boolean, level: Int): Deflater = DeflaterImpl(raw, level)