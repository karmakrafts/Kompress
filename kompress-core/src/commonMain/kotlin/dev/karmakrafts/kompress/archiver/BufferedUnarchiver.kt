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
import dev.karmakrafts.kompress.InternalKompressApi
import kotlinx.io.Buffer

/**
 * A buffered unarchiver loads the entire archive into memory,
 * so random access can be performed on it using state resetting.
 */
interface BufferedUnarchiver<E, D : Decompressor> : Unarchiver<E, D> {
    /**
     * Resets the unarchiver to its initial state, allowing for the
     * archive to be read again from the beginning.
     */
    fun reset()
}

private class BufferedUnarchiverImpl<E, D : Decompressor>( // @formatter:off
    private val delegate: Unarchiver<E, D>, 
    private val buffer: Buffer
) : BufferedUnarchiver<E, D>, Unarchiver<E, D> by delegate { // @formatter:on
    @OptIn(InternalKompressApi::class)
    override fun reset() {
        source = buffer.peek() // The source is a view of the buffer we can reset
    }
}

/**
 * Returns a [BufferedUnarchiver] for this [Unarchiver], which loads the
 * entire archive into memory. This allows for random access and re-reading
 * the archive from the beginning.
 *
 * If this [Unarchiver] is already a [BufferedUnarchiver], it is returned
 * as is.
 *
 * @param chunkSize The maximum size of each data chunk read from the source.
 * @return A [BufferedUnarchiver] containing the entire archive content.
 */
fun <E, D : Decompressor> Unarchiver<E, D>.buffered(
    chunkSize: Int = 4096
): BufferedUnarchiver<E, D> = when (this) {
    is BufferedUnarchiver<E, D> -> this
    else -> {
        val buffer = Buffer()
        var read = source.readAtMostTo(buffer, chunkSize.toLong())
        while (read != -1L) {
            read = source.readAtMostTo(buffer, chunkSize.toLong())
        }
        BufferedUnarchiverImpl(this, buffer).apply { reset() }
    }
}