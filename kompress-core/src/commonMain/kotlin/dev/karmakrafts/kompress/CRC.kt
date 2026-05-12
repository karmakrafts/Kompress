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

import kotlinx.io.Source
import kotlinx.io.readByteArray

/**
 * Compute the CRC32 checksum for the given [data].
 *
 * @param data The data to compute the checksum for.
 * @return The computed CRC32 checksum.
 */
expect fun crc32(data: ByteArray): UInt

/**
 * Compute the CRC32 checksum for the next [size] bytes from this [Source].
 *
 * @param size The number of bytes to read from the source.
 * @return The computed CRC32 checksum.
 */
fun Source.crc32(size: Int): UInt = crc32(readByteArray(size))