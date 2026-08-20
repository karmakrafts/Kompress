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

import dev.karmakrafts.kompress.exception.DataFormatException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers the stored (BTYPE = 00) block path of [Inflater].
 *
 * [Deflater] only ever emits static and dynamic blocks, so every stored stream used here
 * has to be assembled by hand.
 *
 * See [RFC1951](https://datatracker.ietf.org/doc/html/rfc1951) section 3.2.4.
 */
class StoredBlockInflaterTest {
    private companion object {
        const val MAX_STORED_BLOCK_SIZE: Int = 0xFFFF

        val SMALL_DATA: ByteArray = "The quick brown fox jumps over the lazy dog".encodeToByteArray()
        val LARGE_DATA: ByteArray = ByteArray(150_000) { index -> ((index * 31 + index / 7) and 0xFF).toByte() }

        /** The 64 byte payload the mixed stream below stores, then back-references. */
        val DICTIONARY_DATA: ByteArray = ByteArray(64) { index -> ((index * 37 + 11) and 0xFF).toByte() }

        /**
         * A final fixed-Huffman block holding a single length 64, distance 64 match, produced by
         * zlib with [DICTIONARY_DATA] as its preset dictionary. Appended to a stored block holding
         * [DICTIONARY_DATA] it must inflate to that payload twice, which only works if the stored
         * block populated the sliding window.
         */
        val BACK_REFERENCE_BLOCK: ByteArray = byteArrayOf(
            0xE3.toByte(), 0xA6.toByte(), 0x50, 0x3F, 0x00
        )

        /** Encodes [data] as a chain of stored blocks of at most [blockSize] bytes each. */
        fun storedBlocks( // @formatter:off
            data: ByteArray,
            blockSize: Int = MAX_STORED_BLOCK_SIZE,
            finalBlock: Boolean = true
        ): ByteArray { // @formatter:on
            val buffer = Buffer()
            var offset = 0
            do {
                val length = minOf(blockSize, data.size - offset)
                val isLast = finalBlock && offset + length >= data.size
                buffer.writeByte(if (isLast) 1 else 0)
                buffer.writeByte((length and 0xFF).toByte())
                buffer.writeByte(((length ushr 8) and 0xFF).toByte())
                val inverseLength = length xor MAX_STORED_BLOCK_SIZE
                buffer.writeByte((inverseLength and 0xFF).toByte())
                buffer.writeByte(((inverseLength ushr 8) and 0xFF).toByte())
                buffer.write(data, offset, offset + length)
                offset += length
            }
            while (offset < data.size)
            return buffer.readByteArray()
        }

        /** Feeds [data] to [inflater] in [inputChunk] sized pieces, draining [outputChunk] bytes at a time. */
        fun inflateChunked(data: ByteArray, inputChunk: Int, outputChunk: Int): ByteArray {
            val output = Buffer()
            val chunk = ByteArray(outputChunk)
            Inflater().use { inflater ->
                var offset = 0
                while (offset < data.size) {
                    val size = minOf(inputChunk, data.size - offset)
                    inflater.setInput(data, offset, size)
                    offset += size
                    if (offset == data.size) inflater.finish()
                    while (true) {
                        val read = inflater.decompress(chunk, 0, chunk.size, flush = true)
                        if (read == 0) break
                        output.write(chunk, 0, read)
                    }
                }
                assertTrue(inflater.finished, "inflater did not reach the end of the stream")
            }
            return output.readByteArray()
        }
    }

    @Test
    fun `single stored block round trips`() {
        assertContentEquals(SMALL_DATA, Inflater.decompress(storedBlocks(SMALL_DATA)))
    }

    @Test
    fun `empty stored block round trips`() {
        assertContentEquals(ByteArray(0), Inflater.decompress(storedBlocks(ByteArray(0))))
    }

    @Test
    fun `stored payload spanning multiple blocks round trips`() {
        val compressedData = storedBlocks(LARGE_DATA)
        // 150000 bytes cannot fit a single block, so the encoder must have split it.
        assertEquals(3, (LARGE_DATA.size + MAX_STORED_BLOCK_SIZE - 1) / MAX_STORED_BLOCK_SIZE)
        assertContentEquals(LARGE_DATA, Inflater.decompress(compressedData, bufferSize = 4096))
    }

    @Test
    fun `stored block round trips through small output buffers`() {
        val compressedData = storedBlocks(LARGE_DATA, blockSize = 1000)
        assertContentEquals(LARGE_DATA, Inflater.decompress(compressedData, bufferSize = 7))
    }

    @Test
    fun `stored block round trips when input arrives in small pieces`() {
        val compressedData = storedBlocks(LARGE_DATA, blockSize = 4096)
        assertContentEquals(LARGE_DATA, inflateChunked(compressedData, inputChunk = 13, outputChunk = 11))
    }

    @Test
    fun `stored block spanning the sliding window round trips`() {
        // Larger than the 32KiB window, so the window must wrap while the block is copied.
        val data = ByteArray(70_000) { index -> ((index * 91 + 7) and 0xFF).toByte() }
        assertContentEquals(data, Inflater.decompress(storedBlocks(data, blockSize = 9973)))
    }

    @Test
    fun `compressed block back-references data from a preceding stored block`() {
        val compressedData = storedBlocks(DICTIONARY_DATA, finalBlock = false) + BACK_REFERENCE_BLOCK
        assertContentEquals(DICTIONARY_DATA + DICTIONARY_DATA, Inflater.decompress(compressedData))
    }

    @Test
    fun `stored block with a corrupt length check is rejected`() {
        val compressedData = storedBlocks(SMALL_DATA)
        compressedData[3] = (compressedData[3] + 1).toByte() // Break NLEN
        assertFailsWith<DataFormatException> { Inflater.decompress(compressedData) }
    }

    @Test
    fun `truncated stored block is rejected`() {
        val compressedData = storedBlocks(SMALL_DATA)
        assertFailsWith<DataFormatException> { Inflater.decompress(compressedData.copyOf(compressedData.size - 4)) }
    }
}
