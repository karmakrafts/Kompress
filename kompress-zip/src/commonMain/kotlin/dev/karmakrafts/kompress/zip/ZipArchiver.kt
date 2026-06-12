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
import dev.karmakrafts.kompress.ExperimentalCompressionApi
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.archive.Archiver
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.crc.CRC32
import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.exception.UnsupportedCompressionMethodException
import dev.karmakrafts.kompress.util.encodeToCP437
import dev.karmakrafts.kompress.util.packDateWord
import dev.karmakrafts.kompress.util.packTimeWord
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import kotlin.time.Clock
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
        val compressedSize: Long,
        val localHeaderOffset: Long
    ) // @formatter:on

    private val buffer: Buffer = Buffer()
    private var isClosed: Boolean = false
    private val entries: ArrayDeque<QueuedEntry> = ArrayDeque()
    private val crc32: CRC32 = CRC32()
    private var bytesWritten: Long = 0L

    private fun flushBuffer() {
        val byteCount = buffer.size
        sink.write(buffer, byteCount)
        bytesWritten += byteCount
    }

    private fun encodeString(languageEncoding: Boolean, value: String): ByteArray =
        if (languageEncoding) value.encodeToByteArray()
        else value.encodeToCP437()

    private fun effectiveGPBF(entry: ZipEntry): UShort = entry.gpbf.value or ZipGPBF.OMIT_CHECKSUM_AND_SIZES

    private fun versionNeeded(entry: ZipEntry): UShort = when {
        entry.isZip64 -> ZipConstants.ZIP64_ZIP_VERSION
        entry.compressionMethod == ZipCompressionMethod.DEFLATE -> ZipConstants.DEFLATE_ZIP_VERSION
        else -> ZipConstants.STORED_ZIP_VERSION
    }

    private fun isZip64Required(
        entry: ZipEntry, uncompressedSize: Long, compressedSize: Long, localHeaderOffset: Long
    ): Boolean =
        entry.isZip64 || uncompressedSize > ZipConstants.STANDARD_FIELD_MAX_VALUE || compressedSize > ZipConstants.STANDARD_FIELD_MAX_VALUE || localHeaderOffset > ZipConstants.STANDARD_FIELD_MAX_VALUE

    private fun UInt.Companion.fromSize(size: Long, isZip64: Boolean): UInt =
        if (isZip64) ZipConstants.ZIP64_EXTENDED_FIELD_MARKER else size.toUInt()

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
    private fun writeChecksumAndSizes(isZip64: Boolean, checksum: UInt, uncompressedSize: Long, compressedSize: Long) {
        buffer.writeUIntLeFast(checksum)
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
        val name = encodeString(entry.gpbf.languageEncoding, entry.name)
        buffer.writeUIntLeFast(ZipConstants.LOCAL_FILE_HEADER_MAGIC)
        buffer.writeUShortLeFast(versionNeeded(entry))
        buffer.writeUShortLeFast(effectiveGPBF(entry))
        buffer.writeUShortLeFast(entry.compressionMethod.encodedValue)
        writeTimestamp(entry.modificationTime)
        writeChecksumAndSizes(
            entry.isZip64, ZipConstants.DEFERRED_CHECKSUM, ZipConstants.DEFERRED_SIZE, ZipConstants.DEFERRED_SIZE
        )
        buffer.writeUShortLeFast(name.size.toUShort())
        buffer.writeUShortLeFast(entry.extraFields.byteSize.toUShort())
        buffer.write(name)
        entry.extraFields.encode(buffer)
        flushBuffer()
    }

    /**
     * @see writeChecksumAndSizes
     */
    private fun appendDataDescriptor(isZip64: Boolean, checksum: UInt, uncompressedSize: Long, compressedSize: Long) {
        writeChecksumAndSizes(isZip64, checksum, uncompressedSize, compressedSize)
        flushBuffer()
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.12.
     */
    private fun appendCentralDirectoryHeader( // @formatter:off
        entry: ZipEntry,
        checksum: UInt,
        uncompressedSize: Long,
        compressedSize: Long,
        localHeaderOffset: Long
    ) { // @formatter:on
        val name = encodeString(entry.gpbf.languageEncoding, entry.name)
        val comment = entry.comment?.let { value -> encodeString(entry.gpbf.languageEncoding, value) } ?: ByteArray(0)
        val isZip64 = isZip64Required(entry, uncompressedSize, compressedSize, localHeaderOffset)
        buffer.writeUIntLeFast(ZipConstants.CENTRAL_FILE_HEADER_MAGIC)
        buffer.writeUShortLeFast(ZipConstants.LATEST_ZIP_VERSION)
        buffer.writeUShortLeFast(if (isZip64) ZipConstants.ZIP64_ZIP_VERSION else versionNeeded(entry))
        buffer.writeUShortLeFast(effectiveGPBF(entry))
        buffer.writeUShortLeFast(entry.compressionMethod.encodedValue)
        writeTimestamp(entry.modificationTime)
        buffer.writeUIntLeFast(checksum)
        buffer.writeUIntLeFast(UInt.fromSize(compressedSize, isZip64))
        buffer.writeUIntLeFast(UInt.fromSize(uncompressedSize, isZip64))
        buffer.writeUShortLeFast(name.size.toUShort())
        buffer.writeUShortLeFast(entry.extraFields.byteSize.toUShort())
        buffer.writeUShortLeFast(comment.size.toUShort())
        buffer.writeUShortLeFast(ZipConstants.FIRST_DISK_NUMBER)
        buffer.writeUShortLeFast(ZipConstants.NO_INTERNAL_FILE_ATTRIBUTES)
        buffer.writeUIntLeFast(ZipConstants.NO_EXTERNAL_FILE_ATTRIBUTES)
        buffer.writeUIntLeFast(UInt.fromSize(localHeaderOffset, isZip64))
        buffer.write(name)
        entry.extraFields.encode(buffer)
        buffer.write(comment)
        flushBuffer()
    }

    private inline fun appendData(compressor: Compressor, callback: (Sink) -> Boolean): Pair<UInt, Long> {
        crc32.reset()
        var uncompressedSize = 0L
        sink.compressingSink( // @formatter:off
            compressor = compressor,
            isSinkOwned = false,
            isCompressorOwned = false
        ).use { compressingSink -> // @formatter:on
            var hasMore = true
            while (hasMore) {
                hasMore = callback(buffer)
                if (buffer.size > 0L) {
                    val chunkSize = buffer.size
                    val chunk = buffer.readByteArray()
                    for (byte in chunk) crc32.round(byte)
                    buffer.write(chunk)
                    compressingSink.write(buffer, chunkSize)
                    uncompressedSize += chunkSize
                }
            }
        }
        bytesWritten += compressor.bytesWritten
        return crc32.finalize() to uncompressedSize
    }

    override fun appendEntry(entry: ZipEntry, callback: (Sink) -> Boolean) {
        val localHeaderOffset = bytesWritten
        appendLocalFileHeader(entry)
        val method = entry.compressionMethod
        val compressor = compressors[method]
            ?: throw UnsupportedCompressionMethodException("No compressor specified for ZIP compression method $method")
        compressor.reset()
        val (checksum, uncompressedSize) = appendData(compressor, callback)
        val compressedSize = compressor.bytesWritten
        appendDataDescriptor(
            isZip64Required(entry, uncompressedSize, compressedSize, localHeaderOffset),
            checksum,
            uncompressedSize,
            compressedSize
        )
        entries += QueuedEntry(
            entry, checksum, uncompressedSize, compressedSize, localHeaderOffset
        ) // Queue entry so we can generate CDHs
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.14.
     */
    private fun appendZip64EndOfCentralDirectory(
        entryCount: Long, centralDirectorySize: Long, centralDirectoryOffset: Long
    ) {
        buffer.writeUIntLeFast(ZipConstants.ZIP64_END_OF_CENTRAL_DIRECTORY_MAGIC)
        buffer.writeULongLeFast(ZipConstants.ZIP64_END_OF_CENTRAL_DIRECTORY_RECORD_SIZE)
        buffer.writeUShortLeFast(ZipConstants.LATEST_ZIP_VERSION)
        buffer.writeUShortLeFast(ZipConstants.ZIP64_ZIP_VERSION)
        buffer.writeUIntLeFast(ZipConstants.ZIP64_FIRST_DISK_NUMBER)
        buffer.writeUIntLeFast(ZipConstants.ZIP64_FIRST_DISK_NUMBER)
        buffer.writeULongLeFast(entryCount.toULong())
        buffer.writeULongLeFast(entryCount.toULong())
        buffer.writeULongLeFast(centralDirectorySize.toULong())
        buffer.writeULongLeFast(centralDirectoryOffset.toULong())
        flushBuffer()
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.15.
     */
    private fun appendZip64EndOfCentralDirectoryLocator(zip64EndOfCentralDirectoryOffset: Long) {
        buffer.writeUIntLeFast(ZipConstants.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_MAGIC)
        buffer.writeUIntLeFast(ZipConstants.ZIP64_FIRST_DISK_NUMBER)
        buffer.writeULongLeFast(zip64EndOfCentralDirectoryOffset.toULong())
        buffer.writeUIntLeFast(ZipConstants.ZIP64_DISK_COUNT)
        flushBuffer()
    }

    /**
     * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.16.
     */
    private fun appendEndOfCentralDirectory(
        entryCount: Long, centralDirectorySize: Long, centralDirectoryOffset: Long, isZip64: Boolean
    ) {
        buffer.writeUIntLeFast(ZipConstants.END_OF_CENTRAL_DIRECTORY_MAGIC)
        buffer.writeUShortLeFast(ZipConstants.FIRST_DISK_NUMBER)
        buffer.writeUShortLeFast(ZipConstants.FIRST_DISK_NUMBER)
        buffer.writeUShortLeFast(if (isZip64) ZipConstants.ZIP64_ENTRY_COUNT_MARKER else entryCount.toUShort())
        buffer.writeUShortLeFast(if (isZip64) ZipConstants.ZIP64_ENTRY_COUNT_MARKER else entryCount.toUShort())
        buffer.writeUIntLeFast(if (isZip64) ZipConstants.ZIP64_EXTENDED_FIELD_MARKER else centralDirectorySize.toUInt())
        buffer.writeUIntLeFast(if (isZip64) ZipConstants.ZIP64_EXTENDED_FIELD_MARKER else centralDirectoryOffset.toUInt())
        buffer.writeUShortLeFast(ZipConstants.NO_ARCHIVE_COMMENT_LENGTH)
        flushBuffer()
    }

    private fun finalizeArchive() {
        // TODO: we don't support ADH and AEDR right now, so just omit it
        // Generate central directory headers for all queued ZIP entries
        val entryCount = entries.size.toLong()
        val centralDirectoryOffset = bytesWritten
        var needsZip64 =
            entryCount > ZipConstants.STANDARD_ENTRY_COUNT_MAX_VALUE || centralDirectoryOffset > ZipConstants.STANDARD_FIELD_MAX_VALUE
        while (entries.isNotEmpty()) {
            val (entry, checksum, uncompressedSize, compressedSize, localHeaderOffset) = entries.removeFirst()
            needsZip64 = needsZip64 || isZip64Required(entry, uncompressedSize, compressedSize, localHeaderOffset)
            appendCentralDirectoryHeader(entry, checksum, uncompressedSize, compressedSize, localHeaderOffset)
        }
        val centralDirectorySize = bytesWritten - centralDirectoryOffset
        needsZip64 = needsZip64 || centralDirectorySize > ZipConstants.STANDARD_FIELD_MAX_VALUE
        // Digital signature would go here
        if (needsZip64) {
            val zip64EndOfCentralDirectoryOffset = bytesWritten
            appendZip64EndOfCentralDirectory(entryCount, centralDirectorySize, centralDirectoryOffset)
            appendZip64EndOfCentralDirectoryLocator(zip64EndOfCentralDirectoryOffset)
        }
        appendEndOfCentralDirectory(entryCount, centralDirectorySize, centralDirectoryOffset, needsZip64)
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
): Archiver<ZipEntry, ZipCompressionMethod> =
    ZipArchiver(this, compressors, isSinkOwned, isCompressorOwned) // @formatter:on

@ExperimentalCompressionApi
fun Archiver<in ZipEntry, *>.appendEntry( // @formatter:off
    name: String,
    modificationTime: Instant = Clock.System.now(),
    comment: String? = null,
    callback: (Sink) -> Boolean
) = appendEntry(ZipEntry(
    modificationTime = modificationTime,
    name = name,
    comment = comment
), callback
) // @formatter:on

@ExperimentalCompressionApi
fun Archiver<in ZipEntry, *>.appendEntry( // @formatter:off
    name: String,
    source: RawSource,
    modificationTime: Instant = Clock.System.now(),
    comment: String? = null
) = appendEntry(ZipEntry(
    modificationTime = modificationTime,
    name = name,
    comment = comment
)
) { sink -> // @formatter:on
    sink.transferFrom(source) > 0
}