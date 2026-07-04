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

/**
 * Mutable container for ZIP extra field entries.
 *
 * @property delegate Backing mutable list storing all extra field entries.
 */
@ExperimentalCompressionApi
@JvmInline
value class ZipExtraFieldContainer(
    val delegate: MutableList<ZipExtraFieldEntry>
) : MutableList<ZipExtraFieldEntry> by delegate {
    /**
     * Factory helpers for [ZipExtraFieldContainer].
     */
    companion object {
        /**
         * Creates an empty extra field container.
         *
         * @return New empty extra field container.
         */
        fun empty(): ZipExtraFieldContainer = ZipExtraFieldContainer(mutableListOf())
    }

    /**
     * Total encoded size of all contained extra field entries in bytes.
     */
    inline val byteSize: Long get() = sumOf(ZipExtraFieldEntry::size)

    /**
     * Encodes all contained extra field entries to [sink].
     *
     * @param sink Sink receiving the encoded extra field payload.
     */
    fun encode(sink: Sink) {
        forEach { entry -> entry.encode(sink) }
    }
}