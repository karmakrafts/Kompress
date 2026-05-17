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
import kotlinx.io.RawSink
import kotlinx.io.readByteArray
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompressingSinkTest {
    @Test
    fun `compressing sink`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        sourceBuffer.write(data)

        val resultBuffer = Buffer()
        val compressor = MockCompressor()
        val compressingSink = (resultBuffer as RawSink).compressingSink(compressor, bufferSize = 128)

        compressingSink.write(sourceBuffer, data.size.toLong())
        compressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `compressing sink small writes`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        sourceBuffer.write(data)

        val resultBuffer = Buffer()
        val compressor = MockCompressor()
        val compressingSink = (resultBuffer as RawSink).compressingSink(compressor, bufferSize = 128)

        while (sourceBuffer.size > 0) {
            compressingSink.write(sourceBuffer, min(sourceBuffer.size, 10L))
        }
        compressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `compressing sink flush`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        sourceBuffer.write(data)

        val resultBuffer = Buffer()
        val compressor = MockCompressor()
        val compressingSink = (resultBuffer as RawSink).compressingSink(compressor, bufferSize = 128)

        compressingSink.write(sourceBuffer, data.size.toLong())
        compressingSink.flush()
        // MockCompressor doesn't really do anything on flush, but we can verify it's callable
        compressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `mock compressor input offset and size`() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val compressor = MockCompressor()
        compressor.setInput(data, 2, 5)

        assertContentEquals(data, compressor.input)
        assertEquals(2, compressor.inputOffset)
        assertEquals(5, compressor.inputSize)
        assertEquals(5, compressor.remaining)
        assertFalse(compressor.needsInput)

        val output = ByteArray(10)
        val written = compressor.compress(output, 1, 3)

        assertEquals(3, written)
        assertContentEquals(byteArrayOf(0, 2, 3, 4, 0, 0, 0, 0, 0, 0), output)
        assertEquals(5, compressor.inputOffset)
        assertEquals(2, compressor.inputSize)
        assertEquals(2, compressor.remaining)

        val written2 = compressor.compress(output, 4, 5)
        assertEquals(2, written2)
        assertContentEquals(byteArrayOf(0, 2, 3, 4, 5, 6, 0, 0, 0, 0), output)
        assertEquals(7, compressor.inputOffset)
        assertEquals(0, compressor.inputSize)
        assertTrue(compressor.needsInput)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class MockCompressor : Compressor {
        private var _input: ByteArray = byteArrayOf()
        private var _finished = false
        private var _finishCalled = false

        override var inputOffset: Int = 0
        override var inputSize: Int = 0
        override val remaining: Int get() = inputSize

        override fun setInput(data: ByteArray, offset: Int, size: Int) {
            _input = data
            inputOffset = offset
            inputSize = size
        }

        override var input: ByteArray
            get() = _input
            set(value) {
                setInput(value)
            }

        override val needsInput: Boolean
            get() = inputSize <= 0

        override val finished: Boolean
            get() = _finished

        override fun compress(output: ByteArray, offset: Int, size: Int, flush: Boolean): Int {
            if (_finished) return 0
            if (inputSize <= 0) {
                if (_finishCalled) {
                    _finished = true
                }
                return 0
            }
            val toCopy = min(inputSize, size)
            _input.copyInto(output, offset, inputOffset, inputOffset + toCopy)
            inputOffset += toCopy
            inputSize -= toCopy
            return toCopy
        }

        override fun finish() {
            _finishCalled = true
        }

        override fun close() = Unit
        override fun reset() {
            _input = byteArrayOf()
            inputOffset = 0
            inputSize = 0
            _finished = false
            _finishCalled = false
        }
    }
}
