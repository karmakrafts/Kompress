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

@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.karmakrafts.kompress.fflate

import js.typedarrays.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal external interface UnzlibOptions : JsAny {
    var dictionary: Uint8Array<*>?
    var out: Uint8Array<*>?
}

@Suppress("EXPECTED_EXTERNAL_DECLARATION")
internal expect external class Unzlib(options: UnzlibOptions) : FlateStream {
    override var ondata: FlateStreamHandler?
    override fun push(data: Uint8Array<*>, isFinal: Boolean)
}