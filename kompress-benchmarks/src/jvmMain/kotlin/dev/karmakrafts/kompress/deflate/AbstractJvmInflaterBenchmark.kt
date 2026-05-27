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
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import java.util.zip.Inflater
import kotlin.random.Random
import kotlin.time.Clock

abstract class AbstractJvmInflaterBenchmark(level: Int) {
    private val inflater: Inflater = Inflater(true)
    private val random: Random = Random(Clock.System.now().epochSeconds)
    private val data: ByteArray = Deflater.compress(random.nextBytes(128), level = level)

    @JvmName("run")
    @Benchmark
    fun run(): ByteArray {
        // Same code as in decompressBulk()
        inflater.setInput(data)
        val buffer = Buffer()
        val chunkBuffer = ByteArray(4096)
        while (true) {
            val bytesDecompressed = inflater.inflate(chunkBuffer)
            if (bytesDecompressed == 0) break
            buffer.write(chunkBuffer, 0, bytesDecompressed)
        }
        return buffer.readByteArray()
    }
}