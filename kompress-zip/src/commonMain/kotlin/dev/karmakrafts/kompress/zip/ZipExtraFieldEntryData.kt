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
import kotlinx.io.readUIntLe
import kotlinx.io.readULongLe
import kotlinx.io.writeUIntLe
import kotlinx.io.writeULongLe

sealed interface ZipExtraFieldEntryData {
    val size: UShort

    fun encode(sink: Sink)

    data class Zip64( // @formatter:off
        val uncompressedSize: ULong,
        val compressedSize: ULong,
        val lhrOffset: ULong,
        val startDiskIndex: UInt
    ) : ZipExtraFieldEntryData { // @formatter:on
        companion object {
            const val SIZE: Long = 28L

            fun decode(source: Source): Zip64 = Zip64(
                uncompressedSize = source.readULongLe(),
                compressedSize = source.readULongLe(),
                lhrOffset = source.readULongLe(),
                startDiskIndex = source.readUIntLe()
            )
        }

        override val size: UShort = SIZE.toUShort()

        override fun encode(sink: Sink) {
            sink.writeULongLe(uncompressedSize)
            sink.writeULongLe(compressedSize)
            sink.writeULongLe(lhrOffset)
            sink.writeUIntLe(startDiskIndex)
        }
    }
}