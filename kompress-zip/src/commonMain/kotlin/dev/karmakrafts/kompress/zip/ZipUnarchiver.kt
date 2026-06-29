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

import dev.karmakrafts.karbide.readUIntLeFast
import dev.karmakrafts.karbide.readUShortLeFast
import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.ExperimentalCompressionApi
import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.archive.AbstractBufferedUnarchiver
import dev.karmakrafts.kompress.archive.Unarchiver
import dev.karmakrafts.kompress.archive.UnarchiverEntryCallback
import dev.karmakrafts.kompress.crc.CRC32
import dev.karmakrafts.kompress.crc.round
import dev.karmakrafts.kompress.decompressingSource
import dev.karmakrafts.kompress.deflate.Inflater
import dev.karmakrafts.kompress.exception.InvalidChecksumException
import dev.karmakrafts.kompress.exception.UnsupportedCompressionMethodException
import dev.karmakrafts.kompress.util.decodeFromLatin1
import dev.karmakrafts.kompress.util.unpackDateWord
import dev.karmakrafts.kompress.util.unpackTimeWord
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readULongLe

@ExperimentalCompressionApi
@OptIn(InternalCompressionApi::class)
private class ZipUnarchiver( // @formatter:off
    override val source: Source,
    override val decompressors: Map<ZipCompressionMethod, Decompressor>,
    private val isSourceOwned: Boolean,
    private val areDecompressorsOwned: Boolean
) : AbstractBufferedUnarchiver<ZipEntry, ZipCompressionMethod>() { // @formatter:on
    private val decompressionBuffer: Buffer = Buffer()
    private var isClosed: Boolean = false
    private val crc32: CRC32 = CRC32()

    private fun decodeString(languageEncoding: Boolean, value: ByteArray): String =
        if (languageEncoding) value.decodeToString()
        else value.decodeFromLatin1()

    private fun parseExtraFields(size: Int): ZipExtraFieldContainer {
        val extraFields = ZipExtraFieldContainer.empty()
        if (size == 0) return extraFields
        val extraBuffer = Buffer()
        extraBuffer.write(buffer.readByteArray(size))
        while (extraBuffer.size > 0L) {
            extraFields += ZipExtraFieldEntry.decode(extraBuffer)
        }
        return extraFields
    }

    private fun parseHeader(): ZipLocalFileHeader? {
        if (!ensureBufferFilled(UInt.SIZE_BYTES.toLong())) return null
        when (val magic = buffer.readUIntLeFast()) {
            ZipConstants.LOCAL_FILE_HEADER_MAGIC -> Unit
            ZipConstants.CENTRAL_FILE_HEADER_MAGIC, ZipConstants.END_OF_CENTRAL_DIRECTORY_MAGIC, ZipConstants.ZIP64_END_OF_CENTRAL_DIRECTORY_MAGIC, ZipConstants.ZIP64_END_OF_CENTRAL_DIRECTORY_LOCATOR_MAGIC -> return null
            else -> check(false) {
                "Invalid ZIP local file header magic, expected 0x${ZipConstants.LOCAL_FILE_HEADER_MAGIC.toHexString()} but got 0x${magic.toHexString()}"
            }
        }

        if (!ensureBufferFilled(26L)) return null
        buffer.skip(UShort.SIZE_BYTES.toLong()) // Version needed to extract
        val gpbf = ZipGPBF(buffer.readUShortLeFast())
        val compressionMethod = ZipCompressionMethod.byEncodedValue(buffer.readUShortLeFast())
        val time = buffer.readUShortLeFast().unpackTimeWord()
        val date = buffer.readUShortLeFast().unpackDateWord()
        val checksum = buffer.readUIntLeFast()
        val compressedSize32 = buffer.readUIntLeFast()
        val uncompressedSize32 = buffer.readUIntLeFast()
        val fileNameSize = buffer.readUShortLeFast().toInt()
        val extraFieldSize = buffer.readUShortLeFast().toInt()
        if (!ensureBufferFilled((fileNameSize + extraFieldSize).toLong())) return null

        val name = decodeString(gpbf.languageEncoding, buffer.readByteArray(fileNameSize))
        val extraFields = parseExtraFields(extraFieldSize)
        val zip64 = extraFields.asSequence()
            .map(ZipExtraFieldEntry::data)
            .filterIsInstance<ZipExtraFieldEntryData.Zip64>()
            .firstOrNull()
        val compressedSize = when {
            compressedSize32 == ZipConstants.ZIP64_EXTENDED_FIELD_MARKER && zip64 != null -> zip64.compressedSize.toLong()
            else -> compressedSize32.toLong()
        }
        val uncompressedSize = when {
            uncompressedSize32 == ZipConstants.ZIP64_EXTENDED_FIELD_MARKER && zip64 != null -> zip64.uncompressedSize.toLong()
            else -> uncompressedSize32.toLong()
        }
        val modificationTime = LocalDateTime(date, time).toInstant(TimeZone.currentSystemDefault())
        return ZipLocalFileHeader( // @formatter:off
            entry = ZipEntry(
                modificationTime = modificationTime,
                name = name,
                extraFields = extraFields,
                compressionMethod = compressionMethod,
                gpbf = gpbf
            ),
            checksum = checksum,
            compressedSize = compressedSize,
            uncompressedSize = uncompressedSize
        ) // @formatter:on
    }

    private fun parseDataDescriptor(isZip64: Boolean): ZipDataDescriptor? {
        if (!ensureBufferFilled(UInt.SIZE_BYTES.toLong())) return null
        val first = buffer.readUIntLeFast()
        val checksum = if (first == ZipConstants.DATA_DESCRIPTOR_MAGIC) {
            if (!ensureBufferFilled(UInt.SIZE_BYTES.toLong())) return null
            buffer.readUIntLeFast()
        }
        else first

        if (isZip64) {
            if (!ensureBufferFilled((ULong.SIZE_BYTES * 2).toLong())) return null
            return ZipDataDescriptor(
                checksum = checksum,
                compressedSize = buffer.readULongLe().toLong(),
                uncompressedSize = buffer.readULongLe().toLong()
            )
        }
        if (!ensureBufferFilled((UInt.SIZE_BYTES * 2).toLong())) return null
        return ZipDataDescriptor(
            checksum = checksum,
            compressedSize = buffer.readUIntLeFast().toLong(),
            uncompressedSize = buffer.readUIntLeFast().toLong()
        )
    }

    private fun checkChecksum(expected: UInt, actual: UInt) {
        if (expected != actual) throw InvalidChecksumException(expected, actual)
    }

    private inline fun extractStoredData(
        header: ZipLocalFileHeader, callback: UnarchiverEntryCallback<ZipEntry>
    ): UInt? {
        check(!header.entry.gpbf.omitChecksumAndSizes) { "ZIP stored entries with data descriptors are not supported" }
        if (!ensureBufferFilled(header.compressedSize)) return null
        decompressionBuffer.clear()
        crc32.reset()
        callback(header.entry, decompressionBuffer) {
            if (buffer.size == 0L) return@callback false
            val byteCount = buffer.size.coerceAtMost(Decompressor.DEFAULT_BUFFER_SIZE.toLong())
            buffer.readAtMostTo(decompressionBuffer, byteCount)
            crc32.round(decompressionBuffer.peek(), decompressionBuffer.size)
            true
        }
        return crc32.finalize()
    }

    private inline fun decompressData( // @formatter:off
        method: ZipCompressionMethod,
        header: ZipLocalFileHeader,
        callback: UnarchiverEntryCallback<ZipEntry>
    ): UInt? { // @formatter:on
        val decompressor = decompressors[method]
            ?: throw UnsupportedCompressionMethodException("No decompressor specified for ZIP compression method $method")
        decompressor.reset()
        val compressedSize = if (header.entry.gpbf.omitChecksumAndSizes) {
            Inflater.computeCompressedSize(source.peek())
        }
        else header.compressedSize
        if (!ensureBufferFilled(compressedSize)) return null
        decompressionBuffer.clear()
        crc32.reset()
        buffer.decompressingSource( // @formatter:off
            decompressor = decompressor,
            isSourceOwned = false,
            isDecompressorOwned = false
        ).use { decompressingSource -> // @formatter:on
            callback(header.entry, decompressionBuffer) {
                val result = decompressingSource.readAtMostTo(
                    decompressionBuffer, Decompressor.DEFAULT_BUFFER_SIZE.toLong()
                )
                if (result != -1L) crc32.round(decompressionBuffer.peek(), decompressionBuffer.size)
                result != -1L
            }
        }
        return crc32.finalize()
    }

    private inline fun extractData(header: ZipLocalFileHeader, callback: UnarchiverEntryCallback<ZipEntry>): UInt? =
        when (val method = header.entry.compressionMethod) {
            ZipCompressionMethod.NONE -> extractStoredData(header, callback)
            else -> decompressData(method, header, callback)
        }

    override fun forEachEntry(callback: UnarchiverEntryCallback<ZipEntry>) {
        while (true) {
            buffer.clear()
            decompressionBuffer.clear()
            val header = parseHeader() ?: break
            val checksum = extractData(header, callback) ?: break
            if (header.entry.gpbf.omitChecksumAndSizes) {
                val descriptor = parseDataDescriptor(header.entry.isZip64) ?: break
                checkChecksum(descriptor.checksum, checksum)
            }
            else checkChecksum(header.checksum, checksum)
        }
    }

    override fun close() {
        if (isClosed) return
        if (isSourceOwned) source.close()
        if (areDecompressorsOwned) decompressors.values.forEach(AutoCloseable::close)
        isClosed = true
    }
}

@ExperimentalCompressionApi
fun RawSource.unzip( // @formatter:off
    decompressors: Map<ZipCompressionMethod, Decompressor> = mapOf(ZipCompressionMethod.DEFLATE to Inflater()),
    isSourceOwned: Boolean = true,
    areDecompressorsOwned: Boolean = true
): Unarchiver<ZipEntry, ZipCompressionMethod> =
    ZipUnarchiver(buffered(), decompressors, isSourceOwned, areDecompressorsOwned) // @formatter:on

@ExperimentalCompressionApi
fun Source.unzip( // @formatter:off
    decompressors: Map<ZipCompressionMethod, Decompressor> = mapOf(ZipCompressionMethod.DEFLATE to Inflater()),
    isSourceOwned: Boolean = true,
    areDecompressorsOwned: Boolean = true
): Unarchiver<ZipEntry, ZipCompressionMethod> =
    ZipUnarchiver(this, decompressors, isSourceOwned, areDecompressorsOwned) // @formatter:on