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

import dev.karmakrafts.kompress.DataFormatException
import dev.karmakrafts.kompress.InternalKompressApi
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * @throws dev.karmakrafts.kompress.DataFormatException when this String contains characters
 *  outside the valid LATIN-1 range.
 */
@InternalKompressApi
fun String.encodeToLatin1(): ByteArray {
    val result = ByteArray(length)
    for (index in indices) {
        val codepoint = this[index].code
        if (codepoint > 0xFF) throw DataFormatException("Character outside LATIN-1 range")
        result[index] = (codepoint and 0xFF).toByte()
    }
    return result
}

/**
 * @throws dev.karmakrafts.kompress.DataFormatException when the given String contains characters
 *  outside the valid LATIN-1 range.
 */
@InternalKompressApi
fun Sink.writeLatin1String(value: String) = write(value.encodeToLatin1())

@InternalKompressApi
fun ByteArray.decodeFromLatin1(): String {
    val chars = CharArray(size)
    for (index in indices) {
        chars[index] = (this[index].toInt() and 0xFF).toChar()
    }
    return chars.concatToString()
}

@InternalKompressApi
fun Source.readLatin1String(size: Int = peek().bytesUntilZeroTerminator().toInt()): String {
    val result = CharArray(size)
    var byte = readByte()
    var index = 0
    while (index < size) {
        result[index++] = (byte.toInt() and 0xFF).toChar()
        byte = readByte()
    }
    return result.concatToString()
}