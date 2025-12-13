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

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalUnsignedTypes::class)

package dev.karmakrafts.kompress.fflate

import org.khronos.webgl.Uint8Array
import kotlin.js.ExperimentalWasmJsInterop

@JsFun("function(level, mem) { return {level: level, mem: mem}; }")
internal external fun DeflateOptions(
    level: Int, mem: Int
): DeflateOptions

@JsFun("function(dictionary, out) { return {dictionary: dictionary, out: out}; }")
internal external fun InflateOptions(
    dictionary: Uint8Array?, out: Uint8Array?
): InflateOptions

@JsFun("function(level, mem) { return {level: level, mem: mem}; }")
internal external fun ZlibOptions(
    level: Int, mem: Int
): ZlibOptions

@JsFun("function(dictionary, out) { return {dictionary: dictionary, out: out}; }")
internal external fun UnzlibOptions(
    dictionary: Uint8Array?, out: Uint8Array?
): UnzlibOptions