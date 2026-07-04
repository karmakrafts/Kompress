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
import kotlinx.io.Source
import kotlinx.io.readUIntLe
import kotlinx.io.readULongLe
import kotlinx.io.writeUIntLe
import kotlinx.io.writeULongLe

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.5.2.
 *
 * @property size Encoded size of this extra field payload in bytes.
 */
@ExperimentalCompressionApi
sealed interface ZipExtraFieldEntryData {
    val size: Long

    /**
     * Encodes this extra field payload to [sink].
     *
     * @param sink Sink receiving the encoded payload.
     */
    fun encode(sink: Sink)

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.5.3.
     *
     * @property uncompressedSize Uncompressed entry size.
     * @property compressedSize Compressed entry size.
     * @property lhrOffset Offset of the local header record.
     * @property startDiskIndex Disk index containing the local header.
     */
    data class Zip64( // @formatter:off
        val uncompressedSize: ULong,
        val compressedSize: ULong,
        val lhrOffset: ULong,
        val startDiskIndex: UInt
    ) : ZipExtraFieldEntryData { // @formatter:on
        /**
         * ZIP64 extra field payload decoding helpers.
         */
        companion object {
            /** Encoded ZIP64 extra field payload size in bytes. */
            const val SIZE: Long = (ULong.SIZE_BYTES * 3 + UInt.SIZE_BYTES).toLong()

            /**
             * Decodes ZIP64 extra field payload data from [source].
             *
             * @param source Source containing the encoded ZIP64 extra field payload.
             * @return Decoded ZIP64 extra field payload.
             */
            fun decode(source: Source): Zip64 = Zip64(
                uncompressedSize = source.readULongLe(),
                compressedSize = source.readULongLe(),
                lhrOffset = source.readULongLe(),
                startDiskIndex = source.readUIntLe()
            )
        }

        override val size: Long = SIZE

        override fun encode(sink: Sink) {
            sink.writeULongLe(uncompressedSize)
            sink.writeULongLe(compressedSize)
            sink.writeULongLe(lhrOffset)
            sink.writeUIntLe(startDiskIndex)
        }
    }
}