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
import kotlinx.io.readUShortLe
import kotlinx.io.writeUShortLe

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.5.2.
 *
 * @property headerId Header identifier describing the extra field payload type.
 * @property data Parsed payload data for the extra field.
 */
@ExperimentalCompressionApi
data class ZipExtraFieldEntry( // @formatter:off
    val headerId: ZipExtraFieldEntryHeaderId,
    val data: ZipExtraFieldEntryData
) { // @formatter:on
    /**
     * Utilities for decoding extra field entries.
     */
    companion object {
        /**
         * Decodes a single extra field entry from [source].
         *
         * @param source Source positioned at the extra field entry header id.
         * @return Decoded extra field entry.
         */
        fun decode(source: Source): ZipExtraFieldEntry {
            val headerId = ZipExtraFieldEntryHeaderId.byEncodedValue(source.readUShortLe())
            source.skip(UShort.SIZE_BYTES.toLong()) // We don't care about the data size
            val data = headerId.parse(source)
            return ZipExtraFieldEntry(headerId, data)
        }
    }

    /**
     * Total encoded entry size in bytes, including id and size fields.
     */
    inline val size: Long get() = UShort.SIZE_BYTES.toLong() * 2 + data.size

    /**
     * Encodes this extra field entry to [sink].
     *
     * @param sink Sink receiving the encoded entry.
     */
    fun encode(sink: Sink) {
        sink.writeUShortLe(headerId.encodedValue)
        sink.writeUShortLe(data.size.toUShort())
        data.encode(sink)
    }
}