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

package dev.karmakrafts.kompress.fflate

import kotlinx.js.JsPlainObject
import org.khronos.webgl.Uint8Array

@JsPlainObject
internal external interface InflateOptions {
    val dictionary: Uint8Array?
    val out: Uint8Array?
}

internal external class Inflate(options: InflateOptions) {
    var ondata: FlateStreamHandler?
    fun push(data: Uint8Array, isFinal: Boolean)
}