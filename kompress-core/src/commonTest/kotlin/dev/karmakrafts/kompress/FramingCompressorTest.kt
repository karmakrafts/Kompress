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

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FramingCompressorTest {
    @Test
    fun `compress emits prologue before compressed payload`() {
        val delegate = ScriptedCompressor(
            byteArrayOf(0x10, 0x11), byteArrayOf(0x12)
        )
        val compressor = TestFramingCompressor(
            delegate, prologue = byteArrayOf(0x01, 0x02, 0x03), epilogue = byteArrayOf(0x7F)
        )

        val output = ByteArray(2)
        val result = Buffer()
        while (true) {
            val written = compressor.compress(output, 0, output.size)
            if (written == 0) break
            result.write(output, 0, written)
        }

        assertContentEquals(byteArrayOf(0x01, 0x02, 0x03, 0x10, 0x11, 0x12), result.readByteArray())
        assertEquals(6L, compressor.bytesWritten)
        assertEquals(1, compressor.prologueCalls)
        assertEquals(0, compressor.epilogueCalls)
    }

    @Test
    fun `compress emits epilogue only after finish and after payload`() {
        val delegate = ScriptedCompressor(
            byteArrayOf(0x10), byteArrayOf(0x11)
        )
        val compressor = TestFramingCompressor(
            delegate, prologue = byteArrayOf(0x01), epilogue = byteArrayOf(0x20, 0x21)
        )

        val output = ByteArray(1)
        val result = Buffer()

        result.write(output, 0, compressor.compress(output, 0, output.size))
        assertEquals(1L, compressor.bytesWritten)
        compressor.finish()
        while (true) {
            val written = compressor.compress(output, 0, output.size)
            if (written == 0) break
            result.write(output, 0, written)
        }

        assertContentEquals(byteArrayOf(0x01, 0x10, 0x11, 0x20, 0x21), result.readByteArray())
        assertEquals(5L, compressor.bytesWritten)
        assertEquals(1, compressor.prologueCalls)
        assertEquals(1, compressor.epilogueCalls)
    }

    @Test
    fun `reset re-enables prologue emission`() {
        val delegate = ScriptedCompressor(byteArrayOf(0x10))
        val compressor = TestFramingCompressor(
            delegate, prologue = byteArrayOf(0x01), epilogue = byteArrayOf(0x20)
        )

        val output = ByteArray(8)
        val result = Buffer()

        var written = compressor.compress(output, 0, output.size)
        result.write(output, 0, written)
        while (written > 0) {
            written = compressor.compress(output, 0, output.size)
            if (written > 0) {
                result.write(output, 0, written)
            }
        }
        assertEquals(2L, compressor.bytesWritten)

        compressor.reset()
        assertEquals(0L, compressor.bytesWritten)
        delegate.script(byteArrayOf(0x11))

        written = compressor.compress(output, 0, output.size)
        result.write(output, 0, written)
        while (written > 0) {
            written = compressor.compress(output, 0, output.size)
            if (written > 0) {
                result.write(output, 0, written)
            }
        }

        assertContentEquals(byteArrayOf(0x01, 0x10, 0x01, 0x11), result.readByteArray())
        assertEquals(2L, compressor.bytesWritten)
        assertEquals(2, compressor.prologueCalls)
    }

    @Test
    fun `compress notifies data reads incrementally`() {
        val delegate = ScriptedCompressor(
            byteArrayOf(0x10), byteArrayOf(0x11), byteArrayOf(0x12)
        )
        delegate.scriptReads(2, 1, 3)
        val compressor = TestFramingCompressor(
            delegate, prologue = byteArrayOf(), epilogue = byteArrayOf()
        )

        compressor.setInput(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06), 1, 6)

        val output = ByteArray(1)
        repeat(3) {
            compressor.compress(output, 0, output.size)
        }

        assertEquals(listOf(1 to 2, 3 to 1, 4 to 3), compressor.dataReads)
        assertEquals(6L, delegate.bytesRead)
    }

    private class TestFramingCompressor(
        compressor: Compressor, private val prologue: ByteArray, private val epilogue: ByteArray
    ) : FramingCompressor(compressor) {
        var prologueCalls: Int = 0
            private set
        var epilogueCalls: Int = 0
            private set
        val dataReads = mutableListOf<Pair<Int, Int>>()

        override fun appendPrologue() {
            prologueCalls++
            buffer.write(prologue)
        }

        override fun appendEpilogue() {
            epilogueCalls++
            buffer.write(epilogue)
        }

        override fun onDataRead(offset: Int, size: Int) {
            dataReads += offset to size
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class ScriptedCompressor(vararg chunks: ByteArray) : Compressor {
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
            get() = chunkIndex >= scriptedChunks.size

        override val finished: Boolean
            get() = finishCalled && chunkIndex >= scriptedChunks.size

        override fun setInput(data: ByteArray, offset: Int, size: Int) {
            input = data
            inputOffset = offset
            inputSize = size
            remaining = size
            readIndex = 0
        }

        override fun compress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
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
                return compress(output, offset, size, flush)
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