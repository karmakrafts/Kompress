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

import java.util.zip.Inflater as JavaInflater

private class InflaterImpl(raw: Boolean) : Inflater {
    private val impl: JavaInflater = JavaInflater(raw)

    override var input: ByteArray = ByteArray(0)
        set(value) {
            impl.setInput(value)
            field = value
        }

    override val needsInput: Boolean get() = impl.needsInput()
    override val finished: Boolean get() = impl.finished()

    override fun inflate(output: ByteArray): Int = impl.inflate(output)

    override fun finish() = Unit

    override fun close() = impl.end()
}

actual fun Inflater(raw: Boolean): Inflater = InflaterImpl(raw)