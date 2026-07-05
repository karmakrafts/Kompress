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

package dev.karmakrafts.kompress.zlib

import dev.karmakrafts.kompress.deflate.Deflater
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZlibApiExtensionsTest {
    private companion object {
        fun readAll(source: RawSource): ByteArray {
            val output = Buffer()
            output.transferFrom(source)
            return output.readByteArray()
        }
    }

    private class RecordingRawSink : RawSink {
        val buffer: Buffer = Buffer()

        override fun write(source: Buffer, byteCount: Long) {
            buffer.write(source, byteCount)
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    @Test
    fun `RawSource zlibSource overload writes selected zlib header and compressed payload`() {
        val payload = "source-compression".encodeToByteArray()
        val cmf = ZlibCMF(windowSize = 4096)
        val flags = ZlibFlags(level = ZlibCompressionLevel.MAXIMUM)
        val compressedBytes = Buffer().also { compressedBuffer ->
            val source: RawSource = Buffer().apply {
                write(payload)
            }
            source.zlibSource(
                level = Deflater.MAX_LEVEL, cmf = cmf, flags = flags, bufferSize = 97
            ).use { compressedSource ->
                compressedBuffer.transferFrom(compressedSource)
            }
        }.readByteArray()

        assertEquals(cmf.value, compressedBytes[0].toUByte())
        assertEquals(flags.withCheckBits(cmf), compressedBytes[1].toUByte())
        assertContentEquals(payload, ZlibDecompressor.decompress(compressedBytes, bufferSize = 89))
    }

    @Test
    fun `RawSink zlibSink overload compresses written bytes`() {
        val payload = "compress-through-sink".encodeToByteArray()
        val recordingSink = RecordingRawSink()
        recordingSink.zlibSink(level = Deflater.DEFAULT_LEVEL, bufferSize = 83).use { sink ->
            val source = Buffer().apply {
                write(payload)
            }
            sink.write(source, source.size)
        }

        val compressedBytes = recordingSink.buffer.readByteArray()
        val decompressedBytes = ZlibDecompressor.decompress(compressedBytes, bufferSize = 91)

        assertContentEquals(payload, decompressedBytes)
    }

    @Test
    fun `RawSource unzlibSource overload decompresses compressed bytes`() {
        val payload = "decompress-through-source".encodeToByteArray()
        val compressedBytes = ZlibCompressor.compress(payload, level = Deflater.DEFAULT_LEVEL, bufferSize = 79)
        val source: RawSource = Buffer().apply {
            write(compressedBytes)
        }

        val decompressedBytes = source.unzlibSource(bufferSize = 71).use { decompressedSource ->
            readAll(decompressedSource)
        }

        assertContentEquals(payload, decompressedBytes)
    }

    @Test
    fun `RawSink unzlibSink overload decompresses bytes into underlying sink`() {
        val payload = "decompress-through-sink".encodeToByteArray()
        val compressedBytes = ZlibCompressor.compress(payload, level = Deflater.DEFAULT_LEVEL, bufferSize = 67)
        val recordingSink = RecordingRawSink()

        recordingSink.unzlibSink(bufferSize = 61).use { sink ->
            val source = Buffer().apply {
                write(compressedBytes)
            }
            sink.write(source, source.size)
        }

        assertContentEquals(payload, recordingSink.buffer.readByteArray())
    }
}
