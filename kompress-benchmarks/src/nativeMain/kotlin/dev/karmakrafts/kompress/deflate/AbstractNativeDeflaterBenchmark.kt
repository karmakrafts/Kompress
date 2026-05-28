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

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.TearDown
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.deflateReset
import platform.zlib.z_stream
import kotlin.random.Random
import kotlin.time.Clock

@OptIn(ExperimentalForeignApi::class)
abstract class AbstractNativeDeflaterBenchmark(level: Int) {
    private companion object {
        private const val DATA_SIZE: Int = 128
        private const val DATA_COUNT: Int = 100
        private const val RAW_WINDOW_BITS: Int = -15
        private const val DEFAULT_MEMORY_LEVEL: Int = 8
    }

    protected val random: Random = Random(Clock.System.now().epochSeconds)
    protected var dataIndex: Int = 0
    protected val data: Array<ByteArray> = Array(DATA_COUNT) { random.nextBytes(DATA_SIZE) }
    protected val stream: z_stream = nativeHeap.alloc()

    init {
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null
        val result = deflateInit2(
            stream.ptr, level, Z_DEFLATED, RAW_WINDOW_BITS, DEFAULT_MEMORY_LEVEL, Z_DEFAULT_STRATEGY
        )
        check(result == Z_OK) { "deflateInit2 failed with code $result" }
    }

    @Benchmark
    fun run(): ByteArray {
        // Same code as in compressBulk()
        val resetResult = deflateReset(stream.ptr)
        check(resetResult == Z_OK) { "deflateReset failed with code $resetResult" }
        val buffer = Buffer()
        val chunkBuffer = ByteArray(4096)

        data[dataIndex++ % DATA_COUNT].usePinned { input ->
            stream.next_in = input.addressOf(0).reinterpret()
            stream.avail_in = data.size.convert()
            var result: Int = Z_OK
            do {
                chunkBuffer.usePinned { output ->
                    stream.next_out = output.addressOf(0).reinterpret()
                    stream.avail_out = chunkBuffer.size.convert()
                    result = deflate(stream.ptr, Z_FINISH)
                    check(result == Z_OK || result == Z_STREAM_END) { "deflate failed with code $result" }
                    val bytesCompressed = chunkBuffer.size - stream.avail_out.toInt()
                    if (bytesCompressed > 0) {
                        buffer.write(chunkBuffer, 0, bytesCompressed)
                    }
                }
            }
            while (result != Z_STREAM_END)
        }
        return buffer.readByteArray()
    }

    @TearDown
    fun tearDown() {
        deflateEnd(stream.ptr)
        nativeHeap.free(stream)
    }
}