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
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DecompressingSourceTest {
    @Test
    fun `decompressing source`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        buffer.write(data)

        val decompressor = MockDecompressor()
        val decompressingSource = (buffer as RawSource).decompressingSource(decompressor, bufferSize = 128)

        val resultBuffer = Buffer()
        val read = resultBuffer.transferFrom(decompressingSource)

        assertEquals(data.size.toLong(), read)
        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `decompressing source small reads`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        buffer.write(data)

        val decompressor = MockDecompressor()
        val decompressingSource = (buffer as RawSource).decompressingSource(decompressor, bufferSize = 128)

        val resultBuffer = Buffer()
        var totalRead = 0L
        while (true) {
            val read = decompressingSource.readAtMostTo(resultBuffer, 10)
            if (read == -1L) break
            totalRead += read
        }

        assertEquals(data.size.toLong(), totalRead)
        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `decompressing source close`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        buffer.write(data)

        val decompressor = MockDecompressor()
        val decompressingSource = (buffer as RawSource).decompressingSource(decompressor, bufferSize = 128)

        decompressingSource.close()
    }

    @Test
    fun `decompressing source with offset and size`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        // Write some dummy data before and after
        buffer.writeByte(1)
        buffer.write(data)
        buffer.writeByte(2)

        val decompressor = MockDecompressor()
        val decompressingSource = (buffer as RawSource).decompressingSource(decompressor, bufferSize = 128)

        val resultBuffer = Buffer()
        // Read the first byte (the 1 we wrote)
        val firstRead = decompressingSource.readAtMostTo(resultBuffer, 1)
        assertEquals(1, firstRead)
        assertEquals(1, resultBuffer.readByte())

        // Read the data
        var totalRead = 0L
        while (totalRead < 1024) {
            val read = decompressingSource.readAtMostTo(resultBuffer, 1024 - totalRead)
            if (read == -1L) break
            totalRead += read
        }
        assertEquals(data.size.toLong(), totalRead)
        assertContentEquals(data, resultBuffer.readByteArray())

        // Read the last byte (the 2 we wrote)
        val lastRead = decompressingSource.readAtMostTo(resultBuffer, 1)
        assertEquals(1, lastRead)
        assertEquals(2, resultBuffer.readByte())
    }

    @Test
    fun `mock decompressor with offset and size`() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5)
        val decompressor = MockDecompressor()
        decompressor.setInput(data, 1, 3) // Should only "decompress" 1, 2, 3

        assertEquals(1, decompressor.inputOffset)
        assertEquals(3, decompressor.inputSize)
        assertEquals(3, decompressor.remaining)

        val output = ByteArray(10)
        val decompressed = decompressor.decompress(output)

        assertEquals(3, decompressed)
        assertContentEquals(byteArrayOf(1, 2, 3), output.sliceArray(0 until 3))
        assertEquals(4, decompressor.inputOffset)
        assertEquals(0, decompressor.inputSize)
        assertEquals(0, decompressor.remaining)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class MockDecompressor : Decompressor {
        private var _input: ByteArray? = null
        private var _finished = false
        private var _finishCalled = false

        override var inputOffset: Int = 0
            private set
        override var inputSize: Int = 0
            private set
        override val remaining: Int
            get() = inputSize

        override var input: ByteArray
            get() = _input ?: byteArrayOf()
            set(value) {
                setInput(value)
            }

        override val needsInput: Boolean
            get() = inputSize <= 0

        override val finished: Boolean
            get() = _finished

        override fun setInput(data: ByteArray, offset: Int, size: Int) {
            _input = data
            inputOffset = offset
            inputSize = size
        }

        override fun decompress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
            if (_finished) return 0
            val input = _input
            if (input == null || inputSize <= 0) {
                if (_finishCalled) {
                    _finished = true
                }
                return 0
            }
            val toCopy = min(inputSize, size)
            input.copyInto(output, offset, inputOffset, inputOffset + toCopy)
            inputOffset += toCopy
            inputSize -= toCopy
            return toCopy
        }

        override fun finish() {
            _finishCalled = true
        }

        override fun close() = Unit
        override fun reset() {
            _input = null
            inputOffset = 0
            inputSize = 0
            _finished = false
            _finishCalled = false
        }
    }
}
