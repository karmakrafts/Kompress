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

package dev.karmakrafts.kompress.crc

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CRC32Test {
    private companion object {
        const val CUSTOM_POLYNOMIAL: UInt = 0x82F63B78U
        const val CUSTOM_INITIAL_VALUE: UInt = 0x12345678U

        val CHECK_DATA: ByteArray = "123456789".encodeToByteArray()
        val BULK_DATA: ByteArray = ByteArray(4096 + 37) { index ->
            ((index * 31 + index / 3) and 0xFF).toByte()
        }

        fun expectedChecksum(
            data: ByteArray,
            polynomial: UInt = CRC32.DEFAULT_POLYNOMIAL,
            initialValue: UInt = CRC32.DEFAULT_INITIAL_VALUE
        ): UInt {
            var value = initialValue.toInt()
            for (byte in data) {
                value = value xor (byte.toInt() and 0xFF)
                repeat(Byte.SIZE_BITS) {
                    value = when {
                        value and 1 != 0 -> (value ushr 1) xor polynomial.toInt()
                        else -> value ushr 1
                    }
                }
            }
            return value.toUInt().inv()
        }
    }

    @Test
    fun `factory creates CRC32 with default parameters`() {
        val crc = CRC32()

        assertEquals(CRC32.DEFAULT_POLYNOMIAL, crc.polynomial)
        assertEquals(CRC32.DEFAULT_INITIAL_VALUE, crc.initialValue)
        assertEquals(0U, crc.finalize())
    }

    @Test
    fun `factory accepts custom parameters`() {
        val crc = CRC32(CUSTOM_POLYNOMIAL, CUSTOM_INITIAL_VALUE)

        assertEquals(CUSTOM_POLYNOMIAL, crc.polynomial)
        assertEquals(CUSTOM_INITIAL_VALUE, crc.initialValue)
        crc.round(CHECK_DATA)
        assertEquals(expectedChecksum(CHECK_DATA, CUSTOM_POLYNOMIAL, CUSTOM_INITIAL_VALUE), crc.finalize())
    }

    @Test
    fun `single byte rounds produce known checksum`() {
        val crc = CRC32()

        for (byte in CHECK_DATA) {
            crc.round(byte)
        }

        assertEquals(0xCBF43926U, crc.finalize())
        assertEquals(0xCBF43926U, crc.finalize())
    }

    @Test
    fun `bulk rounds match single byte rounds for all slicing paths`() {
        for (size in listOf(0, 1, 7, 8, 9, 15, 16, 17, 31, 32, 4096 + 37)) {
            val data = BULK_DATA.copyOf(size)
            val bulkCrc = CRC32()
            val singleByteCrc = CRC32()

            bulkCrc.round(data)
            for (byte in data) {
                singleByteCrc.round(byte)
            }

            assertEquals(expectedChecksum(data), bulkCrc.finalize())
            assertEquals(singleByteCrc.finalize(), bulkCrc.finalize())
        }
    }

    @Test
    fun `bulk round honors offset and size`() {
        val prefix = byteArrayOf(0x55, 0x66, 0x77)
        val suffix = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val paddedData = prefix + CHECK_DATA + suffix
        val crc = CRC32()

        crc.round(paddedData, offset = prefix.size, size = CHECK_DATA.size)

        assertEquals(0xCBF43926U, crc.finalize())
    }

    @Test
    fun `reset restores initial state`() {
        val crc = CRC32()

        crc.round(CHECK_DATA)
        assertEquals(0xCBF43926U, crc.finalize())
        crc.reset()
        assertEquals(0U, crc.finalize())
        crc.round(byteArrayOf())
        assertEquals(0U, crc.finalize())
        crc.round(CHECK_DATA)
        assertEquals(0xCBF43926U, crc.finalize())
    }

    @Test
    fun `once calculates byte array checksum`() {
        val crc = CRC32()

        assertEquals(0xCBF43926U, crc.once(CHECK_DATA))
        assertEquals(0xCBF43926U, crc.finalize())
    }

    @Test
    fun `source round consumes requested bytes`() {
        val prefix = CHECK_DATA
        val suffix = "remaining".encodeToByteArray()
        val source = Buffer().apply { write(prefix + suffix) }
        val crc = CRC32()

        crc.round(source, prefix.size.toLong())

        assertEquals(0xCBF43926U, crc.finalize())
        assertContentEquals(suffix, source.readByteArray())
    }

    @Test
    fun `source once calculates checksum for requested bytes`() {
        val source = Buffer().apply { write(BULK_DATA) }
        val size = 4096 + 17

        assertEquals(expectedChecksum(BULK_DATA.copyOf(size)), CRC32().once(source, size.toLong()))
        assertContentEquals(BULK_DATA.copyOfRange(size, BULK_DATA.size), source.readByteArray())
    }
}