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

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import java.util.zip.CRC32
import kotlin.random.Random
import kotlin.time.Clock

@State(Scope.Benchmark)
open class JvmCRC32Benchmark {
    private val crc32: CRC32 = CRC32()
    private val random: Random = Random(Clock.System.now().epochSeconds)

    @JvmName("run")
    @Benchmark
    fun runBenchmark(): Long {
        repeat(100) {
            val data = random.nextBytes(128)
            crc32.update(data)
        }
        return crc32.value
    }
}