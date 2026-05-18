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

package dev.karmakrafts.kompress.zip

import kotlin.jvm.JvmInline

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.4.
 */
sealed interface ZipGPBF {
    @JvmInline
    value class Implode(override val value: UShort) : ZipGPBF {}

    @JvmInline
    value class Deflate(override val value: UShort) : ZipGPBF {}

    @JvmInline
    value class LZMA(override val value: UShort) : ZipGPBF {}

    val value: UShort
}