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

import kotlinx.io.Buffer
import kotlinx.io.RawSource

/**
 * A [RawSource] that calculates the CRC32 checksum of the data read from it.
 */
interface CRC32Source : RawSource {
    /**
     * The current CRC32 checksum of the data read from this source.
     */
    val checksum: UInt

    /**
     * Resets the current [checksum] to its initial value.
     */
    fun reset()
}

private class CRC32SourceImpl( // @formatter:off
    private val delegate: RawSource,
    private val isSourceOwned: Boolean
) : CRC32Source { // @formatter:on
    override val checksum: UInt
        get() = rawChecksum.inv()

    private var rawChecksum: UInt = CRC32_INITIAL_VALUE

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val read = delegate.readAtMostTo(sink, byteCount)
        if (read > 0L) {
            rawChecksum = sink.peek().crc32Round(read, rawChecksum)
        }
        return read
    }

    override fun reset() {
        rawChecksum = CRC32_INITIAL_VALUE
    }

    override fun close() {
        if (isSourceOwned) delegate.close()
    }
}

/**
 * Returns a [CRC32Source] that wraps this [RawSource].
 *
 * @param isSourceOwned whether the underlying source is owned by the [CRC32Source] and should be closed when it is closed.
 * @return a [CRC32Source] wrapping this [RawSource].
 */
fun RawSource.crc32Source(isSourceOwned: Boolean = true): CRC32Source = CRC32SourceImpl(this, isSourceOwned)