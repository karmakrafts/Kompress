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

import dev.karmakrafts.karbide.readUIntLeFast
import dev.karmakrafts.karbide.readUShortLeFast
import dev.karmakrafts.karbide.writeUIntLeFast
import dev.karmakrafts.karbide.writeUShortLeFast
import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.archive.AbstractBufferedUnarchiver
import dev.karmakrafts.kompress.archive.Unarchiver
import dev.karmakrafts.kompress.archive.UnarchiverEntryCallback
import dev.karmakrafts.kompress.crc.CRC32
import dev.karmakrafts.kompress.crc.once
import dev.karmakrafts.kompress.crc.round
import dev.karmakrafts.kompress.decompressingSource
import dev.karmakrafts.kompress.deflate.Inflater
import dev.karmakrafts.kompress.exception.InvalidChecksumException
import dev.karmakrafts.kompress.util.readLatin1String
import dev.karmakrafts.kompress.util.writeLatin1String
import dev.karmakrafts.kompress.util.zeroTerminate
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readUByte
import kotlinx.io.readUShort
import kotlinx.io.writeUByte
import kotlinx.io.writeUShort
import kotlin.time.Instant

@OptIn(InternalCompressionApi::class)
private class GZipUnarchiver( // @formatter:off
    override val source: Source,
    private val inflater: Inflater,
    private val isSourceOwned: Boolean,
    private val isDecompressorOwned: Boolean
) : AbstractBufferedUnarchiver<GZipEntry, GZipCompressionMethod>() { // @formatter:on
    override val decompressors: Map<GZipCompressionMethod, Decompressor> =
        mapOf(GZipCompressionMethod.DEFLATE to inflater)

    private val decompressionBuffer: Buffer = Buffer()
    private var isClosed: Boolean = false
    private val crc32: CRC32 = CRC32()

    private fun parseExtraField(): ByteArray? {
        if (!ensureBufferFilled(UShort.SIZE_BYTES.toLong())) return null
        val extraBytes = buffer.readUShortLeFast().toInt()
        if (!ensureBufferFilled(extraBytes.toLong())) return null
        return buffer.readByteArray(extraBytes)
    }

    // TODO: this is horrible; we don't want to reconstruct the entire header just to compute the checksum
    private fun computeHeaderChecksum(entry: GZipEntry, flags: GZipEntryFlags, xfl: UByte): UShort {
        buffer.writeUShort(GZipConstants.MAGIC) // Magic is normally 2 separate bytes, so no LE
        buffer.writeUByte(GZipCompressionMethod.DEFLATE.encodedValue)
        buffer.writeUByte(flags.value)
        buffer.writeUIntLeFast(entry.modificationTime.epochSeconds.toUInt())
        buffer.writeUByte(xfl)
        buffer.writeUByte(entry.os.encodedValue)
        entry.extraField?.let { extraField ->
            check(extraField.size.toUShort() <= UShort.MAX_VALUE) { "Extra field size exceeds GZip maximum" }
            buffer.writeUShortLeFast(extraField.size.toUShort())
            buffer.write(extraField)
        }
        entry.name?.let { name -> buffer.zeroTerminate { writeLatin1String(name) } }
        entry.comment?.let { comment -> buffer.zeroTerminate { writeLatin1String(comment) } }
        crc32.reset()
        return (crc32.once(buffer, buffer.size) and 0xFFFFU).toUShort()
    }

    private fun checkHeaderChecksum(computedCrc16: UShort): Boolean {
        if (!ensureBufferFilled(UShort.SIZE_BYTES.toLong())) return false
        val crc16 = buffer.readUShortLeFast()
        if (crc16 != computedCrc16) throw InvalidChecksumException(crc16.toUInt(), computedCrc16.toUInt())
        return true
    }

    private fun parseHeader(): GZipEntry? {
        if (!ensureBufferFilled(GZipConstants.HEADER_PREAMBLE_SIZE.toLong())) return null // Not enough data
        val magic = buffer.readUShort() // Magic is normally 2 separate bytes, so no LE
        check(magic == GZipConstants.MAGIC) {
            "Invalid GZip magic, expected 0x${GZipConstants.MAGIC.toHexString()} but got 0x${magic.toHexString()}"
        }
        val method = GZipCompressionMethod.byEncodedValue(buffer.readUByte())
        check(method == GZipCompressionMethod.DEFLATE) {
            "Unsupported GZip compression method 0x${method.encodedValue.toHexString()}"
        }
        val flags = GZipEntryFlags(buffer.readUByte())
        val modificationTime = Instant.fromEpochSeconds(buffer.readUIntLeFast().toLong())
        val xfl = buffer.readUByte()
        val os = GZipOs.byEncodedValue(buffer.readUByte())
        // Read extra field if present
        var extraField: ByteArray? = null
        if (flags.fextra) extraField = parseExtraField() ?: return null
        var name: String? = null
        if (flags.fname) name = source.readLatin1String()
        var comment: String? = null
        if (flags.fcomment) comment = source.readLatin1String()
        val entry = GZipEntry(
            modificationTime = modificationTime,
            os = os,
            isText = flags.ftext,
            name = name,
            comment = comment,
            extraField = extraField
        )
        if (flags.fhcrc && !checkHeaderChecksum(computeHeaderChecksum(entry, flags, xfl))) return null
        return entry
    }

    private fun parseAndCheckTrailer(computedCrc32: UInt): Boolean {
        if (!ensureBufferFilled(GZipConstants.TRAILER_SIZE.toLong())) return false // Source is exhausted
        val crc32 = buffer.readUIntLeFast()
        if (crc32 != computedCrc32) throw InvalidChecksumException(crc32, computedCrc32)
        buffer.skip(UInt.SIZE_BYTES.toLong()) // Skip decompressed size
        return true
    }

    private inline fun decompressData( // @formatter:off
        header: GZipEntry,
        callback: UnarchiverEntryCallback<GZipEntry>
    ): Pair<Boolean, UInt>? { // @formatter:on
        val compressedSize = Inflater.computeCompressedSize(source.peek())
        if (compressedSize == 0L) return false to CRC32.DEFAULT_INITIAL_VALUE // No more data
        if (!ensureBufferFilled(compressedSize)) return null // No more data
        crc32.reset()
        buffer.decompressingSource( // @formatter:off
            decompressor = inflater,
            isSourceOwned = false,
            isDecompressorOwned = false
        ).use { decompressingSource -> // @formatter:on
            callback(header, decompressionBuffer) {
                val result = decompressingSource.readAtMostTo(
                    decompressionBuffer, Decompressor.DEFAULT_BUFFER_SIZE.toLong()
                )
                if (result != -1L) crc32.round(decompressionBuffer.peek(), decompressionBuffer.size)
                result != -1L
            }
        }
        return true to crc32.finalize()
    }

    override fun forEachEntry(callback: UnarchiverEntryCallback<GZipEntry>) {
        while (true) {
            buffer.clear()
            inflater.reset() // Reset decompressor before reading data
            decompressionBuffer.clear()
            val header = parseHeader() ?: break
            val (result, crc32) = decompressData(header, callback) ?: break
            if (!result) break
            if (!parseAndCheckTrailer(crc32)) break
        }
    }

    override fun close() {
        if (isClosed) return
        if (isSourceOwned) source.close()
        if (isDecompressorOwned) inflater.close()
        isClosed = true
    }
}

