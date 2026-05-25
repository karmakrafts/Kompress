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

package dev.karmakrafts.kompress.deflate

import dev.karmakrafts.kompress.exception.DataFormatException
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
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_SYNC_FLUSH
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream
import kotlin.math.max
import kotlin.math.min

@Suppress("OVERRIDE_DEPRECATION")
@OptIn(ExperimentalForeignApi::class)
private class InflaterImpl( // @formatter:off
    private val raw: Boolean
) : Inflater { // @formatter:on
    private val stream: z_stream = nativeHeap.alloc<z_stream>().apply {
        check(
            inflateInit2(
                strm = ptr, windowBits = if (raw) -15 else 15
            ) == Z_OK
        ) { "Could not initialize Inflater" }
    }

    override var inputOffset: Int = 0
    override var inputSize: Int = 0

    override var remaining: Int = 0
        private set
    override var bytesRead: Long = 0L
        private set
    override var bytesWritten: Long = 0L
        private set

    private var pinnedInput: Pinned<ByteArray>? = null

    private var _input: ByteArray = ByteArray(0)
    override var input: ByteArray
        get() = _input
        set(value) {
            setInput(value)
        }

    override val needsInput: Boolean get() = stream.avail_in == 0u

    private var finishRequested: Boolean = false

    private var _finished: Boolean = false
    override val finished: Boolean get() = _finished

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

    override fun decompress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
        if (output.isEmpty()) return 0
        return output.usePinned { pinnedOutput ->
            if (_finished) return@usePinned 0

            stream.next_out = pinnedOutput.addressOf(offset).reinterpret()
            stream.avail_out = min(output.size, size).toUInt()

            val outBefore = stream.avail_out
            val inBefore = stream.avail_in
            val flush = if (finishRequested) (if (flush) Z_SYNC_FLUSH else Z_FINISH)
            else (if (flush) Z_SYNC_FLUSH else Z_NO_FLUSH)
            val result = inflate(stream.ptr, flush)
            val written = (outBefore - stream.avail_out).toInt()
            val read = (inBefore - stream.avail_in).toInt()
            remaining = max(0, remaining - read)
            bytesRead += read
            bytesWritten += written

            if (result == Z_STREAM_END) {
                _finished = true
            }
            else if (result != Z_OK && result != Z_BUF_ERROR) {
                throw DataFormatException("Inflater encountered invalid data: code 0x${result.toHexString()}")
            }

            written
        }
    }

    override fun close() {
        if (isClosed) return
        inflateEnd(stream.ptr)
        nativeHeap.free(stream)
        pinnedInput?.unpin()
        isClosed = true
    }

    override fun reset() {
        check(
            inflateInit2(
                strm = stream.ptr, windowBits = if (raw) -15 else 15
            ) == Z_OK
        ) { "Could not initialize Inflater" }
        setInput(ByteArray(0))
        finishRequested = false
        _finished = false
        bytesRead = 0L
        bytesWritten = 0L
    }
}

actual fun Inflater(raw: Boolean): Inflater = InflaterImpl(raw)