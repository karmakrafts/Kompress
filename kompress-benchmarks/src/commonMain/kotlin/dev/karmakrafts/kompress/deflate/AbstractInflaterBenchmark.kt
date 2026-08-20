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
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.jvm.JvmName

abstract class AbstractInflaterBenchmark(protected val data: ByteArray) {
    companion object {
        private const val DATA_SIZE: Int = 1024 * 1024 // 1MiB
    }

    constructor(level: Int) : this(Deflater.compress(ByteArray(DATA_SIZE) { 1 }, level = level))

    protected val inflater: Inflater = Inflater()
    protected val buffer: Buffer = Buffer()
    protected val chunkBuffer: ByteArray = ByteArray(4096)

    @JvmName("run")
    @Benchmark
    fun run(): ByteArray {
        inflater.reset()
        inflater.setInput(data)
        inflater.finish()
        while (true) {
            val bytesDecompressed = inflater.decompress(chunkBuffer)
            if (bytesDecompressed == 0 || inflater.needsInput) break
            buffer.write(chunkBuffer, 0, bytesDecompressed)
        }
        return buffer.readByteArray()
    }

    @TearDown
    fun tearDown() {
        inflater.close()
    }
}