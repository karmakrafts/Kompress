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

package dev.karmakrafts.kompress.zip

import dev.karmakrafts.kompress.ExperimentalCompressionApi
import kotlinx.io.Sink
import kotlin.jvm.JvmInline

@ExperimentalCompressionApi
@JvmInline
value class ZipExtraFieldContainer(
    val delegate: MutableList<ZipExtraFieldEntry>
) : MutableList<ZipExtraFieldEntry> by delegate {
    companion object {
        fun empty(): ZipExtraFieldContainer = ZipExtraFieldContainer(mutableListOf())
    }

    inline val byteSize: Long get() = sumOf(ZipExtraFieldEntry::size)

    fun encode(sink: Sink) {
        forEach { entry -> entry.encode(sink) }
    }
}