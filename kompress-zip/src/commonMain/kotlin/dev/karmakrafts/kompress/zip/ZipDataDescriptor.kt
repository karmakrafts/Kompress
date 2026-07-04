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
 * Data descriptor that follows entry data when checksum and size fields are deferred.
 *
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.3.9.
 *
 * @property checksum CRC-32 checksum of the entry payload.
 * @property compressedSize Number of bytes used by the compressed payload.
 * @property uncompressedSize Number of bytes produced after decompression.
 */
@ExperimentalCompressionApi
data class ZipDataDescriptor( // @formatter:off
    val checksum: UInt,
    val compressedSize: Long,
    val uncompressedSize: Long
) // @formatter:on