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

import dev.karmakrafts.kompress.exception.DataFormatException
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FramingDecompressorTest {
    @Test
    fun `decompress strips wrapper prologue before payload`() {
        val delegate = ScriptedDecompressor(byteArrayOf(0x10, 0x11))
        delegate.scriptReads(2)
        val decompressor = TestFramingDecompressor(
            delegate, prologue = byteArrayOf(0x01, 0x02), epilogue = byteArrayOf()
        )

        decompressor.setInput(byteArrayOf(0x01, 0x02, 0x33, 0x34))
        decompressor.finish()

        assertContentEquals(byteArrayOf(0x10, 0x11), drainDecompressor(decompressor, 2))
        assertTrue(decompressor.finished)
        assertEquals(4L, decompressor.bytesRead)
        assertEquals(2L, delegate.bytesRead)
        assertEquals(1, decompressor.prologueCalls)
    }

    @Test
    fun `decompress validates epilogue after payload`() {
        val delegate = ScriptedDecompressor(byteArrayOf(0x40))
        delegate.scriptReads(1)
        val decompressor = TestFramingDecompressor(
            delegate, prologue = byteArrayOf(0x01), epilogue = byteArrayOf(0x7A, 0x7B)
        )

        decompressor.setInput(byteArrayOf(0x01, 0x55))

        val output = ByteArray(1)
        val written = decompressor.decompress(output, 0, 1)

        decompressor.setInput(byteArrayOf(0x7A, 0x7B))
        decompressor.finish()
        val tailWritten = decompressor.decompress(output, 0, 1)

        assertEquals(1, written)
        assertEquals(0x40.toByte(), output[0])
        assertEquals(0, tailWritten)
        assertTrue(decompressor.finished)
        assertEquals(4L, decompressor.bytesRead)
        assertEquals(1, decompressor.epilogueCalls)
    }

    @Test
    fun `reset re-enables prologue validation and clears bytes read`() {
        val delegate = ScriptedDecompressor(byteArrayOf(0x10))
        delegate.scriptReads(1)
        val decompressor = TestFramingDecompressor(
            delegate, prologue = byteArrayOf(0x01), epilogue = byteArrayOf()
        )

        decompressor.setInput(byteArrayOf(0x01, 0x20))
        decompressor.finish()
        assertContentEquals(byteArrayOf(0x10), drainDecompressor(decompressor))
        assertEquals(2L, decompressor.bytesRead)

        decompressor.reset()
        assertEquals(0L, decompressor.bytesRead)
        delegate.script(byteArrayOf(0x11))
        delegate.scriptReads(1)
        decompressor.setInput(byteArrayOf(0x01, 0x21))
        decompressor.finish()

        assertContentEquals(byteArrayOf(0x11), drainDecompressor(decompressor))
        assertEquals(2L, decompressor.bytesRead)
        assertEquals(2, decompressor.prologueCalls)
    }

    @Test
    fun `decompress notifies data writes incrementally`() {
        val delegate = ScriptedDecompressor(
            byteArrayOf(0x10), byteArrayOf(0x11), byteArrayOf(0x12)
        )
        delegate.scriptReads(2, 1, 3)
        val decompressor = TestFramingDecompressor(
            delegate, prologue = byteArrayOf(), epilogue = byteArrayOf()
        )

        decompressor.setInput(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05))
        decompressor.finish()

        val output = ByteArray(1)
        repeat(3) {
            decompressor.decompress(output, 0, output.size)
        }

        assertEquals(listOf(0 to 1, 0 to 1, 0 to 1), decompressor.dataWrites)
        assertEquals(6L, delegate.bytesRead)
    }

    @Test
    fun `decompress throws when wrapper prologue is incomplete at finish`() {
        val delegate = ScriptedDecompressor(byteArrayOf(0x10))
        val decompressor = TestFramingDecompressor(
            delegate, prologue = byteArrayOf(0x01, 0x02), epilogue = byteArrayOf()
        )

        decompressor.setInput(byteArrayOf(0x01))
        decompressor.finish()

        assertFailsWith<DataFormatException> {
            decompressor.decompress(ByteArray(8))
        }
    }

    private fun drainDecompressor(decompressor: Decompressor, bufferSize: Int = 8): ByteArray {
        val output = Buffer()
        val chunkBuffer = ByteArray(bufferSize)
        while (true) {
            val written = decompressor.decompress(chunkBuffer)
            if (written == 0) break
            output.write(chunkBuffer, 0, written)
        }
        return output.readByteArray()
    }

    private class TestFramingDecompressor(
        decompressor: Decompressor, private val prologue: ByteArray, private val epilogue: ByteArray
    ) : FramingDecompressor(decompressor) {
        var prologueCalls: Int = 0
            private set
        var epilogueCalls: Int = 0
            private set
        val dataWrites = mutableListOf<Pair<Int, Int>>()

        private var prologueOffset: Int = 0
        private var epilogueOffset: Int = 0

        override fun consumePrologue(): Boolean {
            prologueCalls++
            while (prologueOffset < prologue.size && buffer.size > 0L) {
                val actual = buffer.readByte()
                val expected = prologue[prologueOffset]
                if (actual != expected) {
                    throw DataFormatException("Invalid prologue byte at index $prologueOffset")
                }
                prologueOffset++
            }
            return prologueOffset >= prologue.size
        }

        override fun consumeEpilogue(): Boolean {
            epilogueCalls++
            while (epilogueOffset < epilogue.size && buffer.size > 0L) {
                val actual = buffer.readByte()
                val expected = epilogue[epilogueOffset]
                if (actual != expected) {
                    throw DataFormatException("Invalid epilogue byte at index $epilogueOffset")
                }
                epilogueOffset++
            }
            return epilogueOffset >= epilogue.size
        }

        override fun onDataWritten(output: ByteArray, offset: Int, size: Int) {
            dataWrites += offset to size
        }

        override fun reset() {
            super.reset()
            prologueOffset = 0
            epilogueOffset = 0
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class ScriptedDecompressor(vararg chunks: ByteArray) : Decompressor {
        private val scriptedChunks = mutableListOf<ByteArray>()
        private val scriptedReadSizes = mutableListOf<Int>()
        private var chunkIndex: Int = 0
        private var chunkOffset: Int = 0
        private var readIndex: Int = 0
        private var finishCalled: Boolean = false

        init {
            script(*chunks)
        }

        fun script(vararg chunks: ByteArray) {
            scriptedChunks.clear()
            scriptedChunks.addAll(chunks)
            chunkIndex = 0
            chunkOffset = 0
            finishCalled = false
        }

        fun scriptReads(vararg reads: Int) {
            scriptedReadSizes.clear()
            scriptedReadSizes.addAll(reads.toTypedArray())
            readIndex = 0
        }

        override var input: ByteArray = byteArrayOf()
        override var inputOffset: Int = 0
        override var inputSize: Int = 0
        override var remaining: Int = 0
        override var bytesRead: Long = 0L
        override var bytesWritten: Long = 0L

        override val needsInput: Boolean
            get() = remaining <= 0

        override val finished: Boolean
            get() = finishCalled && chunkIndex >= scriptedChunks.size

        override fun setInput(data: ByteArray, offset: Int, size: Int) {
            input = data
            inputOffset = offset
            inputSize = size
            remaining = size
            readIndex = 0
        }

        override fun decompress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
            if (size <= 0) return 0

            val plannedRead = scriptedReadSizes.getOrNull(readIndex) ?: 0
            if (readIndex < scriptedReadSizes.size) {
                readIndex++
            }
            val consumed = min(plannedRead, remaining)
            if (consumed > 0) {
                remaining -= consumed
                bytesRead += consumed
            }

            val chunk = scriptedChunks.getOrNull(chunkIndex) ?: return 0
            val toCopy = min(size, chunk.size - chunkOffset)
            if (toCopy <= 0) {
                chunkIndex++
                chunkOffset = 0
                return decompress(output, offset, size, flush)
            }
            chunk.copyInto(output, offset, chunkOffset, chunkOffset + toCopy)
            chunkOffset += toCopy
            if (chunkOffset >= chunk.size) {
                chunkIndex++
                chunkOffset = 0
            }
            bytesWritten += toCopy
            return toCopy
        }

        override fun finish() {
            finishCalled = true
        }

        override fun reset() {
            input = byteArrayOf()
            inputOffset = 0
            inputSize = 0
            remaining = 0
            bytesRead = 0L
            bytesWritten = 0L
            scriptedChunks.clear()
            scriptedReadSizes.clear()
            chunkIndex = 0
            chunkOffset = 0
            readIndex = 0
            finishCalled = false
        }

        override fun close() = Unit
    }
}