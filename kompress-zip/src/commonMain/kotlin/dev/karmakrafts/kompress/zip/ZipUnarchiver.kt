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

import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.archive.Unarchiver
import dev.karmakrafts.kompress.archive.UnarchiverEntryCallback
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered

private class ZipUnarchiver(
    override val source: Source,
    override val decompressors: Map<ZipCompressionMethod, Decompressor>,
    private val isSourceOwned: Boolean,
    private val areDecompressorsOwned: Boolean
) : Unarchiver<ZipEntry, ZipCompressionMethod> {
    private var isClosed: Boolean = false

    override fun forEachEntry(callback: UnarchiverEntryCallback<ZipEntry>) {
        TODO("Not yet implemented")
    }

    override fun close() {
        if (isClosed) return
        if (isSourceOwned) source.close()
        if (areDecompressorsOwned) decompressors.values.forEach(AutoCloseable::close)
        isClosed = true
    }
}

fun RawSource.unzip( // @formatter:off
    decompressors: Map<ZipCompressionMethod, Decompressor> = mapOf(ZipCompressionMethod.DEFLATE to Inflater()),
    isSourceOwned: Boolean = true,
    areDecompressorsOwned: Boolean = true
): Unarchiver<ZipEntry, ZipCompressionMethod> =
    ZipUnarchiver(buffered(), decompressors, isSourceOwned, areDecompressorsOwned) // @formatter:on

fun Source.unzip( // @formatter:off
    decompressors: Map<ZipCompressionMethod, Decompressor> = mapOf(ZipCompressionMethod.DEFLATE to Inflater()),
    isSourceOwned: Boolean = true,
    areDecompressorsOwned: Boolean = true
): Unarchiver<ZipEntry, ZipCompressionMethod> =
    ZipUnarchiver(this, decompressors, isSourceOwned, areDecompressorsOwned) // @formatter:on