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

@file:JvmName("LZ4Compressor$")

package dev.karmakrafts.kompress.lz4

import dev.karmakrafts.kompress.Compressor
import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.jpountz.lz4.LZ4Compressor as LZ4JCompressor

@Suppress("OVERRIDE_DEPRECATION")
private class LZ4CompressorImpl : Compressor {
    private val delegate: LZ4JCompressor = LZ4Factory.fastestInstance().fastCompressor()
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(Compressor.DEFAULT_BUFFER_SIZE).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(Compressor.DEFAULT_BUFFER_SIZE).order(ByteOrder.nativeOrder())

    private var _input: ByteArray = ByteArray(0)
    override var input: ByteArray
        get() = _input
        set(value) {
            setInput(value)
        }

    override var inputOffset: Int = 0
        private set
    override var inputSize: Int = 0
        private set
    override var finished: Boolean = false
        private set

    override val remaining: Int get() = _input.size - inputBuffer.position()
    override val needsInput: Boolean get() = !finished && remaining == 0

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        _input = data
        inputOffset = offset
        inputSize = size
        inputBuffer.clear()
        inputBuffer.put(data, offset, size)
        inputBuffer.flip()
    }

    override fun compress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        if (finished) return 0
        outputBuffer.clear()
        delegate.compress(inputBuffer, outputBuffer)
        val compressed = outputBuffer.position()
        if (compressed == 0) return 0
        outputBuffer.get(output, offset, size)
        return compressed
    }

    override fun finish() {
        finished = true
    }

    override fun reset() {
        setInput(ByteArray(0))
        inputBuffer.clear()
        outputBuffer.clear()
        finished = false
    }

    override fun close() = Unit
}

actual fun LZ4Compressor(): Compressor = LZ4CompressorImpl()