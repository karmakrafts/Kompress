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

import dev.karmakrafts.kompress.CRC32_INITIAL_VALUE
import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.InternalKompressApi
import dev.karmakrafts.kompress.UnsupportedCompressionMethodException
import dev.karmakrafts.kompress.archive.Archiver
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.crc32Round
import dev.karmakrafts.kompress.util.packDateWord
import dev.karmakrafts.kompress.util.packTimeWord
import dev.karmakrafts.kompress.util.writeCP437String
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.writeString
import kotlinx.io.writeUIntLe
import kotlinx.io.writeULongLe
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
    private val entries: ArrayDeque<ZipEntry> = ArrayDeque()

    private fun flushBuffer() {
        sink.write(buffer, buffer.size)
    }

    /**
     * Writes a raw CP437 or UTF-8 string based on the [entry]'s given
     * language encoding flag. See [ZipGPBF.languageEncoding].
     */
    private fun writeString(languageEncoding: Boolean, value: String) {
        if (languageEncoding) {
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
        buffer.writeUShortLe(localDateTime.packTimeWord())
        buffer.writeUShortLe(localDateTime.packDateWord())
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.9.
     */
    private fun writeDataDescriptor(isZip64: Boolean, checksum: UInt, uncompressedSize: Long, compressedSize: Long) {
        buffer.writeUIntLe(checksum)
        // When dealing with ZIP64, size fields are 8 bytes instead of 4
        if (isZip64) {
            buffer.writeULongLe(compressedSize.toULong())
            buffer.writeULongLe(uncompressedSize.toULong())
            return
        }
        buffer.writeUIntLe(compressedSize.toUInt())
        buffer.writeUIntLe(uncompressedSize.toUInt())
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
        if (entry.gpbf.omitChecksumAndSizes) writeDataDescriptor(entry.isZip64, 0U, 0L, 0L)
        else writeDataDescriptor(entry.isZip64, 0U, 0L, 0L) // TODO: implement support for these
        buffer.writeUShortLe(entry.name.length.toUShort())
        buffer.writeUShortLe(entry.extraFields.byteSize.toUShort())
        writeString(entry.gpbf.languageEncoding, entry.name)
        entry.extraFields.encode(buffer)
        flushBuffer()
    }

    /**
     * @see writeDataDescriptor
     */
    private fun appendDataDescriptor(isZip64: Boolean, checksum: UInt, uncompressedSize: Long, compressedSize: Long) {
        writeDataDescriptor(isZip64, checksum, uncompressedSize, compressedSize)
        flushBuffer()
    }

    private fun appendCentralDirectoryHeader(entry: ZipEntry) {
        // TODO: implement this
    }

    private inline fun appendData(compressor: Compressor, callback: (Sink) -> Boolean): UInt {
        var crc32 = CRC32_INITIAL_VALUE
        sink.compressingSink( // @formatter:off
            compressor = compressor,
            isSinkOwned = false,
            isCompressorOwned = false
        ).use { compressingSink -> // @formatter:on
            var hasMore = true
            while (hasMore) {
                hasMore = callback(buffer)
                if (buffer.size > 0L) {
                    crc32 = buffer.peek().crc32Round(buffer.size, crc32)
                    val chunkSize = buffer.size
                    compressingSink.write(buffer, chunkSize)
                }
            }
        }
        return crc32.inv()
    }

    override fun appendEntry(entry: ZipEntry, callback: (Sink) -> Boolean) {
        appendLocalFileHeader(entry)
        val method = entry.compressionMethod
        val compressor = compressors[method]
            ?: throw UnsupportedCompressionMethodException("No compressor specified for ZIP compression method $method")
        compressor.reset()
        val checksum = appendData(compressor, callback)
        if (entry.gpbf.omitChecksumAndSizes) {
            appendDataDescriptor(entry.isZip64, checksum, compressor.bytesRead, compressor.bytesWritten)
        }
        entries += entry // Queue entry so we can generate CDHs
    }

    private fun finalizeArchive() {
        // TODO: we don't support ADH and AEDR right now, so just omit it
        // Generate central directory headers for all queued ZIP entries
        while (entries.isNotEmpty()) {
            appendCentralDirectoryHeader(entries.removeFirst())
        }
    }

    override fun close() {
        if (isClosed) return
        finalizeArchive()
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