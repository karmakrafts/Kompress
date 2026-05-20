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

package dev.karmakrafts.kompress

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import java.util.zip.Deflater as JDeflater
import java.util.zip.Inflater as JInflater

class JvmInteropTest {
    private fun testKompressToJvm(raw: Boolean, data: ByteArray) {
        val compressedBuffer = Buffer()
        val deflater = Deflater(raw = raw)
        compressedBuffer.compressingSink(deflater).use { sink ->
            sink.write(Buffer().apply { write(data) }, data.size.toLong())
        }

        val compressedData = compressedBuffer.readByteArray()

        val jInflater = JInflater(raw)
        jInflater.setInput(compressedData)

        val decompressedData = ByteArray(data.size)
        val resultLength = jInflater.inflate(decompressedData)

        assertContentEquals(data, decompressedData.copyOf(resultLength))
        jInflater.end()
    }

    private fun testJvmToKompress(raw: Boolean, data: ByteArray) {
        val jDeflater = JDeflater(JDeflater.DEFAULT_COMPRESSION, raw)
        jDeflater.setInput(data)
        jDeflater.finish()

        val compressedData = ByteArray(data.size * 2)
        val compressedLength = jDeflater.deflate(compressedData)
        jDeflater.end()

        val source = Buffer().apply { write(compressedData, 0, compressedLength) }
        val inflater = Inflater(raw = raw)
        val decompressedBuffer = Buffer()
        source.decompressingSource(inflater).use { decompressingSource ->
            decompressedBuffer.transferFrom(decompressingSource)
        }

        assertContentEquals(data, decompressedBuffer.readByteArray())
    }

    private fun testKompressSourceToJvm(raw: Boolean, data: ByteArray) {
        val source = Buffer().apply { write(data) }
        val deflater = Deflater(raw = raw)
        val compressedBuffer = Buffer()
        source.compressingSource(deflater).use { compressingSource ->
            compressedBuffer.transferFrom(compressingSource)
        }

        val compressedData = compressedBuffer.readByteArray()

        val jInflater = JInflater(raw)
        jInflater.setInput(compressedData)

        val decompressedData = ByteArray(data.size)
        var resultLength = 0
        while (!jInflater.finished()) {
            val inflated = jInflater.inflate(decompressedData, resultLength, data.size - resultLength)
            if (inflated == 0) break
            resultLength += inflated
        }

        assertContentEquals(data, decompressedData.copyOf(resultLength))
        jInflater.end()
    }

    private fun testJvmToKompressSink(raw: Boolean, data: ByteArray) {
        val jDeflater = JDeflater(JDeflater.DEFAULT_COMPRESSION, raw)
        jDeflater.setInput(data)
        jDeflater.finish()

        val compressedData = ByteArray(data.size * 2)
        val compressedLength = jDeflater.deflate(compressedData)
        jDeflater.end()

        val decompressedBuffer = Buffer()
        val inflater = Inflater(raw = raw)
        decompressedBuffer.decompressingSink(inflater).use { sink ->
            sink.write(Buffer().apply { write(compressedData, 0, compressedLength) }, compressedLength.toLong())
        }

        assertContentEquals(data, decompressedBuffer.readByteArray())
    }

    @Test
    fun `kompress deflater to jvm inflater raw`() {
        val data = "Hello, World! This is a test of the kompress deflater against the JVM inflater.".encodeToByteArray()
        testKompressToJvm(true, data)
    }

    @Test
    fun `kompress deflater to jvm inflater zlib`() {
        val data = "Hello, World! This is a test of the kompress deflater against the JVM inflater.".encodeToByteArray()
        testKompressToJvm(false, data)
    }

    @Test
    fun `jvm deflater to kompress inflater raw`() {
        val data = "Hello, World! This is a test of the JVM deflater against the kompress inflater.".encodeToByteArray()
        testJvmToKompress(true, data)
    }

    @Test
    fun `jvm deflater to kompress inflater zlib`() {
        val data = "Hello, World! This is a test of the JVM deflater against the kompress inflater.".encodeToByteArray()
        testJvmToKompress(false, data)
    }

    @Test
    fun `large data kompress deflater to jvm inflater raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testKompressToJvm(true, data)
    }

    @Test
    fun `large data jvm deflater to kompress inflater raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testJvmToKompress(true, data)
    }

    @Test
    fun `kompress deflater source to jvm inflater raw`() {
        val data =
            "Hello, World! This is a test of the kompress deflating source against the JVM inflater.".encodeToByteArray()
        testKompressSourceToJvm(true, data)
    }

    @Test
    fun `jvm deflater to kompress decompressing sink raw`() {
        val data =
            "Hello, World! This is a test of the JVM deflater against the kompress decompressing sink.".encodeToByteArray()
        testJvmToKompressSink(true, data)
    }

    @Test
    fun `large data kompress deflater source to jvm inflater raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testKompressSourceToJvm(true, data)
    }

    @Test
    fun `large data jvm deflater to kompress decompressing sink raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testJvmToKompressSink(true, data)
    }

    @Test
    fun `kompress deflater source to jvm inflater zlib`() {
        val data =
            "Hello, World! This is a test of the kompress deflating source against the JVM inflater.".encodeToByteArray()
        testKompressSourceToJvm(false, data)
    }

    @Test
    fun `jvm deflater to kompress decompressing sink zlib`() {
        val data =
            "Hello, World! This is a test of the JVM deflater against the kompress decompressing sink.".encodeToByteArray()
        testJvmToKompressSink(false, data)
    }

    @Test
    fun `large data kompress deflater source to jvm inflater zlib`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testKompressSourceToJvm(false, data)
    }

    @Test
    fun `large data jvm deflater to kompress decompressing sink zlib`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testJvmToKompressSink(false, data)
    }
}
