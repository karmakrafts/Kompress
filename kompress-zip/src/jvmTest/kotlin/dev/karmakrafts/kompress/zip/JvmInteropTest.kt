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

import dev.karmakrafts.kompress.ExperimentalCompressionApi
import kotlinx.io.Buffer
import kotlinx.io.asInputStream
import kotlinx.io.asSource
import kotlinx.io.readByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.util.zip.ZipEntry as JvmZipEntry

@OptIn(ExperimentalCompressionApi::class)
class JvmInteropTest {
    @Test
    fun `kompress zip archiver to jvm zip input stream`() {
        val data = Random(42).nextBytes(1024 * 1024)
        val outputBuffer = Buffer()

        outputBuffer.zip().use { archiver ->
            archiver.appendEntry("test.bin") { sink ->
                sink.write(data)
                false
            }
        }

        val compressedData = outputBuffer.readByteArray()
        ZipInputStream(ByteArrayInputStream(compressedData)).use { zipInputStream ->
            val entry = zipInputStream.nextEntry

            assertNotNull(entry)
            assertEquals("test.bin", entry.name)
            val decompressedData = zipInputStream.readAllBytes()

            assertContentEquals(data, decompressedData)
        }
    }

    @Test
    fun `multiple entries kompress to jvm`() {
        val data1 = "Hello".encodeToByteArray()
        val data2 = "World".encodeToByteArray()
        val outputBuffer = Buffer()

        outputBuffer.zip().use { archiver ->
            archiver.appendEntry("1.txt") { it.write(data1); false }
            archiver.appendEntry("2.txt") { it.write(data2); false }
        }

        ZipInputStream(outputBuffer.asInputStream()).use { zipInputStream ->
            val entry1 = zipInputStream.nextEntry
            assertNotNull(entry1)
            assertEquals("1.txt", entry1.name)
            assertContentEquals(data1, zipInputStream.readAllBytes())

            val entry2 = zipInputStream.nextEntry
            assertNotNull(entry2)
            assertEquals("2.txt", entry2.name)
            assertContentEquals(data2, zipInputStream.readAllBytes())
        }
    }

    @Test
    fun `kompress zip archiver to kompress zip unarchiver`() {
        val data1 = Random(42).nextBytes(1024 * 1024)
        val data2 = "Hello ZIP".encodeToByteArray()
        val outputBuffer = Buffer()

        outputBuffer.zip().use { archiver ->
            archiver.appendEntry("1.bin") { it.write(data1); false }
            archiver.appendEntry("2.txt") { it.write(data2); false }
        }

        val entries = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        outputBuffer.unzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, source, fetchMore ->
                entries += entry.name
                fetchMore()
                val content = Buffer()
                var read = source.readAtMostTo(content, 4096)
                while (read != -1L || fetchMore()) {
                    read = source.readAtMostTo(content, 4096)
                }
                contents += content.readByteArray()
            }
        }

        assertEquals(listOf("1.bin", "2.txt"), entries)
        assertContentEquals(data1, contents[0])
        assertContentEquals(data2, contents[1])
    }

    @Test
    fun `jvm zip output stream to kompress zip unarchiver`() {
        val data = Random(42).nextBytes(1024 * 1024)
        val outputStream = ByteArrayOutputStream()

        ZipOutputStream(outputStream).use { zipOutputStream ->
            zipOutputStream.putNextEntry(JvmZipEntry("test.bin"))
            zipOutputStream.write(data)
            zipOutputStream.closeEntry()
        }

        val contents = ArrayList<ByteArray>()
        ByteArrayInputStream(outputStream.toByteArray()).asSource().unzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, source, fetchMore ->
                assertEquals("test.bin", entry.name)
                fetchMore()
                val content = Buffer()
                var read = source.readAtMostTo(content, 4096)
                while (read != -1L || fetchMore()) {
                    read = source.readAtMostTo(content, 4096)
                }
                contents += content.readByteArray()
            }
        }

        assertEquals(1, contents.size)
        assertContentEquals(data, contents.single())
    }
}
