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

package dev.karmakrafts.kompress.util

import dev.karmakrafts.kompress.InternalKompressApi
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.writeUByte

@InternalKompressApi
fun Source.bytesUntilZeroTerminator(): Long {
    var byte = readByte()
    var index = 0L
    // First probe for length of the string
    while (byte != 0.toByte()) {
        index++
        byte = readByte()
    }
    return index
}

@InternalKompressApi
inline fun <reified R> Sink.zeroTerminate(function: Sink.() -> R): R {
    val result = function(this)
    writeUByte(0.toUByte())
    return result
}