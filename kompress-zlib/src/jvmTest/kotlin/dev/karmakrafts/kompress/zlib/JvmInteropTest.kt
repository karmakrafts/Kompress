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

import dev.karmakrafts.kompress.exception.DataFormatException
import dev.karmakrafts.kompress.exception.InvalidChecksumException
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class JvmInteropTest {
    private fun deflateWithJvm(data: ByteArray, level: Int = Deflater.DEFAULT_COMPRESSION): ByteArray {
        val deflater = Deflater(level, false)
        deflater.setInput(data)
        deflater.finish()

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val bytesWritten = deflater.deflate(buffer)
            output.write(buffer, 0, bytesWritten)
        }
        deflater.end()

        return output.toByteArray()
    }

    private fun inflateWithJvm(compressedData: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater(false)
        inflater.setInput(compressedData)

        val output = ByteArrayOutputStream(expectedSize)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val bytesRead = inflater.inflate(buffer)
            if (bytesRead == 0 && inflater.needsInput()) {
                break
            }
            output.write(buffer, 0, bytesRead)
        }
        inflater.end()

        return output.toByteArray()
    }

    private fun assertZlibEnvelopeMatchesJvm(
        kompressCompressed: ByteArray, jvmCompressed: ByteArray
    ) {
        assertContentEquals(jvmCompressed.copyOfRange(0, 2), kompressCompressed.copyOfRange(0, 2))
        assertContentEquals(
            jvmCompressed.copyOfRange(jvmCompressed.size - 4, jvmCompressed.size),
            kompressCompressed.copyOfRange(kompressCompressed.size - 4, kompressCompressed.size)
        )
    }

    @Test
    fun `zlib compressor round trip against jvm deflater`() {
        val data = "Hello, World! This is a zlib interoperability test.".encodeToByteArray()

        val kompressCompressed = ZlibCompressor.compress(data)
        val jvmCompressed = deflateWithJvm(data)

        assertZlibEnvelopeMatchesJvm(kompressCompressed, jvmCompressed)

        val jvmRoundTrip = inflateWithJvm(jvmCompressed, data.size)
        val kompressRoundTrip = inflateWithJvm(kompressCompressed, data.size)

        assertContentEquals(data, jvmRoundTrip)
        assertContentEquals(data, kompressRoundTrip)
        assertContentEquals(data, ZlibDecompressor.decompress(jvmCompressed))
        assertContentEquals(data, ZlibDecompressor.decompress(kompressCompressed))
    }

    @Test
    fun `large zlib compressor round trip against jvm deflater`() {
        val data = Random(42).nextBytes(1024 * 1024)

        val kompressCompressed = ZlibCompressor.compress(data)
        val jvmCompressed = deflateWithJvm(data)

        assertZlibEnvelopeMatchesJvm(kompressCompressed, jvmCompressed)

        val jvmRoundTrip = inflateWithJvm(jvmCompressed, data.size)
        val kompressRoundTrip = inflateWithJvm(kompressCompressed, data.size)

        assertContentEquals(data, jvmRoundTrip)
        assertContentEquals(data, kompressRoundTrip)
        assertContentEquals(data, ZlibDecompressor.decompress(jvmCompressed))
        assertContentEquals(data, ZlibDecompressor.decompress(kompressCompressed))
    }

    @Test
    fun `empty zlib stream round trips through decompressor`() {
        val data = ByteArray(0)
        val jvmCompressed = deflateWithJvm(data)

        assertContentEquals(data, ZlibDecompressor.decompress(jvmCompressed))
    }

    @Test
    fun `zlib decompressor rejects invalid header check bits`() {
        val compressedData = deflateWithJvm("invalid header".encodeToByteArray()).copyOf()
        compressedData[1] = (compressedData[1].toInt() xor 1).toByte()

        assertFailsWith<DataFormatException> {
            ZlibDecompressor.decompress(compressedData)
        }
    }

    @Test
    fun `zlib decompressor rejects invalid checksum`() {
        val compressedData = deflateWithJvm("invalid checksum".encodeToByteArray()).copyOf()
        compressedData[compressedData.lastIndex] = (compressedData[compressedData.lastIndex].toInt() xor 1).toByte()

        assertFailsWith<InvalidChecksumException> {
            ZlibDecompressor.decompress(compressedData)
        }
    }
}