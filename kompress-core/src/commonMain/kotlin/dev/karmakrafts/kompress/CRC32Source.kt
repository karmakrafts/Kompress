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

// TODO: document this
interface CRC32Source : RawSource {
    // TODO: document this
    val checksum: UInt

    // TODO: document this
    fun reset()
}

private class CRC32SourceImpl( // @formatter:off
    private val delegate: RawSource,
    private val isSourceOwned: Boolean
) : CRC32Source { // @formatter:on
    override var checksum: UInt = CRC32_INITIAL_VALUE
        private set

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        val read = delegate.readAtMostTo(sink, byteCount)
        if (read != 0L && sink.size > 0L) {
            checksum = sink.peek().crc32(read, checksum)
        }
        return read
    }

    override fun reset() {
        checksum = CRC32_INITIAL_VALUE
    }

    override fun close() {
        if (isSourceOwned) delegate.close()
    }
}

// TODO: document this
fun RawSource.crc32Source(isSourceOwned: Boolean = true): CRC32Source = CRC32SourceImpl(this, isSourceOwned)