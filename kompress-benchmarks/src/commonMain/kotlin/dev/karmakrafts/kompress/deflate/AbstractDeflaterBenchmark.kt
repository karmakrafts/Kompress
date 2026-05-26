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
    protected val random: Random = Random(Clock.System.now().epochSeconds)
    protected val deflater: Deflater = Deflater(level)

    @JvmName("run")
    @Benchmark
    fun run(): ByteArray {
        return deflater.compressBulk(random.nextBytes(128))
    }

    @TearDown
    fun tearDown() {
        deflater.close()
    }
}