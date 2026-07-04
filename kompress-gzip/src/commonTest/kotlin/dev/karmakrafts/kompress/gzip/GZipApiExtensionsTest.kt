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

import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.archive.Archiver
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class GZipApiExtensionsTest {
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
    fun `appendEntry name overload builds GZipEntry and forwards callback`() {
        val archiver = RecordingArchiver()
        val modificationTime = Instant.fromEpochSeconds(42)
        val payload = "abc".encodeToByteArray()

        archiver.appendEntry(
            name = "path/file.txt", modificationTime = modificationTime, comment = "test", isText = true
        ) { sink ->
            sink.write(payload)
            false
        }

        val entry = assertNotNull(archiver.appendedEntry)
        assertEquals("path/file.txt", entry.name)
        assertEquals(modificationTime, entry.modificationTime)
        assertEquals("test", entry.comment)
        assertTrue(entry.isText)
        assertEquals(GZipOs.guessCurrent(), entry.os)
        assertContentEquals(payload, archiver.payload.readByteArray())
        assertEquals(1, archiver.callbackCalls)
    }

    @Test
    fun `appendEntry source overload transfers source bytes into callback sink`() {
        val archiver = RecordingArchiver()
        val payload = "source-data".encodeToByteArray()
        val source = Buffer().apply {
            write(payload)
        }
        val modificationTime = Instant.fromEpochSeconds(7)

        archiver.appendEntry(
            name = "source.bin",
            source = source,
            modificationTime = modificationTime,
            comment = "payload",
            isText = false
        )

        val entry = assertNotNull(archiver.appendedEntry)
        assertEquals("source.bin", entry.name)
        assertEquals(modificationTime, entry.modificationTime)
        assertEquals("payload", entry.comment)
        assertEquals(false, entry.isText)
        assertContentEquals(payload, archiver.payload.readByteArray())
    }

    @Test
    fun `RawSource ungzip overload reads archive from raw source`() {
        val payload = "raw-source-data".encodeToByteArray()
        val archiveBytes = Buffer().apply {
            gzip().use { archiver ->
                archiver.appendEntry("entry.txt") { sink ->
                    sink.write(payload)
                    false
                }
            }
        }.readByteArray()
        val rawSource: RawSource = Buffer().apply {
            write(archiveBytes)
        }

        val names = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        rawSource.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, source, fetchMore ->
                names += assertNotNull(entry.name)
                contents += readEntryBytes(source, fetchMore)
            }
        }

        assertEquals(listOf("entry.txt"), names)
        assertContentEquals(payload, contents.single())
    }

    @Test
    fun `Source ungzip overload reads archive from buffered source`() {
        val payload = "source-data".encodeToByteArray()
        val archiveBytes = Buffer().apply {
            gzip().use { archiver ->
                archiver.appendEntry("entry.txt") { sink ->
                    sink.write(payload)
                    false
                }
            }
        }.readByteArray()
        val source: Source = (Buffer().apply {
            write(archiveBytes)
        } as RawSource).buffered()

        val names = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        source.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, entrySource, fetchMore ->
                names += assertNotNull(entry.name)
                contents += readEntryBytes(entrySource, fetchMore)
            }
        }

        assertEquals(listOf("entry.txt"), names)
        assertContentEquals(payload, contents.single())
    }

    private class RecordingArchiver : Archiver<GZipEntry, GZipCompressionMethod> {
        override val sink: RawSink = Buffer()
        override val compressors: Map<GZipCompressionMethod, Compressor> = emptyMap()

        var appendedEntry: GZipEntry? = null
        var callbackCalls: Int = 0
        val payload: Buffer = Buffer()

        override fun appendEntry(entry: GZipEntry, callback: (Sink) -> Boolean) {
            appendedEntry = entry
            while (true) {
                callbackCalls += 1
                if (!callback(payload)) break
            }
        }

        override fun close() = Unit
    }
}