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
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_SYNC_FLUSH
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.inflateReset
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
abstract class AbstractNativeInflaterBenchmark(level: Int) {
    private companion object {
        private const val DATA_SIZE: Int = 1024 * 1024 // 1MiB
        private const val RAW_WINDOW_BITS: Int = -15
    }

    protected val stream: z_stream = nativeHeap.alloc()
    protected val data: ByteArray = Deflater.compress(ByteArray(DATA_SIZE) { 1 }, level = level)
    protected val buffer: Buffer = Buffer()
    protected val chunkBuffer: ByteArray = ByteArray(4096)

    init {
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null
        val result = inflateInit2(
            stream.ptr, RAW_WINDOW_BITS
        )
        check(result == Z_OK) { "inflateInit2 failed with code $result" }
    }

    @Benchmark
    fun run(): ByteArray {
        val resetResult = inflateReset(stream.ptr)
        check(resetResult == Z_OK) { "inflateReset failed with code $resetResult" }

        data.usePinned { input ->
            stream.next_in = input.addressOf(0).reinterpret()
            stream.avail_in = data.size.convert()
            var result: Int = Z_OK
            do {
                chunkBuffer.usePinned { output ->
                    stream.next_out = output.addressOf(0).reinterpret()
                    stream.avail_out = chunkBuffer.size.convert()
                    result = inflate(stream.ptr, Z_SYNC_FLUSH)
                    check(result == Z_OK || result == Z_STREAM_END) { "inflate failed with code $result" }
                    val bytesDecompressed = chunkBuffer.size - stream.avail_out.toInt()
                    if (bytesDecompressed > 0) {
                        buffer.write(chunkBuffer, 0, bytesDecompressed)
                    }
                }
            }
            while (result != Z_STREAM_END)
        }
        return buffer.readByteArray()
    }

    @TearDown
    fun tearDown() {
        inflateEnd(stream.ptr)
        nativeHeap.free(stream)
    }
}