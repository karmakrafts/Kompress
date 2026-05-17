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

import dev.karmakrafts.kompress.fflate.FlateStream
import dev.karmakrafts.kompress.fflate.Inflate
import dev.karmakrafts.kompress.fflate.InflateOptions
import dev.karmakrafts.kompress.fflate.Unzlib
import dev.karmakrafts.kompress.fflate.UnzlibOptions
import js.buffer.ArrayBufferLike
import js.typedarrays.Uint8Array
import js.typedarrays.toUByteArray
import js.typedarrays.toUint8Array
import kotlin.math.min

@Suppress("OVERRIDE_DEPRECATION")
@OptIn(ExperimentalUnsignedTypes::class)
private class InflaterImpl(raw: Boolean) : Inflater {
    private var impl: FlateStream =
        (if (raw) Inflate(InflateOptions(null, null)) else Unzlib(UnzlibOptions(null, null))).apply {
            ondata = ::onData
        }

    private var inputPending: Boolean = false
    private var finishRequested: Boolean = false
    private var finalSeen: Boolean = false
    private var finalPushed: Boolean = false
    private val outQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var outOffset: Int = 0
    private val emptyUint8Array: Uint8Array<ArrayBufferLike> = Uint8Array(0)

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

    override val needsInput: Boolean
        get() = !inputPending && outQueue.isEmpty()

    override val finished: Boolean
        get() = finalSeen && outQueue.isEmpty()

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        _input = data
        inputOffset = offset
        inputSize = size
        inputPending = true
        remaining = size
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun decompress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
        if (output.isEmpty() || size <= 0) return 0
        if (inputPending && !finalSeen) {
            val dataToPush = if (input.isNotEmpty() && inputSize > 0) {
                input.asUByteArray().toUint8Array().subarray(inputOffset, inputOffset + inputSize)
            }
            else {
                emptyUint8Array
            }
            impl.push(dataToPush, false)
            inputPending = false
        }
        else if (!finalSeen && (finishRequested || flush) && !finalPushed && outQueue.isEmpty()) {
            impl.push(emptyUint8Array, finishRequested)
            if (finishRequested) {
                finalPushed = true
            }
        }

        if (outQueue.isEmpty()) return 0

        var written = 0
        var remainingOut = size
        var currentOutputOffset = offset
        while (remainingOut > 0 && outQueue.isNotEmpty()) {
            val head = outQueue.first()
            val available = head.size - outOffset
            val toCopy = min(available, remainingOut)
            if (toCopy > 0) {
                head.copyInto(
                    destination = output,
                    destinationOffset = currentOutputOffset,
                    startIndex = outOffset,
                    endIndex = outOffset + toCopy
                )
                written += toCopy
                remainingOut -= toCopy
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
        finishRequested = true
    }

    override fun close() {
        if (!finalSeen && !finalPushed) {
            impl.push(emptyUint8Array, true)
            finalPushed = true
        }
        impl.ondata = null
        outQueue.clear()
        outOffset = 0
        inputPending = false
        finalSeen = true
        inputOffset = 0
        inputSize = 0
        _input = ByteArray(0)
    }

    override fun reset() {
        _input = ByteArray(0)
        inputOffset = 0
        inputSize = 0
        inputPending = false
        finishRequested = false
        finalSeen = false
        finalPushed = false
        outQueue.clear()
        outOffset = 0
    }

    private fun onData(data: Uint8Array<*>, isFinal: Boolean) {
        if (data.length > 0) outQueue.addLast(data.toUByteArray().asByteArray())
        if (isFinal) finalSeen = true
    }
}

actual fun Inflater(raw: Boolean): Inflater = InflaterImpl(raw)