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

package dev.karmakrafts.kompress.deflate

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeShortLe

internal const val STORED_DATA_SIZE: Int = 1024 * 1024 // 1MiB

private const val MAX_STORED_BLOCK_SIZE: Int = 0xFFFF

/**
 * Encodes the given data as a chain of raw DEFLATE stored blocks.
 *
 * [Deflater] only ever emits static and dynamic blocks, so a stored stream has to be
 * assembled by hand to exercise the BTYPE = 00 path of an inflater.
 *
 * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.4.
 */
internal fun storedBlocks(data: ByteArray): ByteArray {
    val buffer = Buffer()
    var offset = 0
    do {
        val length = minOf(MAX_STORED_BLOCK_SIZE, data.size - offset)
        val isFinalBlock = offset + length >= data.size
        // RFC1951 3.2.4: BFINAL followed by BTYPE = 00, padded up to the next byte boundary.
        buffer.writeByte(if (isFinalBlock) 1 else 0)
        // RFC1951 3.2.4: LEN and NLEN are little endian, NLEN being the one's complement of LEN.
        buffer.writeShortLe(length.toShort())
        buffer.writeShortLe((length xor MAX_STORED_BLOCK_SIZE).toShort())
        buffer.write(data, offset, offset + length)
        offset += length
    }
    while (offset < data.size)
    return buffer.readByteArray()
}

/**
 * The stored block equivalent of the payload every other inflate benchmark decompresses.
 */
internal fun storedBenchmarkData(): ByteArray = storedBlocks(ByteArray(STORED_DATA_SIZE) { 1 })
