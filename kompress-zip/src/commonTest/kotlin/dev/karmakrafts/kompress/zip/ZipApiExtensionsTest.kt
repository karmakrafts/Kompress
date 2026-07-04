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

import dev.karmakrafts.kompress.Compressor
import dev.karmakrafts.kompress.ExperimentalCompressionApi
import dev.karmakrafts.kompress.archive.Archiver
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

@OptIn(ExperimentalCompressionApi::class)
class ZipApiExtensionsTest {
    @Test
    fun `appendEntry name overload builds ZipEntry and forwards callback`() {
        val archiver = RecordingArchiver()
        val modificationTime = Instant.fromEpochSeconds(42)
        val payload = "abc".encodeToByteArray()

        archiver.appendEntry(
            name = "path/file.txt", modificationTime = modificationTime, comment = "test"
        ) { sink ->
            sink.write(payload)
            false
        }

        val entry = assertNotNull(archiver.appendedEntry)
        assertEquals("path/file.txt", entry.name)
        assertEquals(modificationTime, entry.modificationTime)
        assertEquals("test", entry.comment)
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

        archiver.appendEntry(
            name = "source.bin", source = source, modificationTime = Instant.fromEpochSeconds(0), comment = null
        )

        val entry = assertNotNull(archiver.appendedEntry)
        assertEquals("source.bin", entry.name)
        assertContentEquals(payload, archiver.payload.readByteArray())
    }

    @Test
    fun `zip and unzip extension functions roundtrip a single entry`() {
        val archiveBuffer = Buffer()
        val payload = "hello zip".encodeToByteArray()
        archiveBuffer.zip().also { archiver ->
            archiver.appendEntry("hello.txt") { sink ->
                sink.write(payload)
                false
            }
            archiver.close()
        }

        val names = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        val unarchiver = archiveBuffer.unzip()
        try {
            unarchiver.forEachEntry { entry, source, fetchMore ->
                names += entry.name
                fetchMore()
                val content = Buffer()
                var read = source.readAtMostTo(content, 64)
                while (read != -1L || fetchMore()) {
                    read = source.readAtMostTo(content, 64)
                }
                contents += content.readByteArray()
            }
        }
        finally {
            unarchiver.close()
        }

        assertEquals(listOf("hello.txt"), names)
        assertContentEquals(payload, contents.single())
    }

    @Test
    fun `RawSource unzip overload reads archive from raw source`() {
        val archiveBuffer = Buffer()
        archiveBuffer.zip().also { archiver ->
            archiver.appendEntry("entry.txt") { sink ->
                sink.write("data".encodeToByteArray())
                false
            }
            archiver.close()
        }

        val rawSource: RawSource = archiveBuffer
        val names = ArrayList<String>()
        val unarchiver = rawSource.unzip()
        try {
            unarchiver.forEachEntry { entry, source, fetchMore ->
                names += entry.name
                fetchMore()
                val drainBuffer = Buffer()
                var read = source.readAtMostTo(drainBuffer, 32)
                while (read != -1L || fetchMore()) {
                    read = source.readAtMostTo(drainBuffer, 32)
                }
            }
        }
        finally {
            unarchiver.close()
        }

        assertEquals(listOf("entry.txt"), names)
    }

    private class RecordingArchiver : Archiver<ZipEntry, ZipCompressionMethod> {
        override val sink: RawSink = Buffer()
        override val compressors: Map<ZipCompressionMethod, Compressor> = emptyMap()

        var appendedEntry: ZipEntry? = null
        var callbackCalls: Int = 0
        val payload: Buffer = Buffer()

        override fun appendEntry(entry: ZipEntry, callback: (Sink) -> Boolean) {
            appendedEntry = entry
            while (true) {
                callbackCalls += 1
                if (!callback(payload)) break
            }
        }

        override fun close() = Unit
    }
}
