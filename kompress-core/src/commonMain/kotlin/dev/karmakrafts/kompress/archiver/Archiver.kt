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

package dev.karmakrafts.kompress.archiver

import dev.karmakrafts.kompress.Compressor
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink

/**
 * Base interface for all archiver implementations.
 * Provides access to the target [Sink], the [Compressor] used to compress entries,
 * and a function to append new entries to the archive.
 */
interface Archiver<E> : AutoCloseable {
    /**
     * The target sink being written to.
     */
    val sink: RawSink

    /**
     * The compressor used for compressing entry data blocks.
     */
    val compressor: Compressor

    /**
     * Append a new entry to the archive.
     *
     * @param entry The entry of type [E] (usually the header) to write.
     * @param callback An entry write callback which is invoked repeatedly
     *  until the returned Boolean value is false.
     *  This allows streaming compression for the current entry data.
     */
    fun appendEntry(entry: E, callback: (Sink) -> Boolean)
}

/**
 * Append a new entry to the archive using a [RawSource].
 *
 * @param entry The entry of type [E] to write.
 * @param source The source to read the entry data from.
 */
fun <E> Archiver<E>.appendEntry(entry: E, source: RawSource) {
    appendEntry(entry) { sink ->
        sink.transferFrom(source) > 0L
    }
}

/**
 * Append multiple entries to the archive.
 *
 * @param entries An [Iterable] of entry and source pairs to write.
 */
fun <E> Archiver<E>.appendEntries(entries: Iterable<Pair<E, RawSource>>) {
    for ((entry, source) in entries) {
        appendEntry(entry, source)
    }
}