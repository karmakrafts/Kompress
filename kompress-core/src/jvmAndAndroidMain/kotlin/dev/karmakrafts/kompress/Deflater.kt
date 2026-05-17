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

@file:JvmName("Deflater$")

package dev.karmakrafts.kompress

import java.util.zip.Deflater as JavaDeflater

@Suppress("OVERRIDE_DEPRECATION")
private class DeflaterImpl( // @formatter:off
    raw: Boolean,
    initialLevel: Int
) : Deflater { // @formatter:on
    private val impl: JavaDeflater = JavaDeflater(initialLevel, raw)
    private var isClosed: Boolean = false

    override var level: Int = initialLevel
        set(value) {
            impl.setLevel(value)
            field = value
        }

    override var inputOffset: Int = 0
        private set
    override var inputSize: Int = 0
        private set

    private var _input: ByteArray = ByteArray(0)
    override var input: ByteArray
        get() = _input
        set(value) {
            setInput(value)
        }

    override val remaining: Int get() = _input.size - impl.totalIn
    override val needsInput: Boolean get() = impl.needsInput()
    override val finished: Boolean get() = impl.finished()

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        _input = data
        inputOffset = offset
        inputSize = size
        impl.setInput(data, offset, size)
    }

    override fun finish() = impl.finish()

    override fun compress( // @formatter:off
        output: ByteArray,
        offset: Int,
        size: Int,
        flush: Boolean
    ): Int { // @formatter:on
        val flushFlags = if (flush) JavaDeflater.SYNC_FLUSH else JavaDeflater.NO_FLUSH
        return impl.deflate(output, offset, size, flushFlags)
    }

    override fun close() {
        if (isClosed) return
        impl.end()
        isClosed = true
    }

    override fun reset() = impl.reset()
}

actual fun Deflater(raw: Boolean, level: Int): Deflater = DeflaterImpl(raw, level)