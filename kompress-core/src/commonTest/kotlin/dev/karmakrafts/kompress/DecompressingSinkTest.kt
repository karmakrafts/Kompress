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
        val decompressingSink = (resultBuffer as RawSink).decompressing(decompressor, bufferSize = 128)

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
        val decompressingSink = (resultBuffer as RawSink).decompressing(decompressor, bufferSize = 128)

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
        val decompressingSink = (resultBuffer as RawSink).decompressing(decompressor, bufferSize = 128)

        decompressingSink.write(sourceBuffer, data.size.toLong())
        decompressingSink.flush()
        decompressingSink.close()

        assertContentEquals(data, resultBuffer.readByteArray())
    }

    private class MockDecompressor : Decompressor {
        private var _input: ByteArray = byteArrayOf()
        private var _finished = false
        private var _inputExhausted = true
        private var _finishCalled = false

        override var input: ByteArray
            get() = _input
            set(value) {
                _input = value
                _inputExhausted = value.isEmpty()
            }

        override val needsInput: Boolean
            get() = _inputExhausted

        override val finished: Boolean
            get() = _finished

        override fun decompress(output: ByteArray): Int {
            if (_finished) return 0
            if (_inputExhausted) {
                if (_finishCalled) {
                    _finished = true
                }
                return 0
            }
            val toCopy = min(_input.size, output.size)
            _input.copyInto(output, 0, 0, toCopy)
            if (toCopy < _input.size) {
                _input = _input.copyOfRange(toCopy, _input.size)
            }
            else {
                _input = byteArrayOf()
                _inputExhausted = true
            }
            return toCopy
        }

        override fun finish() {
            _finishCalled = true
        }

        override fun close() {}
    }
}
