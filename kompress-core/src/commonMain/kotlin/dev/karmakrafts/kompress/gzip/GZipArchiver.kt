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

import dev.karmakrafts.kompress.Deflater
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.archiver.Archiver
import dev.karmakrafts.kompress.util.writeZeroTerminatedString
import kotlinx.io.Buffer
import kotlinx.io.writeUByte
import kotlinx.io.writeUInt
import kotlinx.io.writeUShort

class GZipArchiver( // @formatter:off
    val deflater: Deflater = Deflater(),
    val inflater: Inflater = Inflater()
) : Archiver<GZipEntry> { // @formatter:on
    private val entryBuffer: Buffer = Buffer()

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * end of page 7.
     */
    private fun getCurrentXFL(): UByte = when (deflater.level) {
        1 -> 0x04U // Fastest compression
        9 -> 0x02U // Best compression
        else -> error("Unsupported GZip XFL")
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    private fun appendHeader(entry: GZipEntry, flags: GZipEntryFlags) {
        // File magic
        entryBuffer.writeUShort(GZipConstants.MAGIC)
        // For GZip, compression method is always deflate
        entryBuffer.writeUByte(GZipCompressionMethod.DEFLATE.encodedValue)
        // Compute entry flags from entry data
        entryBuffer.writeUByte(flags.value)
        // Write modification time as UNIX timestamp truncated to unsigned 32-bit value
        entryBuffer.writeUInt(entry.modificationTime.epochSeconds.toUInt())
        // Write XFL based on compression level of compressor
        entryBuffer.writeUByte(getCurrentXFL())
        // Write the operating system type
        entryBuffer.writeUByte(entry.os.encodedValue)
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    override fun appendEntry(entry: GZipEntry) {
        val flags = entry.computeFlags()
        appendHeader(entry, flags)
        // Write extra field if present
        entry.extraField?.let { extraField ->
            check(extraField.size.toUShort() <= UShort.MAX_VALUE) { "Extra field size exceeds GZip maximum" }
            entryBuffer.writeUShort(extraField.size.toUShort())
            entryBuffer.write(extraField)
        }
        // Write original file name if present
        entry.name?.let(entryBuffer::writeZeroTerminatedString)
        // Write file comment if present
        entry.comment?.let(entryBuffer::writeZeroTerminatedString)
        // Compute and write entry header CRC16 sum if flag bit is set
        if (flags.fhcrc) {
            // TODO: ...
        }
    }

    override fun nextEntry(): GZipEntry? {
        TODO("Not yet implemented")
    }

    override fun finish() {
        TODO("Not yet implemented")
    }

    override fun close() {
        deflater.close()
        inflater.close()
    }
}