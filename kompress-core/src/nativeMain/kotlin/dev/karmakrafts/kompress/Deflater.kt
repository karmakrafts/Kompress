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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_SYNC_FLUSH
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.deflateParams
import platform.zlib.deflateReset
import platform.zlib.z_stream
import kotlin.math.max
import kotlin.math.min

@Suppress("OVERRIDE_DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
private class DeflaterImpl(raw: Boolean, initialLevel: Int) : Deflater {
    override var level: Int = initialLevel
        set(value) {
            check(deflateParams(stream.ptr, value, Z_DEFAULT_STRATEGY) == Z_OK) { "Could not adjust Deflater level" }
            field = value
        }

    override var inputOffset: Int = 0
    override var inputSize: Int = 0
    override var remaining: Int = 0
        private set

    private var pinnedInput: Pinned<ByteArray>? = null

    private var _input: ByteArray = ByteArray(0)
    override var input: ByteArray
        get() = _input
        set(value) {
            setInput(value)
        }

    private val stream: z_stream = nativeHeap.alloc<z_stream>().apply {
        check(
            deflateInit2(
                strm = ptr,
                level = level,
                method = Z_DEFLATED,
                windowBits = if (raw) -15 else 15,
                memLevel = 8,
                strategy = Z_DEFAULT_STRATEGY
            ) == Z_OK
        ) { "Could not initialize Deflater" }
    }

    override val needsInput: Boolean
        get() = stream.avail_in == 0u

    private var finishRequested: Boolean = false

    private var _finished: Boolean = false
    override val finished: Boolean
        get() = _finished

    private var isClosed: Boolean = false

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        inputOffset = offset
        inputSize = size
        pinnedInput?.unpin()
        if (data.isNotEmpty()) {
            pinnedInput = data.pin().apply {
                stream.next_in = addressOf(inputOffset).reinterpret()
                stream.avail_in = min(data.size, inputSize).toUInt()
            }
        }
        else {
            pinnedInput = null
            stream.next_in = null
            stream.avail_in = 0u
        }
        _input = data
        remaining = size
    }

    override fun finish() {
        finishRequested = true
    }

    override fun compress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        if (output.isEmpty()) return 0
        return output.usePinned { pinnedOutput ->
            if (_finished) return@usePinned 0

            stream.next_out = pinnedOutput.addressOf(offset).reinterpret()
            stream.avail_out = min(output.size, size).toUInt()

            val outBefore = stream.avail_out
            val inBefore = stream.avail_in
            val flush = if (finishRequested) (if (flush) Z_SYNC_FLUSH else Z_FINISH)
            else (if (flush) Z_SYNC_FLUSH else Z_NO_FLUSH)
            val result = deflate(stream.ptr, flush)
            remaining = max(0, remaining - (inBefore - stream.avail_in).toInt())
            val written = (outBefore - stream.avail_out).toInt()

            if (result == Z_STREAM_END) {
                _finished = true
                deflateEnd(stream.ptr)
            }
            else if (result != Z_OK) {
                if (written == 0) return@usePinned 0
            }

            written
        }
    }

    override fun close() {
        if (isClosed) return
        if (!_finished) deflateEnd(stream.ptr)
        nativeHeap.free(stream)
        pinnedInput?.unpin()
        isClosed = true
    }

    override fun reset() {
        check(deflateReset(stream.ptr) == Z_OK) { "Could not reset deflater" }
        input = ByteArray(0)
    }
}

actual fun Deflater(raw: Boolean, level: Int): Deflater = DeflaterImpl(raw, level)