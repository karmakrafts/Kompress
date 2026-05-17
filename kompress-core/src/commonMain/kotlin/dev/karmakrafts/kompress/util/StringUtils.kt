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

@InternalKompressApi
fun String.encodeToZTLatin1(): ByteArray {
    val result = ByteArray(length + 1)
    for (index in indices) {
        result[index] = (this[index].code and 0xFF).toByte()
    }
    return result
}

@InternalKompressApi
fun Sink.writeZTLatin1String(value: String) = write(value.encodeToZTLatin1())

// TODO: check this
@InternalKompressApi
fun ByteArray.decodeFromZTLatin1(): String {
    val chars = CharArray(size - 1)
    for (index in 0..<lastIndex) { // Skip null terminator
        chars[index] = (this[index].toInt() and 0xFF).toChar()
    }
    return chars.concatToString()
}

// TODO: optimize this
@InternalKompressApi
fun Source.readZTStringAsSize(): Long {
    var byte = readByte()
    var index = 0L
    // First probe for length of the string
    while (byte != 0.toByte()) {
        index++
        byte = readByte()
    }
    return index
}

// TODO: check this
// TODO: optimize this
@InternalKompressApi
fun Source.readZTLatin1String(): String {
    val peeking = peek()
    var byte = peeking.readByte()
    var index = 0
    // First probe for length of the string
    while (byte != 0.toByte()) {
        index++
        byte = peeking.readByte()
    }
    // Then allocate array and copy
    val result = CharArray(index)
    byte = readByte()
    index = 0
    while (byte != 0.toByte()) {
        result[index++] = (byte.toInt() and 0xFF).toChar()
        byte = readByte()
    }
    return result.concatToString()
}