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

import dev.karmakrafts.kompress.aliceInWonderlandData
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.TearDown
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.jvm.JvmName

abstract class AbstractDeflaterBenchmark(private val level: Int) {
    companion object {
        private const val DATA_SIZE: Int = 1024 * 1024 // 1MiB
    }

    protected val deflater: Deflater = Deflater(level)
    protected val data: ByteArray = ByteArray(DATA_SIZE) { 1 }
    protected val buffer: Buffer = Buffer()
    protected val chunkBuffer: ByteArray = ByteArray(4096)

    @JvmName("random")
    @Benchmark
    fun random(): ByteArray {
        deflater.reset()
        deflater.setInput(data)
        deflater.finish()
        while (true) {
            val bytesCompressed = deflater.compress(chunkBuffer)
            if (bytesCompressed == 0 || deflater.needsInput) break
            buffer.write(chunkBuffer, 0, bytesCompressed)
        }
        return buffer.readByteArray()
    }

    @JvmName("text")
    @Benchmark
    fun text(): ByteArray {
        deflater.reset()
        deflater.setInput(aliceInWonderlandData)
        deflater.finish()
        while (true) {
            val bytesCompressed = deflater.compress(chunkBuffer)
            if (bytesCompressed == 0 || deflater.needsInput) break
            buffer.write(chunkBuffer, 0, bytesCompressed)
        }
        return buffer.readByteArray()
    }

    @TearDown
    fun tearDown() {
        deflater.close()
    }
}