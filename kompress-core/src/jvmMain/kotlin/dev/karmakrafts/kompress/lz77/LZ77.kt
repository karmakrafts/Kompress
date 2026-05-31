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

@file:JvmName("LZ77$")

package dev.karmakrafts.kompress.lz77

import dev.karmakrafts.kompress.util.has256BitSimd

private val lz77Factory: (Int, Int, Int, Int) -> LZ77 by lazy {
    if (has256BitSimd) ::FastLZ77
    else ::LZ77Impl
}

internal actual fun LZ77( // @formatter:off
    level: Int,
    minMatch: Int,
    maxMatch: Int,
    windowSize: Int
): LZ77 = lz77Factory(level, minMatch, maxMatch, windowSize)