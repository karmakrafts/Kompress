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
import dev.karmakrafts.kompress.compressing
import dev.karmakrafts.kompress.crc32
import dev.karmakrafts.kompress.util.writeZeroTerminatedString
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.writeUByte
import kotlinx.io.writeUInt
import kotlinx.io.writeUShort

class GZipArchiver( // @formatter:off
    val sink: RawSink,
    val deflater: Deflater = Deflater()
) : AutoCloseable { // @formatter:on
    private val buffer: Buffer = Buffer()

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
     * end of page 7.
     */
    private fun getCurrentXFL(): UByte = when (deflater.level) {
        GZipConstants.MIN_COMPRESSION -> GZipConstants.XFL_MIN_COMPRESSION
        GZipConstants.MAX_COMPRESSION -> GZipConstants.XFL_MAX_COMPRESSION
        else -> error("Unsupported GZip XFL")
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    private fun appendHeader(entry: GZipEntry, flags: GZipEntryFlags) {
        buffer.writeUShort(GZipConstants.MAGIC)
        buffer.writeUByte(GZipCompressionMethod.DEFLATE.encodedValue)
        buffer.writeUByte(flags.value)
        buffer.writeUInt(entry.modificationTime.epochSeconds.toUInt())
        buffer.writeUByte(getCurrentXFL())
        buffer.writeUByte(entry.os.encodedValue)
    }

    /**
     * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.
     * start of page 5.
     */
    fun appendEntry(entry: GZipEntry): RawSink {
        val flags = entry.computeFlags()
        appendHeader(entry, flags)
        // Write extra field if present
        entry.extraField?.let { extraField ->
            check(extraField.size.toUShort() <= UShort.MAX_VALUE) { "Extra field size exceeds GZip maximum" }
            buffer.writeUShort(extraField.size.toUShort())
            buffer.write(extraField)
        }
        // Write original file name if present
        entry.name?.let(buffer::writeZeroTerminatedString)
        // Write file comment if present
        entry.comment?.let(buffer::writeZeroTerminatedString)
        // Compute and write entry header CRC16 sum if flag bit is set
        if (flags.fhcrc) {
            // HCRC is two least significant bytes of header CRC32 up until self
            val crc32 = buffer.peek().crc32(buffer.size.toInt())
            val crc16 = (crc32 and 0xFFFFU).toUShort()
            buffer.writeUShort(crc16)
        }
        // Flush the entry header into the sink
        sink.write(buffer, buffer.size)
        buffer.clear()
        // Append the compressed data block
        return sink.compressing(deflater)
    }

    override fun close() {
        deflater.close()
    }
}