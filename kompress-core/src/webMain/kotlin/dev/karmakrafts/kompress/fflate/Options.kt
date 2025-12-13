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

import org.khronos.webgl.Uint8Array

internal fun DeflateOptions(level: Int, mem: Int): DeflateOptions = unsafeJso {
    this.level = level
    this.mem = mem
}

internal fun InflateOptions(dictionary: Uint8Array?, out: Uint8Array?): InflateOptions = unsafeJso {
    this.dictionary = dictionary
    this.out = out
}

internal fun UnzlibOptions(dictionary: Uint8Array?, out: Uint8Array?): UnzlibOptions = unsafeJso {
    this.dictionary = dictionary
    this.out = out
}

internal fun ZlibOptions(level: Int, mem: Int): ZlibOptions = unsafeJso {
    this.level = level
    this.mem = mem
}