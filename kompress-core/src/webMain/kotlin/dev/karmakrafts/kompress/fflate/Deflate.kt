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

@file:OptIn(ExperimentalWasmJsInterop::class) @file:JsModule("fflate")

package dev.karmakrafts.kompress.fflate

import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsModule
import kotlin.js.definedExternally

internal external interface DeflateOptions : JsAny {
    val level: Int
    val mem: Int
}

internal external class Deflate( // @formatter:off
    options: DeflateOptions,
    callback: FlateStreamHandler? = definedExternally
) : FlateStream { // @formatter:on
    override var ondata: FlateStreamHandler?
    override fun push(data: Uint8Array, isFinal: Boolean)
}