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

import kotlinx.io.Sink
import kotlinx.io.Source

// TODO: optimize this
// Just add a zero terminator to the end of the UTF-8 data
internal fun String.encodeZeroTerminated(): ByteArray = encodeToByteArray() + 0x00.toByte()

internal fun Sink.writeZeroTerminatedString(value: String) = write(value.encodeZeroTerminated())

// TODO: optimize this
// We just assume zero-terminated UTF-8
internal fun ByteArray.decodeZeroTerminated(): String = sliceArray(0..<lastIndex).decodeToString()

// TODO: optimize this
internal fun Source.readZeroTerminatedString(): String {
    val peeking = peek()
    var byte = peeking.readByte()
    var index = 0
    // First probe for length of the string
    while (byte != 0.toByte()) {
        index++
        byte = peeking.readByte()
    }
    // Then allocate array and copy
    val result = ByteArray(index)
    byte = readByte()
    index = 0
    while (byte != 0.toByte()) {
        result[index++] = byte
        byte = readByte()
    }
    return result.decodeToString()
}