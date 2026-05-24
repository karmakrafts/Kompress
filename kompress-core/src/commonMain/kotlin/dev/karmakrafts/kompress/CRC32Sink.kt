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
import kotlinx.io.RawSink

/**
 * A [RawSink] that calculates the CRC32 checksum of the data written to it.
 */
interface CRC32Sink : RawSink {
    /**
     * The current CRC32 checksum of the data written to this sink.
     */
    val checksum: UInt

    /**
     * Resets the current [checksum] to its initial value.
     */
    fun reset()
}

private class CRC32SinkImpl( // @formatter:off
    private val delegate: RawSink,
    private val isSinkOwned: Boolean,
    private val crc32: CRC32
) : CRC32Sink, RawSink by delegate { // @formatter:on
    override val checksum: UInt
        get() = crc32.finalize()

    override fun write(source: Buffer, byteCount: Long) {
        crc32.round(source.peek(), byteCount)
        delegate.write(source, byteCount)
    }

    override fun reset() = crc32.reset()

    override fun close() {
        if (isSinkOwned) delegate.close()
    }
}

/**
 * Returns a [CRC32Sink] that wraps this [RawSink].
 *
 * @param isSinkOwned whether the underlying sink is owned by the [CRC32Sink] and should be closed when it is closed.
 * @return a [CRC32Sink] wrapping this [RawSink].
 */
fun RawSink.crc32Sink( // @formatter:off
    isSinkOwned: Boolean = true,
    crc32: CRC32 = CRC32()
): CRC32Sink = CRC32SinkImpl(this, isSinkOwned, crc32) // @formatter:on