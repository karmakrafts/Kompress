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

package dev.karmakrafts.kompress.deflate

import dev.karmakrafts.kompress.compressingSink
import dev.karmakrafts.kompress.compressingSource
import dev.karmakrafts.kompress.decompressingSink
import dev.karmakrafts.kompress.decompressingSource
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

class JvmInteropTest {
    private fun testKompressToJvm(data: ByteArray) {
        val compressedBuffer = Buffer()
        val deflater = Deflater()
        compressedBuffer.compressingSink(deflater).use { sink ->
            sink.write(Buffer().apply { write(data) }, data.size.toLong())
        }

        val compressedData = compressedBuffer.readByteArray()

        val jInflater = java.util.zip.Inflater(true)
        jInflater.setInput(compressedData)

        val decompressedData = ByteArray(data.size)
        val resultLength = jInflater.inflate(decompressedData)

        assertContentEquals(data, decompressedData.copyOf(resultLength))
        jInflater.end()
    }

    private fun testJvmToKompress(data: ByteArray) {
        val jDeflater = java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true)
        jDeflater.setInput(data)
        jDeflater.finish()

        val compressedData = ByteArray(data.size * 2)
        val compressedLength = jDeflater.deflate(compressedData)
        jDeflater.end()

        val source = Buffer().apply { write(compressedData, 0, compressedLength) }
        val inflater = Inflater()
        val decompressedBuffer = Buffer()
        source.decompressingSource(inflater).use { decompressingSource ->
            decompressedBuffer.transferFrom(decompressingSource)
        }

        assertContentEquals(data, decompressedBuffer.readByteArray())
    }

    private fun testKompressSourceToJvm(data: ByteArray) {
        val source = Buffer().apply { write(data) }
        val deflater = Deflater()
        val compressedBuffer = Buffer()
        source.compressingSource(deflater).use { compressingSource ->
            compressedBuffer.transferFrom(compressingSource)
        }

        val compressedData = compressedBuffer.readByteArray()

        val jInflater = java.util.zip.Inflater(true)
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

    private fun testJvmToKompressSink(data: ByteArray) {
        val jDeflater = java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true)
        jDeflater.setInput(data)
        jDeflater.finish()

        val compressedData = ByteArray(data.size * 2)
        val compressedLength = jDeflater.deflate(compressedData)
        jDeflater.end()

        val decompressedBuffer = Buffer()
        val inflater = Inflater()
        decompressedBuffer.decompressingSink(inflater).use { sink ->
            sink.write(Buffer().apply { write(compressedData, 0, compressedLength) }, compressedLength.toLong())
        }

        assertContentEquals(data, decompressedBuffer.readByteArray())
    }

    @Test
    fun `kompress deflater to jvm inflater raw`() {
        testKompressToJvm("Hello, World! This is a test of the kompress deflater against the JVM inflater.".encodeToByteArray())
    }

    @Test
    fun `jvm deflater to kompress inflater raw`() {
        testJvmToKompress("Hello, World! This is a test of the JVM deflater against the kompress inflater.".encodeToByteArray())
    }

    @Test
    fun `large data kompress deflater to jvm inflater raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testKompressToJvm(data)
    }

    @Test
    fun `large data jvm deflater to kompress inflater raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testJvmToKompress(data)
    }

    @Test
    fun `kompress deflater source to jvm inflater raw`() {
        testKompressSourceToJvm("Hello, World! This is a test of the kompress deflating source against the JVM inflater.".encodeToByteArray())
    }

    @Test
    fun `jvm deflater to kompress decompressing sink raw`() {
        testJvmToKompressSink("Hello, World! This is a test of the JVM deflater against the kompress decompressing sink.".encodeToByteArray())
    }

    @Test
    fun `large data kompress deflater source to jvm inflater raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testKompressSourceToJvm(data)
    }

    @Test
    fun `large data jvm deflater to kompress decompressing sink raw`() {
        val data = Random(42).nextBytes(1024 * 1024)
        testJvmToKompressSink(data)
    }
}