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

import dev.karmakrafts.karbide.writeUIntLeFast
import dev.karmakrafts.karbide.writeULongLeFast
import dev.karmakrafts.karbide.writeUShortLeFast
import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.ExperimentalCompressionApi
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.archive.Archiver
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.crc.CRC32
import dev.karmakrafts.kompress.crc.round
import dev.karmakrafts.kompress.exception.UnsupportedCompressionMethodException
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
import kotlin.time.Instant

@OptIn(InternalCompressionApi::class, ExperimentalCompressionApi::class)
private class ZipArchiver(
    override val sink: RawSink,
    override val compressors: Map<ZipCompressionMethod, Compressor>,
    private val isSinkOwned: Boolean,
    private val areCompressorsOwned: Boolean
) : Archiver<ZipEntry, ZipCompressionMethod> {
    data class QueuedEntry( // @formatter:off
        val entry: ZipEntry,
        val checksum: UInt,
        val uncompressedSize: Long,
        val compressedSize: Long
    ) // @formatter:on

    private val buffer: Buffer = Buffer()
    private var isClosed: Boolean = false
    private val entries: ArrayDeque<QueuedEntry> = ArrayDeque()
    private val crc32: CRC32 = CRC32()

    private fun flushBuffer() {
        sink.write(buffer, buffer.size)
    }

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
        buffer.writeUShortLeFast(localDateTime.packTimeWord())
        buffer.writeUShortLeFast(localDateTime.packDateWord())
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.9.
     */
    private fun writeDataDescriptor(isZip64: Boolean, checksum: UInt, uncompressedSize: Long, compressedSize: Long) {
        buffer.writeUIntLe(checksum)
        // When dealing with ZIP64, size fields are 8 bytes instead of 4
        if (isZip64) {
            buffer.writeULongLeFast(compressedSize.toULong())
            buffer.writeULongLeFast(uncompressedSize.toULong())
            return
        }
        buffer.writeUIntLeFast(compressedSize.toUInt())
        buffer.writeUIntLeFast(uncompressedSize.toUInt())
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.7.
     */
    private fun appendLocalFileHeader(entry: ZipEntry) {
        buffer.writeUIntLeFast(ZipConstants.LOCAL_FILE_HEADER_MAGIC)
        buffer.writeUShortLeFast(ZipConstants.LATEST_ZIP_VERSION) // TODO: determine this based on features?
        buffer.writeUShortLeFast(entry.gpbf.value)
        buffer.writeUShortLeFast(entry.compressionMethod.encodedValue)
        writeTimestamp(entry.modificationTime)
        if (entry.gpbf.omitChecksumAndSizes) writeDataDescriptor(entry.isZip64, 0U, 0L, 0L)
        else writeDataDescriptor(entry.isZip64, 0U, 0L, 0L) // TODO: implement support for these
        buffer.writeUShortLeFast(entry.name.length.toUShort())
        buffer.writeUShortLeFast(entry.extraFields.byteSize.toUShort())
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

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.12.
     */
    private fun appendCentralDirectoryHeader( // @formatter:off
        entry: ZipEntry,
        checksum: UInt,
        uncompressedSize: Long,
        compressedSize: Long
    ) { // @formatter:on
        buffer.writeUIntLeFast(ZipConstants.CENTRAL_FILE_HEADER_MAGIC)
        buffer.writeUShortLeFast(ZipConstants.LATEST_ZIP_VERSION) // TODO: determine this based on features?
        buffer.writeUShortLeFast(ZipConstants.LATEST_ZIP_VERSION) // TODO: set a sensible default for our own version
        buffer.writeUShortLeFast(entry.gpbf.value)
        buffer.writeUShortLeFast(entry.compressionMethod.encodedValue)
        writeTimestamp(entry.modificationTime)
        writeDataDescriptor(entry.isZip64, checksum, uncompressedSize, compressedSize)
        buffer.writeUShortLeFast(0U) // TODO: file name length
        buffer.writeUShortLeFast(0U) // TODO: extra field length
        buffer.writeUShortLeFast(0U) // TODO: file comment length
        buffer.writeUShortLeFast(0U) // TODO: disk number start
        buffer.writeUShortLeFast(0U) // TODO: internal file attribs
        buffer.writeUIntLeFast(0U) // TODO: external file attribs
        buffer.writeUIntLeFast(0U) // TODO: relative offset of local header
        // TODO: file name
        // TODO: extra field
        // TODO: file comment
        flushBuffer()
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.13.
     */
    private fun appendDigitalSignature() {
        // TODO: implement this
    }

    private inline fun appendData(compressor: Compressor, callback: (Sink) -> Boolean): UInt {
        crc32.reset()
        sink.compressingSink( // @formatter:off
            compressor = compressor,
            isSinkOwned = false,
            isCompressorOwned = false
        ).use { compressingSink -> // @formatter:on
            var hasMore = true
            while (hasMore) {
                hasMore = callback(buffer)
                if (buffer.size > 0L) {
                    crc32.round(buffer.peek(), buffer.size)
                    val chunkSize = buffer.size
                    compressingSink.write(buffer, chunkSize)
                }
            }
        }
        return crc32.finalize()
    }

    override fun appendEntry(entry: ZipEntry, callback: (Sink) -> Boolean) {
        appendLocalFileHeader(entry)
        val method = entry.compressionMethod
        val compressor = compressors[method]
            ?: throw UnsupportedCompressionMethodException("No compressor specified for ZIP compression method $method")
        compressor.reset()
        val checksum = appendData(compressor, callback)
        val uncompressedSize = compressor.bytesRead
        val compressedSize = compressor.bytesWritten
        if (entry.gpbf.omitChecksumAndSizes) {
            appendDataDescriptor(entry.isZip64, checksum, uncompressedSize, compressedSize)
        }
        entries += QueuedEntry(entry, checksum, uncompressedSize, compressedSize) // Queue entry so we can generate CDHs
    }

    private fun finalizeArchive() {
        // TODO: we don't support ADH and AEDR right now, so just omit it
        // Generate central directory headers for all queued ZIP entries
        while (entries.isNotEmpty()) {
            val (entry, checksum, uncompressedSize, compressedSize) = entries.removeFirst()
            appendCentralDirectoryHeader(entry, checksum, uncompressedSize, compressedSize)
        }
        appendDigitalSignature()
    }

    override fun close() {
        if (isClosed) return
        finalizeArchive()
        if (isSinkOwned) sink.close()
        if (areCompressorsOwned) compressors.values.forEach(AutoCloseable::close)
        isClosed = true
    }
}

@ExperimentalCompressionApi
fun RawSink.zip( // @formatter:off
    compressors: Map<ZipCompressionMethod, Compressor> = mapOf(ZipCompressionMethod.DEFLATE to Deflater()),
    isSinkOwned: Boolean = true,
    isCompressorOwned: Boolean = true
): Archiver<ZipEntry, ZipCompressionMethod> = ZipArchiver(this, compressors, isSinkOwned, isCompressorOwned) // @formatter:on