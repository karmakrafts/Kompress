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

import kotlinx.io.Buffer
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.readByteArray
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GZipJvmInteropTest {
    @Test
    fun `kompress gzip archiver to jvm gzip input stream`() {
        val data = Random(42).nextBytes(1024 * 1024)
        val outputBuffer = Buffer()

        outputBuffer.gzip().use { archiver ->
            archiver.appendEntry("test.bin") { sink ->
                sink.write(data)
                false
            }
        }

        val compressedData = outputBuffer.readByteArray()
        val inputStream = GZIPInputStream(ByteArrayInputStream(compressedData))
        val decompressedData = inputStream.readAllBytes()

        assertContentEquals(data, decompressedData)
    }

    @Test
    fun `jvm gzip output stream to kompress gzip unarchiver`() {
        val data = Random(42).nextBytes(1024 * 1024)
        val compressedBuffer = Buffer()

        GZIPOutputStream(compressedBuffer.asOutputStream()).use { it.write(data) }

        var entriesFound = 0
        compressedBuffer.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { _, source, fetchMore ->
                entriesFound++
                val decompressedBuffer = Buffer()
                while (fetchMore()) {
                    decompressedBuffer.transferFrom(source)
                }
                assertContentEquals(data, decompressedBuffer.readByteArray())
            }
        }
        assertEquals(1, entriesFound)
    }

    @Test
    fun `multiple entries kompress to jvm`() {
        val data1 = "Hello".encodeToByteArray()
        val data2 = "World".encodeToByteArray()
        val outputBuffer = Buffer()

        outputBuffer.gzip().use { archiver ->
            archiver.appendEntry("1.txt") { it.write(data1); false }
            archiver.appendEntry("2.txt") { it.write(data2); false }
        }

        val inputStream = GZIPInputStream(outputBuffer.asInputStream())
        val decompressed1 = inputStream.readNBytes(data1.size)
        assertContentEquals(data1, decompressed1)

        val decompressed2 = inputStream.readAllBytes()
        assertContentEquals(data2, decompressed2)
    }

    @Test
    fun `multiple entries jvm to kompress`() {
        val data1 = "Hello".encodeToByteArray()
        val data2 = "World".encodeToByteArray()
        val compressedBuffer = Buffer()

        GZIPOutputStream(compressedBuffer.asOutputStream()).use { it.write(data1) }
        GZIPOutputStream(compressedBuffer.asOutputStream()).use { it.write(data2) }

        val receivedData = mutableListOf<ByteArray>()
        compressedBuffer.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { _, source, fetchMore ->
                val buffer = Buffer()
                while (fetchMore()) buffer.transferFrom(source)
                receivedData.add(buffer.readByteArray())
            }
        }

        assertEquals(2, receivedData.size)
        assertContentEquals(data1, receivedData[0])
        assertContentEquals(data2, receivedData[1])
    }
}
