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

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readUShortLe
import kotlinx.io.writeUShortLe

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.5.2.
 */
data class ZipExtraFieldEntry( // @formatter:off
    val headerId: ZipExtraFieldEntryHeaderId,
    val data: ZipExtraFieldEntryData
) { // @formatter:on
    companion object {
        fun decode(source: Source): ZipExtraFieldEntry {
            val headerId = ZipExtraFieldEntryHeaderId.byEncodedValue(source.readUShortLe())
            source.skip(UShort.SIZE_BYTES.toLong()) // We don't care about the data size
            val data = headerId.parse(source)
            return ZipExtraFieldEntry(headerId, data)
        }
    }

    inline val size: Long get() = UShort.SIZE_BYTES.toLong() * 2 + data.size

    fun encode(sink: Sink) {
        sink.writeUShortLe(headerId.encodedValue)
        sink.writeUShortLe(data.size.toUShort())
        data.encode(sink)
    }
}