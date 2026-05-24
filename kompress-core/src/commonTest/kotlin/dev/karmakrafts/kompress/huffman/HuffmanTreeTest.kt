/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kompress.huffman

import dev.karmakrafts.karbide.BitOrder
import dev.karmakrafts.karbide.bitSink
import dev.karmakrafts.karbide.bitSource
import dev.karmakrafts.kompress.exception.NoSuchCodeException
import dev.karmakrafts.kompress.exception.NoSuchSymbolException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HuffmanTreeTest {
    @Test
    fun `encode empty tree`() {
        val tree = HuffmanTree(intArrayOf())
        val buffer = Buffer()
        buffer.bitSink().use { sink ->
            assertFailsWith<NoSuchSymbolException> {
                tree.encodeSymbol(sink, 0)
            }
        }
    }

    @Test
    fun `encode small tree`() {
        val tree = HuffmanTree(intArrayOf(1, 2, 2))
        val buffer = Buffer()
        buffer.bitSink(bitOrder = BitOrder.LSB_FIRST).use { sink ->
            tree.encodeSymbol(sink, 0)
            tree.encodeSymbol(sink, 1)
            tree.encodeSymbol(sink, 2)
        }
        assertEquals(26.toByte(), buffer.readByte())
    }

    @Test
    fun `decode small tree`() {
        val tree = HuffmanTree(intArrayOf(1, 2, 2))
        val buffer = Buffer()
        buffer.writeByte(26.toByte())
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            assertEquals(0, tree.decodeSymbol(source))
            assertEquals(1, tree.decodeSymbol(source))
            assertEquals(2, tree.decodeSymbol(source))
        }
    }

    @Test
    fun `decode invalid code`() {
        val tree = HuffmanTree(intArrayOf(1))
        val buffer = Buffer()
        buffer.writeByte(0x01.toByte())
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            assertFailsWith<NoSuchCodeException> {
                tree.decodeSymbol(source)
            }
        }
    }

    @Test
    fun `encode with skipped symbols`() {
        val tree = HuffmanTree(intArrayOf(0, 0, 1))
        val buffer = Buffer()
        buffer.bitSink(bitOrder = BitOrder.LSB_FIRST).use { sink ->
            tree.encodeSymbol(sink, 0)
            tree.encodeSymbol(sink, 1)
            assertFailsWith<NoSuchSymbolException> {
                tree.encodeSymbol(sink, 3)
            }
            tree.encodeSymbol(sink, 2)
        }
        assertEquals(0x00.toByte(), buffer.readByte())
    }

    @Test
    fun `decode with skipped symbols`() {
        val tree = HuffmanTree(intArrayOf(0, 0, 1))
        val buffer = Buffer()
        buffer.writeByte(0x00.toByte())
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            assertEquals(2, tree.decodeSymbol(source))
        }
    }

    @Test
    fun `round-trip small tree`() {
        val tree = HuffmanTree(intArrayOf(1, 2, 2))
        val buffer = Buffer()
        val symbols = listOf(0, 1, 2, 1, 0)
        buffer.bitSink(bitOrder = BitOrder.LSB_FIRST).use { sink ->
            for (s in symbols) {
                tree.encodeSymbol(sink, s)
            }
        }
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            for (s in symbols) {
                assertEquals(s, tree.decodeSymbol(source))
            }
        }
    }

    @Test
    fun `encode large tree`() {
        val tree = HuffmanTree(IntArray(256) { 8 })
        val buffer = Buffer()
        buffer.bitSink(bitOrder = BitOrder.LSB_FIRST).use { sink ->
            for (s in 0 until 256) {
                tree.encodeSymbol(sink, s)
            }
        }
        val bytes = buffer.readByteArray()
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x80.toByte(), bytes[1])
        assertEquals(0x40.toByte(), bytes[2])
        assertEquals(0xC0.toByte(), bytes[3])
    }

    @Test
    fun `decode large tree`() {
        val tree = HuffmanTree(IntArray(256) { 8 })
        val buffer = Buffer()
        buffer.write(byteArrayOf(0x00.toByte(), 0x80.toByte(), 0x40.toByte(), 0xC0.toByte()))
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            assertEquals(0, tree.decodeSymbol(source))
            assertEquals(1, tree.decodeSymbol(source))
            assertEquals(2, tree.decodeSymbol(source))
            assertEquals(3, tree.decodeSymbol(source))
        }
    }

    @Test
    fun `round-trip large tree`() {
        val tree = HuffmanTree(IntArray(256) { 8 })
        val buffer = Buffer()
        val symbols = (0 until 256).toList()
        buffer.bitSink(bitOrder = BitOrder.LSB_FIRST).use { sink ->
            for (s in symbols) {
                tree.encodeSymbol(sink, s)
            }
        }
        buffer.bitSource(bitOrder = BitOrder.LSB_FIRST).use { source ->
            for (s in symbols) {
                assertEquals(s, tree.decodeSymbol(source))
            }
        }
    }
}