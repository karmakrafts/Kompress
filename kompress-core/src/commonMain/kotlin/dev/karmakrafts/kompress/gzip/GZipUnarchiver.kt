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

import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.archiver.Unarchiver
import dev.karmakrafts.kompress.archiver.UnarchiverEntryCallback
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.readUByte
import kotlinx.io.readUIntLe
import kotlinx.io.readUShort
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

    private fun ensureBufferFilled(): Boolean = source.readAtMostTo(buffer, CHUNK_SIZE.toLong()) != -1L

    override fun forEachEntry(callback: UnarchiverEntryCallback<GZipEntry>) {
        ensureBufferFilled(10L) // Ensure mandatory header fields are readable
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
        val entry = GZipEntry( // @formatter:off
            modificationTime = modificationTime,
            os = os,
            isText = flags.ftext,
            name = null, // TODO
            comment = null, // TODO
            extraField = null // TODO
        ) // @formatter:on
        callback(entry, buffer, ::ensureBufferFilled)
    }

    override fun close() {
        source.close()
        decompressor.close()
        buffer.clear()
    }
}

fun RawSource.ungzip( // @formatter:off
    inflater: Inflater = Inflater()
): Unarchiver<GZipEntry, Inflater> = GZipUnarchiver(this, inflater) // @formatter:on