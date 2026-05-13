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

import dev.karmakrafts.kompress.Decompressor
import dev.karmakrafts.kompress.Inflater
import dev.karmakrafts.kompress.archiver.Unarchiver
import kotlinx.io.RawSource
import kotlinx.io.Source

private class GZipUnarchiver( // @formatter:off
    override val source: RawSource,
    val inflater: Inflater
) : Unarchiver<GZipEntry> { // @formatter:on
    override val decompressor: Decompressor get() = inflater

    override fun nextEntry(): GZipEntry? {
        TODO("Not yet implemented")
    }

    override fun openEntry(entry: GZipEntry): Source {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}

fun RawSource.ungzip( // @formatter:off
    inflater: Inflater = Inflater()
): Unarchiver<GZipEntry> = GZipUnarchiver(this, inflater) // @formatter:on