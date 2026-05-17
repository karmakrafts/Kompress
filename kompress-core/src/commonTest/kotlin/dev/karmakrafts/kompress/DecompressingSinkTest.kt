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

class DecompressingSinkTest {
    @Test
    fun `decompressing sink`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        sourceBuffer.write(data)

        val resultBuffer = Buffer()
        val decompressor = MockDecompressor()
        val decompressingSink = (resultBuffer as RawSink).decompressingSink(decompressor, bufferSize = 128)

        decompressingSink.write(sourceBuffer, data.size.toLong())
        decompressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `decompressing sink small writes`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        sourceBuffer.write(data)

        val resultBuffer = Buffer()
        val decompressor = MockDecompressor()
        val decompressingSink = (resultBuffer as RawSink).decompressingSink(decompressor, bufferSize = 128)

        while (sourceBuffer.size > 0) {
            decompressingSink.write(sourceBuffer, min(sourceBuffer.size, 10L))
        }
        decompressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `decompressing sink flush`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        sourceBuffer.write(data)

        val resultBuffer = Buffer()
        val decompressor = MockDecompressor()
        val decompressingSink = (resultBuffer as RawSink).decompressingSink(decompressor, bufferSize = 128)

        decompressingSink.write(sourceBuffer, data.size.toLong())
        decompressingSink.flush()
        decompressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `decompressing sink with offset and size`() {
        val data = ByteArray(1024) { it.toByte() }
        val sourceBuffer = Buffer()
        // Write some dummy data before and after
        sourceBuffer.writeByte(0)
        sourceBuffer.write(data)
        sourceBuffer.writeByte(0)
        sourceBuffer.skip(1)

        val resultBuffer = Buffer()
        val decompressor = MockDecompressor()
        val decompressingSink = (resultBuffer as RawSink).decompressingSink(decompressor, bufferSize = 128)

        decompressingSink.write(sourceBuffer, data.size.toLong())
        decompressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private class MockDecompressor : Decompressor {
        private var _input: ByteArray = byteArrayOf()
        private var _finished = false
        private var _finishCalled = false

        override var inputOffset: Int = 0
            private set
        override var inputSize: Int = 0
            private set
        override val remaining: Int
            get() = inputSize

        override var input: ByteArray
            get() = _input
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
        override fun reset() = Unit
    }
}
