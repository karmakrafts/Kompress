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
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.archiver.Unarchiver
import dev.karmakrafts.kompress.archiver.UnarchiverEntryCallback
import dev.karmakrafts.kompress.crc32
import dev.karmakrafts.kompress.decompressing
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlinx.io.readUByte
import kotlinx.io.readUIntLe
import kotlinx.io.readUShort
import kotlinx.io.readUShortLe
import kotlin.time.Instant

private class GZipUnarchiver( // @formatter:off
    override var source: RawSource,
    override var decompressor: Inflater
) : Unarchiver<GZipEntry, Inflater> { // @formatter:on
    companion object {
        private const val CHUNK_SIZE: Int = 4096
    }

    private val buffer: Buffer = Buffer()

    private fun ensureBufferFilled(size: Long): Boolean {
        var missing = size - buffer.size
        var read = source.readAtMostTo(buffer, missing)
        if (read == -1L) return false // Reached EOF
        missing -= read
        while (missing > 0L) {
            read = source.readAtMostTo(buffer, missing)
            if (read == -1L) break // Reached EOF
            missing -= read
        }
        return missing == 0L
    }

    // We have to transfer/read byte by byte here sadly
    // TODO: optimize this
    private fun readZeroTerminatedString(): String? {
        if (!ensureBufferFilled(1L)) return null // Not enough data available
        var byte = buffer.readByte()
        val result = ArrayList<Byte>()
        while (byte != 0.toByte()) {
            if (!ensureBufferFilled(1L)) return null // Not enough data available
            result += byte
            byte = buffer.readByte()
        }
        return result.toByteArray().decodeToString()
    }

    private fun ensureBufferFilled(
        source: RawSource = this.source
    ): Boolean = source.readAtMostTo(buffer, CHUNK_SIZE.toLong()) != -1L

    private fun parseHeader(): GZipEntry? {
        if (!ensureBufferFilled(10L)) return null // Not enough data available
        // Validate entry header megic
        val magic = buffer.readUShort()
        check(magic == GZipConstants.MAGIC) {
            "Invalid GZip magic, expected 0x${GZipConstants.MAGIC.toHexString()} but got 0x${magic.toHexString()}"
        }
        // Check compression method
        val rawCompressionMethod = buffer.readUByte()
        val compressionMethod = GZipCompressionMethod.byEncodedValue(rawCompressionMethod)
        check(compressionMethod == GZipCompressionMethod.DEFLATE) {
            "Unsupported GZip compression method 0x${rawCompressionMethod.toHexString()}"
        }
        // Read entry flags
        val flags = GZipEntryFlags(buffer.readUByte())
        val modificationTime = Instant.fromEpochSeconds(buffer.readUIntLe().toLong())
        buffer.skip(UShort.SIZE_BYTES.toLong()) // Skip XFL, we let compressor detect
        val os = GZipOs.byEncodedValue(buffer.readUByte())
        // Parse optional fields
        var extraField: ByteArray? = null
        var name: String? = null
        var comment: String? = null
        if (flags.fextra) {
            if (!ensureBufferFilled(2L)) return null // Not enough data available
            val size = buffer.readUShortLe()
            if (!ensureBufferFilled(size.toLong())) return null // Not enough data available
            extraField = buffer.readByteArray(size.toInt())
        }
        if (flags.fname) name = readZeroTerminatedString() ?: return null // Not enough data available
        if (flags.fcomment) comment = readZeroTerminatedString() ?: return null // Not enough data available
        // TODO: validate header checksum if present
        // Create entry and invoke callback
        return GZipEntry( // @formatter:off
            modificationTime = modificationTime,
            os = os,
            isText = flags.ftext,
            name = name,
            comment = comment,
            extraField = extraField
        ) // @formatter:on
    }

    override fun forEachEntry(callback: UnarchiverEntryCallback<GZipEntry>) {
        while (true) {
            val entry = parseHeader() ?: break // Not enough data available, no more entries
            var computedCrc32 = CRC32_INITIAL_VALUE
            var computedUncompressedSize = 0L
            source.decompressing(decompressor).use { decompressingSource ->
                callback(entry, buffer) {
                    // Every time we request more data from the entry, we perform another CRC round
                    val result = ensureBufferFilled(decompressingSource)
                    computedUncompressedSize += buffer.size
                    if (result) computedCrc32 = buffer.peek().crc32(initialValue = computedCrc32)
                    result
                }
            }
            // Read and check trailer
            if (!ensureBufferFilled(8L)) break // Not enough data available, no more entries
            val crc32 = buffer.readUIntLe()
            check(crc32 == computedCrc32) {
                "Invalid CRC32 checksum for uncompressed data, expected 0x${crc32.toHexString()} but got 0x${computedCrc32.toHexString()}"
            }
            val uncompressedSize = buffer.readUIntLe()
            check(uncompressedSize.toLong() == computedUncompressedSize) {
                "Mismatched compressed data size, expected $uncompressedSize bytes but got $computedUncompressedSize"
            }
        }
    }

    override fun close() {
        source.close()
        decompressor.close()
        buffer.clear()
    }
}

/**
 * Wraps this [RawSource] into a GZip [Unarchiver] using the given [inflater].
 *
 * @param inflater The [Inflater] to use for decompression.
 * @return A GZip [Unarchiver] for [GZipEntry]s.
 */
fun RawSource.ungzip( // @formatter:off
    inflater: Inflater = Inflater()
): Unarchiver<GZipEntry, Inflater> = GZipUnarchiver(this, inflater) // @formatter:on