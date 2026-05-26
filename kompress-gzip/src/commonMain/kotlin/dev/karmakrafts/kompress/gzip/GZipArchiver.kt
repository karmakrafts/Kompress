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

package dev.karmakrafts.kompress.gzip

import dev.karmakrafts.karbide.writeUIntLeFast
import dev.karmakrafts.karbide.writeUShortLeFast
import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.archive.Archiver
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.crc.CRC32
import dev.karmakrafts.kompress.crc.once
import dev.karmakrafts.kompress.crc.round
import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.util.FileUtils
import dev.karmakrafts.kompress.util.writeLatin1String
import dev.karmakrafts.kompress.util.zeroTerminate
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeUByte
import kotlinx.io.writeUShort
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(InternalCompressionApi::class)
private class GZipArchiver( // @formatter:off
    override val sink: RawSink,
    private val deflater: Deflater,
    private val isSinkOwned: Boolean,
    private val isCompressorOwned: Boolean
) : Archiver<GZipEntry, GZipCompressionMethod> { // @formatter:on
    override val compressors: Map<GZipCompressionMethod, Compressor> = mapOf(
        GZipCompressionMethod.DEFLATE to deflater
    )

    private val buffer: Buffer = Buffer()
    private var isClosed: Boolean = false
    private val crc32: CRC32 = CRC32()

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * end of page 7.
     */
    private fun getCurrentXFL(): UByte = when (deflater.level) {
        GZipConstants.MIN_COMPRESSION -> GZipConstants.XFL_MIN_COMPRESSION
        GZipConstants.MAX_COMPRESSION -> GZipConstants.XFL_MAX_COMPRESSION
        else -> GZipConstants.XFL_NONE
    }

    private fun appendExtraField(extraField: ByteArray) {
        check(extraField.size.toUShort() <= UShort.MAX_VALUE) { "Extra field size exceeds GZip maximum" }
        buffer.writeUShortLeFast(extraField.size.toUShort())
        buffer.write(extraField)
    }

    private fun appendHeaderChecksum() {
        // HCRC is two least significant bytes of header CRC32 up until self
        crc32.reset()
        val crc32 = crc32.once(buffer.peek(), buffer.size)
        val crc16 = (crc32 and 0xFFFFU).toUShort()
        buffer.writeUShortLeFast(crc16)
    }

    private fun flushBuffer() {
        sink.write(buffer, buffer.size)
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    private fun appendHeader(entry: GZipEntry, flags: GZipEntryFlags) {
        buffer.writeUShort(GZipConstants.MAGIC) // Magic is normally 2 separate bytes, so no LE
        buffer.writeUByte(GZipCompressionMethod.DEFLATE.encodedValue)
        buffer.writeUByte(flags.value)
        buffer.writeUIntLeFast(entry.modificationTime.epochSeconds.toUInt())
        buffer.writeUByte(getCurrentXFL())
        buffer.writeUByte(entry.os.encodedValue)
        entry.extraField?.let(::appendExtraField)
        entry.name?.let { name -> buffer.zeroTerminate { writeLatin1String(name) } }
        entry.comment?.let { comment -> buffer.zeroTerminate { writeLatin1String(comment) } }
        if (flags.fhcrc) appendHeaderChecksum()
        flushBuffer()
    }

    private fun appendTrailer(crc32: UInt, uncompressedSize: Long) {
        buffer.writeUIntLeFast(crc32)
        buffer.writeUIntLeFast(uncompressedSize.toUInt())
        flushBuffer()
    }

    private inline fun appendData(callback: (Sink) -> Boolean): Pair<UInt, Long> {
        crc32.reset()
        var uncompressedSize = 0L
        sink.compressingSink( // @formatter:off
            compressor = deflater,
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
                    uncompressedSize += chunkSize
                }
            }
        }
        return crc32.finalize() to uncompressedSize
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    override fun appendEntry(entry: GZipEntry, callback: (Sink) -> Boolean) {
        deflater.reset()
        val flags = entry.computeFlags()
        appendHeader(entry, flags)
        val (crc32, uncompressedSize) = appendData(callback)
        appendTrailer(crc32, uncompressedSize)
    }

    override fun close() {
        if (isClosed) return
        if (isSinkOwned) sink.close()
        if (isCompressorOwned) deflater.close()
        buffer.clear()
        isClosed = true
    }
}

/**
 * Appends a new entry to the GZip archive from the given [path].
 *
 * @param path The path to the file to append.
 * @param modificationTime The modification time of the file. Defaults to the actual modification time of the file or the current time if not available.
 * @param comment An optional comment for the entry.
 * @param isText Whether the entry is a text file.
 */
@OptIn(InternalCompressionApi::class)
fun Archiver<in GZipEntry, *>.appendEntry( // @formatter:off
    path: Path,
    modificationTime: Instant = FileUtils.getModificationTime(path) ?: Clock.System.now(),
    comment: String? = null,
    isText: Boolean = false
) { // @formatter:on
    return SystemFileSystem.source(path).use { source ->
        appendEntry( // @formatter:off
            modificationTime = modificationTime,
            name = path.name,
            comment = comment,
            isText = isText,
            source = source
        ) // @formatter:on
    }
}

/**
 * Appends a new entry to the GZip archive with the given [name] and a [callback] to write the data.
 *
 * @param name The name of the entry.
 * @param modificationTime The modification time of the entry. Defaults to the current time.
 * @param comment An optional comment for the entry.
 * @param isText Whether the entry is a text file.
 * @param callback A callback to write the entry's data to the given [Sink].
 */
fun Archiver<in GZipEntry, *>.appendEntry( // @formatter:off
    name: String,
    modificationTime: Instant = Clock.System.now(),
    comment: String? = null,
    isText: Boolean = false,
    callback: (Sink) -> Boolean
) = appendEntry(GZipEntry(
    modificationTime = modificationTime,
    os = GZipOs.guessCurrent(),
    isText = isText,
    name = name,
    comment = comment
), callback
) // @formatter:on

/**
 * Appends a new entry to the GZip archive with the given [name] and [source].
 *
 * @param name The name of the entry.
 * @param source The source to read the entry's data from.
 * @param modificationTime The modification time of the entry. Defaults to the current time.
 * @param comment An optional comment for the entry.
 * @param isText Whether the entry is a text file.
 */
fun Archiver<in GZipEntry, *>.appendEntry( // @formatter:off
    name: String,
    source: RawSource,
    modificationTime: Instant = Clock.System.now(),
    comment: String? = null,
    isText: Boolean = false,
) = appendEntry(GZipEntry(
    modificationTime = modificationTime,
    os = GZipOs.guessCurrent(),
    isText = isText,
    name = name,
    comment = comment
)
) { sink -> // @formatter:on
    sink.transferFrom(source) > 0
}

/**
 * Wraps this [RawSink] into a GZip [Archiver] using the given [deflater].
 *
 * @param deflater The [Deflater] to use for compression.
 * @param isSinkOwned Whether the [RawSink] is owned by the archiver and should be closed when the archiver is closed.
 * @param isCompressorOwned Whether the [deflater] is owned by the archiver and should be closed when the archiver is closed.
 * @return A GZip [Archiver] for [GZipEntry]s.
 */
fun RawSink.gzip( // @formatter:off
    deflater: Deflater = Deflater(),
    isSinkOwned: Boolean = true,
    isCompressorOwned: Boolean = true
): Archiver<GZipEntry, GZipCompressionMethod> = GZipArchiver(this, deflater, isSinkOwned, isCompressorOwned) // @formatter:on