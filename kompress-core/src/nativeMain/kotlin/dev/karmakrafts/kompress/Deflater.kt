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
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.deflateParams
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
private class DeflaterImpl(raw: Boolean, initialLevel: Int) : Deflater {
    override var level: Int = initialLevel
        set(value) {
            check(deflateParams(stream.ptr, value, Z_DEFAULT_STRATEGY) == Z_OK) { "Could not adjust Deflater level" }
            field = value
        }

    private var pinnedInput: Pinned<ByteArray>? = null
    override var input: ByteArray = ByteArray(0)
        set(value) {
            pinnedInput?.unpin()
            if (value.isNotEmpty()) {
                pinnedInput = value.pin().apply {
                    stream.next_in = addressOf(0).reinterpret()
                    stream.avail_in = value.size.toUInt()
                }
            }
            else {
                pinnedInput = null
                stream.next_in = null
                stream.avail_in = 0u
            }
            field = value
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

    override fun finish() {
        finishRequested = true
    }

    override fun compress(output: ByteArray): Int {
        if (output.isEmpty()) return 0
        return output.usePinned { pinnedOutput ->
            if (_finished) return@usePinned 0

            stream.next_out = pinnedOutput.addressOf(0).reinterpret()
            stream.avail_out = output.size.toUInt()

            val before = stream.avail_out
            val flush = if (finishRequested) Z_FINISH else Z_NO_FLUSH
            val res = deflate(stream.ptr, flush)
            val after = stream.avail_out
            val written = (before - after).toInt()

            if (res == Z_STREAM_END) {
                _finished = true
            }
            else if (res != Z_OK) {
                if (written == 0) return@usePinned 0
            }

            written
        }
    }

    override fun close() {
        deflateEnd(stream.ptr)
        nativeHeap.free(stream)
        pinnedInput?.unpin()
    }
}

actual fun Deflater(raw: Boolean, level: Int): Deflater = DeflaterImpl(raw, level)