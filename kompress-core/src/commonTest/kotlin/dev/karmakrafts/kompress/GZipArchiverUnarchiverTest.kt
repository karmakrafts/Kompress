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

import dev.karmakrafts.kompress.gzip.appendEntry
import dev.karmakrafts.kompress.gzip.gzip
import dev.karmakrafts.kompress.gzip.ungzip
import kotlinx.io.Buffer
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.Test

class GZipArchiverUnarchiverTest {
    @Test
    fun `Archive and unarchive text file`() {
        val inputBuffer = Buffer()
        inputBuffer.writeString("HELLO, WORLD!")
        val outputBuffer = Buffer()
        // Archive the file
        outputBuffer.gzip().use { archiver ->
            archiver.appendEntry("test.txt", source = inputBuffer)
        }
        // Unarchive the file
        outputBuffer.ungzip().use { unarchiver ->
            unarchiver.forEachEntry { entry, source, fetchMore ->
                println(entry)
                val buffer = Buffer()
                while (fetchMore()) buffer.transferFrom(source)
                println("Value: ${buffer.readString()}")
            }
        }
    }
}