/**
 * Wraps this [RawSource] into a GZip [Unarchiver] using the given [inflater].
 *
 * @receiver The source to wrap.
 * @param inflater The [Inflater] to use for decompression.
 * @param isSourceOwned Whether the [RawSource] is owned by the unarchiver and should be closed when the unarchiver is closed.
 * @param isDecompressorOwned Whether the [inflater] is owned by the unarchiver and should be closed when the unarchiver is closed.
 * @return A GZip [Unarchiver] for [GZipEntry]s.
 */
fun RawSource.ungzip( // @formatter:off
    inflater: Inflater = Inflater(),
    isSourceOwned: Boolean = true,
    isDecompressorOwned: Boolean = true
): Unarchiver<GZipEntry, GZipCompressionMethod> =
    GZipUnarchiver(buffered(), inflater, isSourceOwned, isDecompressorOwned) // @formatter:on

/**
 * Wraps this [Source] into a GZip [Unarchiver] using the given [inflater].
 *
 * @receiver The source to wrap.
 * @param inflater The [Inflater] to use for decompression.
 * @param isSourceOwned Whether the [Source] is owned by the unarchiver and should be closed when the unarchiver is closed.
 * @param isDecompressorOwned Whether the [inflater] is owned by the unarchiver and should be closed when the unarchiver is closed.
 * @return A GZip [Unarchiver] for [GZipEntry]s.
 */
fun Source.ungzip( // @formatter:off
    inflater: Inflater = Inflater(),
    isSourceOwned: Boolean = true,
    isDecompressorOwned: Boolean = true
): Unarchiver<GZipEntry, GZipCompressionMethod> = GZipUnarchiver(this, inflater, isSourceOwned, isDecompressorOwned) // @formatter:on