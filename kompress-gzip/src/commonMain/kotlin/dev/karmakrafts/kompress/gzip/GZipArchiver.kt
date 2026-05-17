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

import dev.karmakrafts.kompress.CRC32_INITIAL_VALUE
import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.InternalKompressApi
import dev.karmakrafts.kompress.archive.Archiver
import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.crc32
import dev.karmakrafts.kompress.util.writeZeroTerminatedString
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.writeUByte
import kotlinx.io.writeUIntLe
import kotlinx.io.writeUShort
import kotlinx.io.writeUShortLe
import kotlin.time.Clock.System

@OptIn(InternalKompressApi::class)
private class GZipArchiver( // @formatter:off
    override val sink: RawSink,
    override val compressor: Deflater,
    private val isSinkOwned: Boolean,
    private val isCompressorOwned: Boolean
) : Archiver<GZipEntry, Deflater> { // @formatter:on
    private val buffer: Buffer = Buffer()
    private var isClosed: Boolean = false

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * end of page 7.
     */
    private fun getCurrentXFL(): UByte = when (compressor.level) {
        GZipConstants.MIN_COMPRESSION -> GZipConstants.XFL_MIN_COMPRESSION
        GZipConstants.MAX_COMPRESSION -> GZipConstants.XFL_MAX_COMPRESSION
        else -> GZipConstants.XFL_NONE
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    private fun appendHeader(entry: GZipEntry, flags: GZipEntryFlags) {
        buffer.writeUShort(GZipConstants.MAGIC) // Magic is normally 2 separate bytes, so no LE
        buffer.writeUByte(GZipCompressionMethod.DEFLATE.encodedValue)
        buffer.writeUByte(flags.value)
        buffer.writeUIntLe(entry.modificationTime.epochSeconds.toUInt())
        buffer.writeUByte(getCurrentXFL())
        buffer.writeUByte(entry.os.encodedValue)
        // Write extra field if present
        entry.extraField?.let { extraField ->
            check(extraField.size.toUShort() <= UShort.MAX_VALUE) { "Extra field size exceeds GZip maximum" }
            buffer.writeUShortLe(extraField.size.toUShort())
            buffer.write(extraField)
        }
        // Write original file name if present
        entry.name?.let(buffer::writeZeroTerminatedString)
        // Write file comment if present
        entry.comment?.let(buffer::writeZeroTerminatedString)
        // Compute and write entry header CRC16 sum if flag bit is set
        if (flags.fhcrc) {
            // HCRC is two least significant bytes of header CRC32 up until self
            val crc32 = buffer.peek().crc32(buffer.size)
            val crc16 = (crc32 and 0xFFFFU).toUShort()
            buffer.writeUShortLe(crc16)
        }
        // Flush the entry header into the sink
        sink.write(buffer, buffer.size)
        buffer.clear()
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    override fun appendEntry(entry: GZipEntry, callback: (Sink) -> Boolean) {
        val flags = entry.computeFlags()
        appendHeader(entry, flags)
        // We chunk the entry data and compute the CRC32 of the uncompressed data at the same time
        var crc32 = CRC32_INITIAL_VALUE
        var uncompressedSize = 0L
        sink.compressingSink( // @formatter:off
            compressor = compressor,
            isSinkOwned = false,
            isCompressorOwned = false
        ).use { compressingSink -> // @formatter:on
            while (callback(buffer)) {
                crc32 = buffer.peek().crc32(buffer.size, crc32)
                val chunkSize = buffer.size
                compressingSink.write(buffer, chunkSize)
                buffer.clear()
                uncompressedSize += chunkSize
            }
        }
        // Append the CRC32 of the uncompressed data and the uncompressed size (breaks over 4GB)
        buffer.writeUIntLe(crc32)
        buffer.writeUIntLe(uncompressedSize.toUInt())
        sink.write(buffer, buffer.size)
        buffer.clear()
    }

    override fun close() {
        if (isClosed) return
        if (isSinkOwned) sink.close()
        if (isCompressorOwned) compressor.close()
        buffer.clear()
        isClosed = true
    }
}

// TODO: document this
fun Archiver<in GZipEntry, *>.appendEntry( // @formatter:off
    name: String,
    comment: String? = null,
    isText: Boolean = false,
    callback: (Sink) -> Boolean
) = appendEntry(GZipEntry(
    modificationTime = System.now(),
    os = GZipOs.guessCurrent(),
    isText = isText,
    name = name,
    comment = comment
), callback
) // @formatter:on

// TODO: document this
fun Archiver<in GZipEntry, *>.appendEntry( // @formatter:off
    name: String,
    comment: String? = null,
    isText: Boolean = false,
    source: RawSource
) = appendEntry(GZipEntry(
    modificationTime = System.now(),
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
 * // TODO: document new parameters
 * @return A GZip [Archiver] for [GZipEntry]s.
 */
fun RawSink.gzip( // @formatter:off
    deflater: Deflater = Deflater(),
    isSinkOwned: Boolean = true,
    isCompressorOwned: Boolean = true
): Archiver<GZipEntry, Deflater> = GZipArchiver(this, deflater, isSinkOwned, isCompressorOwned) // @formatter:on