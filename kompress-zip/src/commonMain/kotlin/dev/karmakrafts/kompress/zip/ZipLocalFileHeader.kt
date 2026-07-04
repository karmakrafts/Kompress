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

/**
 * Parsed ZIP local file header together with resolved size fields.
 *
 * @property entry Logical entry metadata stored in the local header.
 * @property checksum CRC-32 checksum stored in the header or descriptor.
 * @property compressedSize Number of compressed bytes for the entry payload.
 * @property uncompressedSize Number of bytes expected after decompression.
 */
@ExperimentalCompressionApi
data class ZipLocalFileHeader( // @formatter:off
    val entry: ZipEntry,
    val checksum: UInt,
    val compressedSize: Long,
    val uncompressedSize: Long
) // @formatter:on