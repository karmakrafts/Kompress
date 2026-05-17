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

package dev.karmakrafts.kompress.archive

import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.InvalidChecksumException
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * @param entry The current entry.
 * @param source A source to read the current entry data from.
 *  This source **MUST** be consumed, either by copying data or by skipping.
 * @param fetchMore A callback which allows requesting more data from
 *  the unarchivers underlying source if needed.
 *  Returns true if more data was available, false otherwise.
 */
typealias UnarchiverEntryCallback<E> = ( // @formatter:off
    entry: E,
    source: Buffer,
    fetchMore: () -> Boolean
) -> Unit // @formatter:on

/**
 * Base interface for all unarchiver implementations.
 * Provides access to the source [Source], the [Decompressor] used to decompress entries,
 * and functions to read entries from the archive.
 */
interface Unarchiver<E, D : Decompressor> : AutoCloseable {
    /**
     * The source being read from.
     */
    val source: Source

    /**
     * The decompressor used for decompressing entry data blocks.
     */
    val decompressor: D

    /**
     * Iterates over all entries in the current archive in a streaming
     * fashion. The given source is only to be used within the closure,
     * since it is backed by the current internal buffer state.
     *
     * @param callback The callback to invoke for each entry.
     * @throws InvalidChecksumException when a checksum validation fails for the current entry.
     */
    fun forEachEntry(callback: UnarchiverEntryCallback<E>)
}

/**
 * Extracts all entries from this unarchiver matching the
 * given predicate into memory and retains a copy of them
 * in form of a [kotlinx.io.Buffer].
 *
 * **Use with care, as this operation can be very expensive!**
 *
 * @param chunkSize The maximum size of each transferred data chunk in bytes.
 * @param filter A filter to match all entries in this archive against.
 * @return A new list containing all extracted entries which matched the given predicate.
 * @throws InvalidChecksumException when a checksum validation fails for the current entry.
 */
inline fun <E, D : Decompressor> Unarchiver<E, D>.extract( // @formatter:off
    chunkSize: Int = 4096,
    crossinline filter: (E, Buffer) -> Boolean = { _, _ -> true }
): List<Pair<E, Buffer>> { // @formatter:on
    val entries = ArrayList<Pair<E, Buffer>>()
    forEachEntry { entry, source, fetchMore ->
        if (!filter(entry, source)) return@forEachEntry
        val ownedBuffer = Buffer()
        fetchMore()
        var read = source.readAtMostTo(ownedBuffer, chunkSize.toLong())
        while (read != -1L || fetchMore()) {
            read = source.readAtMostTo(ownedBuffer, chunkSize.toLong())
        }
        entries += entry to ownedBuffer
    }
    return entries
}