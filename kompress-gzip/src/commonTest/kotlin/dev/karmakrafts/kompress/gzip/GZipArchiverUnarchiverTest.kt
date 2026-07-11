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

import dev.karmakrafts.kompress.exception.InvalidChecksumException
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GZipArchiverUnarchiverTest {
    private companion object {
        fun readEntryBytes(source: Source, fetchMore: () -> Boolean): ByteArray {
            val buffer = Buffer()
            var read = source.readAtMostTo(buffer, 64)
            while (read != -1L || fetchMore()) {
                read = source.readAtMostTo(buffer, 64)
            }
            return buffer.readByteArray()
        }
    }

    @Test
    fun `archive and unarchive text entry preserves metadata and content`() {
        val payload = "HELLO, WORLD!".encodeToByteArray()
        val expectedEntry = GZipEntry(
            modificationTime = Instant.fromEpochSeconds(42),
            os = GZipOs.UNIX,
            isText = true,
            name = "test.txt",
            comment = "hello",
            extraField = byteArrayOf(0x01, 0x02, 0x03)
        )
        val archiveBuffer = Buffer()

        archiveBuffer.gzip().use { archiver ->
            archiver.appendEntry(expectedEntry) { sink ->
                sink.write(payload)
                false
            }
        }

        val entries = ArrayList<GZipEntry>()
        val contents = ArrayList<ByteArray>()
        archiveBuffer.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, source, fetchMore ->
                entries += entry
                contents += readEntryBytes(source, fetchMore)
            }
        }

        assertEquals(listOf(expectedEntry), entries)
        assertContentEquals(payload, contents.single())
    }

    @Test
    fun `unarchive rejects invalid header checksum`() {
        val archiveBuffer = Buffer()

        archiveBuffer.gzip().use { archiver ->
            archiver.appendEntry("test.txt") { sink ->
                sink.writeString("HELLO, WORLD!")
                false
            }
        }

        val archive = archiveBuffer.readByteArray()
        archive[10] = (archive[10].toInt() xor 1).toByte()
        val corruptedBuffer = Buffer()
        corruptedBuffer.write(archive)

        assertFailsWith<InvalidChecksumException> {
            corruptedBuffer.ungzip().use { unarchiver ->
                unarchiver.forEachEntry { _, source, fetchMore ->
                    readEntryBytes(source, fetchMore)
                }
            }
        }
    }

    @Test
    fun `archive and unarchive multiple text entries preserves order and data`() {
        val inputBuffer1 = Buffer()
        inputBuffer1.writeString("HELLO, WORLD!")
        val inputBuffer2 = Buffer()
        inputBuffer2.writeString("The fox goes yap!")
        val archiveBuffer = Buffer()

        archiveBuffer.gzip().use { archiver ->
            archiver.appendEntry("test1.txt", source = inputBuffer1)
            archiver.appendEntry("test2.txt", source = inputBuffer2)
        }

        val names = ArrayList<String>()
        val contents = ArrayList<String>()
        archiveBuffer.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, source, fetchMore ->
                names += assertNotNull(entry.name)
                contents += readEntryBytes(source, fetchMore).decodeToString()
            }
        }

        assertEquals(listOf("test1.txt", "test2.txt"), names)
        assertEquals(listOf("HELLO, WORLD!", "The fox goes yap!"), contents)
    }

    @Test
    fun `archive and unarchive supports latin-1 names`() {
        val inputBuffer = Buffer()
        inputBuffer.writeString("LATIN-1 test")
        val archiveBuffer = Buffer()
        val latin1Name = "täst-lâtìn1-ÿ.txt"

        archiveBuffer.gzip().use { archiver ->
            archiver.appendEntry(latin1Name, source = inputBuffer)
        }

        archiveBuffer.ungzip().use { unarchiver ->
            var entryFound = false
            unarchiver.forEachEntry { entry, source, fetchMore ->
                if (entry.name == latin1Name) {
                    entryFound = true
                }
                readEntryBytes(source, fetchMore)
            }
            assertTrue(entryFound, "Entry with name $latin1Name should be present")
        }
    }

    @Test
    fun `archive and unarchive empty file keeps zero-length payload`() {
        val inputBuffer = Buffer()
        val archiveBuffer = Buffer()

        archiveBuffer.gzip().use { archiver ->
            archiver.appendEntry("empty.txt", source = inputBuffer)
        }

        archiveBuffer.ungzip().use { unarchiver ->
            var entryFound = false
            unarchiver.forEachEntry { entry, source, fetchMore ->
                if (entry.name == "empty.txt") {
                    entryFound = true
                    assertEquals(0, readEntryBytes(source, fetchMore).size)
                }
            }
            assertTrue(entryFound, "Entry 'empty.txt' should be present")
        }
    }
}