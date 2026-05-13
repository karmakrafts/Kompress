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

class CompressingSourceTest {

    @Test
    fun `compressing source`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        buffer.write(data)

        val compressor = MockCompressor()
        val compressingSource = (buffer as RawSource).compressing(compressor, bufferSize = 128)

        val resultBuffer = Buffer()
        val read = resultBuffer.transferFrom(compressingSource)

        assertEquals(data.size.toLong(), read)
        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `compressing source small reads`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        buffer.write(data)

        val compressor = MockCompressor()
        val compressingSource = (buffer as RawSource).compressing(compressor, bufferSize = 128)

        val resultBuffer = Buffer()
        var totalRead = 0L
        while (true) {
            val read = compressingSource.readAtMostTo(resultBuffer, 10)
            if (read == -1L) break
            totalRead += read
        }

        assertEquals(data.size.toLong(), totalRead)
        assertContentEquals(data, resultBuffer.readByteArray())
    }

    @Test
    fun `compressing source close`() {
        val data = ByteArray(1024) { it.toByte() }
        val buffer = Buffer()
        buffer.write(data)

        val compressor = MockCompressor()
        val compressingSource = (buffer as RawSource).compressing(compressor, bufferSize = 128)

        compressingSource.close()
        // Compressor should be closed as well, but our MockCompressor doesn't track it.
    }

    private class MockCompressor : Compressor {
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

        override fun compress(output: ByteArray): Int {
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
