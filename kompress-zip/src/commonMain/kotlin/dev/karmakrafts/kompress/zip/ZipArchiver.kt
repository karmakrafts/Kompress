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

import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.archive.Archiver
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.writeUIntLe
import kotlinx.io.writeUShortLe

private class ZipArchiver(
    override val sink: RawSink,
    override val compressors: Map<ZipCompressionMethod, Compressor>,
    private val isSinkOwned: Boolean,
    private val areCompressorsOwned: Boolean
) : Archiver<ZipEntry, ZipCompressionMethod> {
    private val buffer: Buffer = Buffer()
    private var isClosed: Boolean = false

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.7.
     */
    private fun appendLocalFileHeader(entry: ZipEntry) {
        buffer.writeUIntLe(ZipConstants.LOCAL_FILE_HEADER_MAGIC)
        buffer.writeUShortLe(ZipConstants.LATEST_ZIP_VERSION) // TODO: determine this based on features?
        buffer.writeUShortLe(0U) // TODO: Implement GPBF encoding
        buffer.writeUShortLe(entry.compressionMethod.encodedValue)
        buffer.writeUShortLe(0U) // TODO: Implement last mod file time
        buffer.writeUShortLe(0U) // TODO: Implement last mod file date
        buffer.writeUIntLe(0U) // TODO: Implement CRC-32
        buffer.writeUIntLe(0U) // TODO: Implement compressed size
        buffer.writeUIntLe(0U) // TODO: Implement uncompressed size
        buffer.writeUShortLe(0U) // TODO: Implement file name length
        buffer.writeUShortLe(0U) // TODO: Implement extra field length
    }

    override fun appendEntry(entry: ZipEntry, callback: (Sink) -> Boolean) {
        TODO("Not yet implemented")
    }

    override fun close() {
        if (isClosed) return
        if (isSinkOwned) sink.close()
        if (areCompressorsOwned) compressors.values.forEach(AutoCloseable::close)
        isClosed = true
    }
}

fun RawSink.zip( // @formatter:off
    compressors: Map<ZipCompressionMethod, Compressor> = mapOf(ZipCompressionMethod.DEFLATE to Deflater()),
    isSinkOwned: Boolean = true,
    isCompressorOwned: Boolean = true
): Archiver<ZipEntry, ZipCompressionMethod> = ZipArchiver(this, compressors, isSinkOwned, isCompressorOwned) // @formatter:on