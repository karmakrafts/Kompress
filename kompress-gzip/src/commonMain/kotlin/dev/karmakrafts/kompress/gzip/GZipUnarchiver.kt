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
import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.InternalKompressApi
import dev.karmakrafts.kompress.InvalidChecksumException
import dev.karmakrafts.kompress.archive.Unarchiver
import dev.karmakrafts.kompress.archive.UnarchiverEntryCallback
import dev.karmakrafts.kompress.crc32
import dev.karmakrafts.kompress.decompressingSource
import dev.karmakrafts.kompress.util.readZeroTerminatedString
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readUByte
import kotlinx.io.readUIntLe
import kotlinx.io.readUShort
import kotlinx.io.readUShortLe
import kotlin.time.Instant

@OptIn(InternalKompressApi::class)
private class GZipUnarchiver( // @formatter:off
    override val source: Source,
    override val decompressor: Inflater,
    private val isSourceOwned: Boolean,
    private val isDecompressorOwned: Boolean
) : Unarchiver<GZipEntry, Inflater> { // @formatter:on
    private val buffer: Buffer = Buffer()
    private val decompressionBuffer: Buffer = Buffer()
    private var isClosed: Boolean = false

    // Allows buffering N bytes on demand based on the bytes already in the buffer
    private fun ensureBufferFilled(size: Long): Boolean {
        var missing = size - buffer.size
        var read = source.readAtMostTo(buffer, missing)
        if (read == -1L) return false
        missing -= read
        while (missing > 0) {
            read = source.readAtMostTo(buffer, missing)
            if (read == -1L) break
            missing -= read
        }
        return missing == 0L
    }

    private fun parseExtraField(): ByteArray? {
        if (!ensureBufferFilled(UShort.SIZE_BYTES.toLong())) return null
        val extraBytes = buffer.readUShortLe().toInt()
        if (!ensureBufferFilled(extraBytes.toLong())) return null
        return buffer.readByteArray(extraBytes)
    }

    private fun computeHeaderChecksum(entry: GZipEntry): UShort {
        return 0U.toUShort()
    }

    private fun checkHeaderChecksum(computedCrc16: UShort): Boolean {
        if (!ensureBufferFilled(UShort.SIZE_BYTES.toLong())) return false
        val crc16 = buffer.readUShortLe()
        //if (crc16 != computedCrc16) throw InvalidChecksumException(crc16.toUInt(), computedCrc16.toUInt())
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
        val modificationTime = Instant.fromEpochSeconds(buffer.readUIntLe().toLong())
        buffer.skip(UByte.SIZE_BYTES.toLong()) // Skip XFL, we don't care about it for decompression
        val os = GZipOs.byEncodedValue(buffer.readUByte())
        // Read extra field if present
        var extraField: ByteArray? = null
        if (flags.fextra) extraField = parseExtraField() ?: return null
        var name: String? = null
        if (flags.fname) name = source.readZeroTerminatedString()
        var comment: String? = null
        if (flags.fcomment) comment = source.readZeroTerminatedString()
        val entry = GZipEntry(
            modificationTime = modificationTime,
            os = os,
            isText = flags.ftext,
            name = name,
            comment = comment,
            extraField = extraField
        )
        if (flags.fhcrc && !checkHeaderChecksum(computeHeaderChecksum(entry))) return null
        return entry
    }

    private fun parseAndCheckTrailer(computedCrc32: UInt): Boolean {
        if (!ensureBufferFilled(GZipConstants.TRAILER_SIZE.toLong())) return false // Source is exhausted
        val crc32 = buffer.readUIntLe()
        if (crc32 != computedCrc32) throw InvalidChecksumException(crc32, computedCrc32)
        buffer.skip(UInt.SIZE_BYTES.toLong()) // Skip decompressed size
        return true
    }

    private inline fun decompressData( // @formatter:off
        header: GZipEntry,
        callback: UnarchiverEntryCallback<GZipEntry>
    ): Pair<Boolean, UInt>? { // @formatter:on
        val compressedSize = Inflater.computeCompressedSize(source.peek())
        if (compressedSize == 0L) return false to CRC32_INITIAL_VALUE // No more data
        if (!ensureBufferFilled(compressedSize)) return null // No more data
        var computedCrc32 = CRC32_INITIAL_VALUE
        buffer.decompressingSource( // @formatter:off
            decompressor = decompressor,
            isSourceOwned = false,
            isDecompressorOwned = false
        ).use { decompressingSource -> // @formatter:on
            callback(header, decompressionBuffer) {
                val result = decompressingSource.readAtMostTo(
                    decompressionBuffer, Decompressor.DEFAULT_BUFFER_SIZE.toLong()
                )
                if (result != -1L) computedCrc32 = decompressionBuffer.peek().crc32(initialValue = computedCrc32)
                result != -1L
            }
        }
        return true to computedCrc32
    }

    override fun forEachEntry(callback: UnarchiverEntryCallback<GZipEntry>) {
        while (true) {
            buffer.clear()
            decompressor.reset() // Reset decompressor before reading data
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
        if (isDecompressorOwned) decompressor.close()
        isClosed = true
    }
}

/**
 * Wraps this [RawSource] into a GZip [Unarchiver] using the given [inflater].
 *
 * @param inflater The [Inflater] to use for decompression.
 * // TODO: document new parameters
 * @return A GZip [Unarchiver] for [GZipEntry]s.
 */
fun RawSource.ungzip( // @formatter:off
    inflater: Inflater = Inflater(),
    isSourceOwned: Boolean = true,
    isDecompressorOwned: Boolean = true
): Unarchiver<GZipEntry, Inflater> =
    GZipUnarchiver(buffered(), inflater, isSourceOwned, isDecompressorOwned) // @formatter:on

/**
 * Wraps this [Source] into a GZip [Unarchiver] using the given [inflater].
 *
 * @param inflater The [Inflater] to use for decompression.
 * // TODO: document new parameters
 * @return A GZip [Unarchiver] for [GZipEntry]s.
 */
fun Source.ungzip( // @formatter:off
    inflater: Inflater = Inflater(),
    isSourceOwned: Boolean = true,
    isDecompressorOwned: Boolean = true
): Unarchiver<GZipEntry, Inflater> = GZipUnarchiver(this, inflater, isSourceOwned, isDecompressorOwned) // @formatter:on