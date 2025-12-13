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

import dev.karmakrafts.kompress.fflate.Inflate
import dev.karmakrafts.kompress.fflate.InflateOptions
import dev.karmakrafts.kompress.fflate.Unzlib
import dev.karmakrafts.kompress.fflate.UnzlibOptions
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.toUByteArray
import org.khronos.webgl.toUint8Array
import kotlin.math.min

@OptIn(ExperimentalUnsignedTypes::class)
private class InflaterImpl(raw: Boolean) : Inflater {
    private var impl: FlateStreamWrapper = FlateStreamWrapper(
        if (raw) Inflate(InflateOptions(null, null)) else Unzlib(UnzlibOptions(null, null))
    ).apply {
        ondata = ::onData
    }

    private var inputPending: Boolean = false
    private var finalSeen: Boolean = false
    private var finalPushed: Boolean = false
    private val outQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var outOffset: Int = 0
    private val emptyUint8Array: Uint8Array = Uint8Array(0)

    override var input: ByteArray = ByteArray(0)
        set(value) {
            field = value
            inputPending = true
        }

    override val needsInput: Boolean
        get() = !inputPending

    override val finished: Boolean
        get() = finalSeen && outQueue.isEmpty()

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun inflate(output: ByteArray): Int {
        if (output.isEmpty()) return 0
        if (inputPending && !finalSeen) {
            val dataToPush = if (input.isNotEmpty()) input.asUByteArray().toUint8Array() else emptyUint8Array
            impl.push(dataToPush, false)
            inputPending = false
        }
        else if (!finalSeen && !finalPushed && outQueue.isEmpty()) {
            impl.push(emptyUint8Array, true)
            finalPushed = true
        }

        if (outQueue.isEmpty()) return 0

        var written = 0
        var remaining = output.size
        while (remaining > 0 && outQueue.isNotEmpty()) {
            val head = outQueue.first()
            val available = head.size - outOffset
            val toCopy = min(available, remaining)
            if (toCopy > 0) {
                head.copyInto(
                    destination = output,
                    destinationOffset = written,
                    startIndex = outOffset,
                    endIndex = outOffset + toCopy
                )
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
        input = ByteArray(0)
    }

    private fun onData(data: Uint8Array, isFinal: Boolean) {
        if (data.length > 0) outQueue.addLast(data.toUByteArray().asByteArray())
        if (isFinal) finalSeen = true
    }
}

actual fun Inflater(raw: Boolean): Inflater = InflaterImpl(raw)