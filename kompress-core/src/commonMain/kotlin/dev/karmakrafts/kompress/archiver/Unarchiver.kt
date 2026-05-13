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

import dev.karmakrafts.kompress.Decompressor
import kotlinx.io.RawSource
import kotlinx.io.Source

/**
 * Base interface for all unarchiver implementations.
 * Provides access to the source [RawSource], the [Decompressor] used to decompress entries,
 * and functions to read entries from the archive.
 */
interface Unarchiver<E> : AutoCloseable {
    /**
     * The source being read from.
     */
    val source: RawSource

    /**
     * The decompressor used for decompressing entry data blocks.
     */
    val decompressor: Decompressor

    /**
     * Get the next entry from the archive.
     *
     * @return The next entry of type [E] (usually the header) or null if no more entries are available.
     */
    fun nextEntry(): E?

    /**
     * Open a [Source] to read the data of the given [entry].
     *
     * @param entry The entry to open.
     * @return A [Source] to read the entry data.
     */
    fun openEntry(entry: E): Source
}

/**
 * Get a [Sequence] of all entries in the archive.
 *
 * @return A [Sequence] of all entries of type [E].
 */
fun <E> Unarchiver<E>.entries(): Sequence<E> = sequence {
    var entry = nextEntry()
    while (entry != null) {
        yield(entry)
        entry = nextEntry()
    }
}