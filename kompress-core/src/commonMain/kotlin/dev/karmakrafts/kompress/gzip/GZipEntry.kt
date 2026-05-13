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

import dev.karmakrafts.kompress.archiver.DelegateEntry
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlin.time.Instant

class GZipEntry( // @formatter:off
    offset: Long,
    val compressionMethod: GZipCompressionMethod,
    val modificationTime: Instant,
    val os: GZipOs,
    val crc32: UInt,
    val uncompressedSize: UInt,
    val name: String? = null,
    val comment: String? = null,
    val extraField: ByteArray? = null,
    sourceProvider: () -> RawSource,
    sinkProvider: () -> RawSink
) : DelegateEntry(offset, sourceProvider, sinkProvider) // @formatter:on