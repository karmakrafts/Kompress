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

@file:JvmName("LZ4Decompressor$")

package dev.karmakrafts.kompress.lz4

import dev.karmakrafts.kompress.DataFormatException
import dev.karmakrafts.kompress.Decompressor
import net.jpountz.lz4.LZ4Exception
import net.jpountz.lz4.LZ4Factory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.jpountz.lz4.LZ4FastDecompressor as LZ4JFastDecompressor

@Suppress("OVERRIDE_DEPRECATION")
private class LZ4DecompressorImpl : Decompressor {
    private val delegate: LZ4JFastDecompressor = LZ4Factory.fastestInstance().fastDecompressor()
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(Decompressor.DEFAULT_BUFFER_SIZE).order(ByteOrder.nativeOrder())
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(Decompressor.DEFAULT_BUFFER_SIZE).order(ByteOrder.nativeOrder())

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

    override fun decompress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int = try { // @formatter:on
        if (finished) return 0
        outputBuffer.clear()
        delegate.decompress(inputBuffer, outputBuffer)
        val compressed = outputBuffer.position()
        if (compressed == 0) return 0
        outputBuffer.get(output, offset, size)
        return compressed
    } catch (error: LZ4Exception) {
        throw DataFormatException(error.message, error.cause) // Rethrow as our type
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

actual fun LZ4Decompressor(): Decompressor = LZ4DecompressorImpl()