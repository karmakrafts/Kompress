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

package dev.karmakrafts.kompress.zlib

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Reproducer test for https://github.com/karmakrafts/Kompress/issues/5
 */
class GH5Test {
    private val zlibTestData: Map<String, ByteArray> = mapOf(
        "I love Kotlin!" to byteArrayOf(
            0x78.toByte(), 0x9c.toByte(), 0xf3.toByte(), 0x54.toByte(), 0xc8.toByte(), 0xc9.toByte(),
            0x2f.toByte(), 0x4b.toByte(), 0x55.toByte(), 0xf0.toByte(), 0xce.toByte(), 0x2f.toByte(),
            0xc9.toByte(), 0xc9.toByte(), 0xcc.toByte(), 0x53.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x23.toByte(), 0x7d.toByte(), 0x04.toByte(), 0xd2.toByte()
        ),
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit." to byteArrayOf(
            0x78.toByte(), 0x9c.toByte(), 0x05.toByte(), 0xc1.toByte(), 0x81.toByte(), 0x09.toByte(),
            0x40.toByte(), 0x21.toByte(), 0x08.toByte(), 0x05.toByte(), 0xc0.toByte(), 0x55.toByte(),
            0xde.toByte(), 0x00.toByte(), 0xd1.toByte(), 0x24.toByte(), 0x7f.toByte(), 0x89.toByte(),
            0x30.toByte(), 0x89.toByte(), 0x07.toByte(), 0x99.toByte(), 0xa1.toByte(), 0xb6.toByte(),
            0xFF.toByte(), 0xbf.toByte(), 0xfb.toByte(), 0x3c.toByte(), 0xd4.toByte(), 0xc0.toByte(),
            0x9b.toByte(), 0xcf.toByte(), 0x30.toByte(), 0x7d.toByte(), 0x7b.toByte(), 0x20.toByte(),
            0x59.toByte(), 0x18.toByte(), 0xa6.toByte(), 0xd5.toByte(), 0x20.toByte(), 0x7e.toByte(),
            0x52.toByte(), 0xa5.toByte(), 0xb4.toByte(), 0x5e.toByte(), 0x60.toByte(), 0x4c.toByte(),
            0x5e.toByte(), 0xa6.toByte(), 0xf0.toByte(), 0x2c.toByte(), 0xe8.toByte(), 0x66.toByte(),
            0xf5.toByte(), 0x1f.toByte(), 0x55.toByte(), 0x03.toByte(), 0x14.toByte(), 0xf7.toByte()
        )
    )

    @Test
    fun testCompress() {
        for (entry in zlibTestData)
            assertContentEquals(
                expected = entry.value,
                actual = ZlibCompressor.compress(entry.key.encodeToByteArray()),
                message = "Failure to compress: ${entry.key}"
            )
    }

    @Test
    fun testDecompress() {

        for (entry in zlibTestData)
            assertEquals(
                expected = entry.key,
                actual = ZlibDecompressor.decompress(entry.value).decodeToString()
            )
    }
}