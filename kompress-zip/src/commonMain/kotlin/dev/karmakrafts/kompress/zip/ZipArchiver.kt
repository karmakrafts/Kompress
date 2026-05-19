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
import dev.karmakrafts.kompress.InternalKompressApi
import dev.karmakrafts.kompress.archive.Archiver
import dev.karmakrafts.kompress.util.writeCP437String
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.io.writeUIntLe
import kotlinx.io.writeUShortLe
import kotlin.time.Instant

@OptIn(InternalKompressApi::class)
private class ZipArchiver(
    override val sink: RawSink,
    override val compressors: Map<ZipCompressionMethod, Compressor>,
    private val isSinkOwned: Boolean,
    private val areCompressorsOwned: Boolean
) : Archiver<ZipEntry, ZipCompressionMethod> {
    private val buffer: Buffer = Buffer()
    private var isClosed: Boolean = false

    private fun flushBuffer() {
        sink.write(buffer, buffer.size)
    }

    private fun writeString(entry: ZipEntry, value: String) {
        if (entry.gpbf.languageEncoding) {
            // We need to encode as UTF-8 directly
            buffer.writeString(value)
            return
        }
        buffer.writeCP437String(value)
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.6.
     */
    private fun writeTimestamp(value: Instant) {
        val localDateTime = value.toLocalDateTime(TimeZone.currentSystemDefault())
        // Calculate timeword and write it
        val hours = (localDateTime.hour and 0b1111).toUInt()
        val minutes = (localDateTime.minute and 0b1111).toUInt()
        val seconds = ((localDateTime.second shr 1) and 0b1111).toUInt()
        val timeWord = (hours shl 11) or (minutes shl 5) or seconds
        buffer.writeUShortLe(timeWord.toUShort())
        // Calculate dateword and write it
        val year = ((localDateTime.year - 1980) and 0b111111).toUInt()
        val month = (localDateTime.month.number and 0b111).toUInt()
        val day = (localDateTime.day and 0b1111).toUInt()
        val dateWord = (year shl 9) or (month shl 5) or day
        buffer.writeUShortLe(dateWord.toUShort())
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.9.
     */
    private fun writeDataDescriptor() {
        buffer.writeUIntLe(0U) // TODO: Implement CRC-32
        buffer.writeUIntLe(0U) // TODO: Implement compressed size
        buffer.writeUIntLe(0U) // TODO: Implement uncompressed size
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.7.
     */
    private fun appendLocalFileHeader(entry: ZipEntry) {
        buffer.writeUIntLe(ZipConstants.LOCAL_FILE_HEADER_MAGIC)
        buffer.writeUShortLe(ZipConstants.LATEST_ZIP_VERSION) // TODO: determine this based on features?
        buffer.writeUShortLe(entry.gpbf.value)
        buffer.writeUShortLe(entry.compressionMethod.encodedValue)
        writeTimestamp(entry.modificationTime)
        writeDataDescriptor()
        buffer.writeUShortLe(entry.name.length.toUShort())
        buffer.writeUShortLe(entry.extraField?.size?.toUShort() ?: 0U)
        writeString(entry, entry.name)
        entry.extraField?.let(buffer::write)
        flushBuffer()
    }

    /**
     * @see writeDataDescriptor
     */
    private fun appendDataDescriptor() {
        writeDataDescriptor()
        flushBuffer()
    }

    override fun appendEntry(entry: ZipEntry, callback: (Sink) -> Boolean) {
        appendLocalFileHeader(entry)
        // TODO: add compressed data
        if (entry.gpbf.omitChecksumAndSizes) appendDataDescriptor()
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