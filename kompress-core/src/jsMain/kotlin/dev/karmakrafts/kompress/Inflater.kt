/*
 * Copyright (C) 2025 Karma Krafts & associates
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

import dev.karmakrafts.kompress.fflate.Inflate
import dev.karmakrafts.kompress.fflate.InflateOptions
import dev.karmakrafts.kompress.fflate.Unzlib
import dev.karmakrafts.kompress.fflate.UnzlibOptions

private class InflaterImpl(raw: Boolean) : Inflater {
    private var impl: FlateStreamWrapper = FlateStreamWrapper(
        if (raw) Inflate(InflateOptions(null, null))
        else Unzlib(UnzlibOptions(null, null))
    )

    override var input: ByteArray
        get() = TODO("Not yet implemented")
        set(value) {}

    override val needsInput: Boolean
        get() = TODO("Not yet implemented")

    override val finished: Boolean
        get() = TODO("Not yet implemented")

    override fun inflate(output: ByteArray): Int {
        TODO("Not yet implemented")
    }

    override fun close() = Unit
}

actual fun Inflater(raw: Boolean): Inflater = InflaterImpl(raw)