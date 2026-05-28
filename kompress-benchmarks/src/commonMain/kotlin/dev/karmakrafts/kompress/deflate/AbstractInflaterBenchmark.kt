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

abstract class AbstractInflaterBenchmark(level: Int) {
    companion object {
        private const val DATA_SIZE: Int = 128
        private const val DATA_COUNT: Int = 100
    }

    protected val inflater: Inflater = Inflater()
    protected val random: Random = Random(Clock.System.now().epochSeconds)
    protected var dataIndex: Int = 0
    protected val data: Array<ByteArray> = Array(DATA_COUNT) {
        Deflater.compress(random.nextBytes(DATA_SIZE), level = level)
    }

    @JvmName("run")
    @Benchmark
    fun run(): ByteArray {
        return inflater.decompressBulk(data[dataIndex++ % DATA_COUNT])
    }

    @TearDown
    fun tearDown() {
        inflater.close()
    }
}