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

private class DeflaterImpl( // @formatter:off
    raw: Boolean,
    initialLevel: Int
) : Deflater { // @formatter:on
    private val impl: JavaDeflater = JavaDeflater(initialLevel, raw)

    override var level: Int = initialLevel
        set(value) {
            impl.setLevel(value)
            field = value
        }

    override var input: ByteArray = ByteArray(0)
        set(value) {
            impl.setInput(value)
            field = value
        }

    override val needsInput: Boolean get() = impl.needsInput()
    override val finished: Boolean get() = impl.finished()

    override fun finish() = impl.finish()
    override fun compress(output: ByteArray): Int = impl.deflate(output)
    override fun close() = impl.end()
}

actual fun Deflater(raw: Boolean, level: Int): Deflater = DeflaterImpl(raw, level)