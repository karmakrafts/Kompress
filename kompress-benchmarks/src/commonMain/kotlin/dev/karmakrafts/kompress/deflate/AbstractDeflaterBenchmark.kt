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
import kotlin.jvm.JvmName
import kotlin.random.Random
import kotlin.time.Clock

abstract class AbstractDeflaterBenchmark(private val level: Int) {
    companion object {
        private const val DATA_SIZE: Int = 128
        private const val DATA_COUNT: Int = 100
    }

    protected val random: Random = Random(Clock.System.now().epochSeconds)
    protected val deflater: Deflater = Deflater(level)
    protected val data: Array<ByteArray> = Array(DATA_COUNT) { random.nextBytes(DATA_SIZE) }
    protected var dataIndex: Int = 0

    @JvmName("run")
    @Benchmark
    fun run(): ByteArray {
        return deflater.compressBulk(data[dataIndex++ % DATA_COUNT])
    }

    @TearDown
    fun tearDown() {
        deflater.close()
    }
}