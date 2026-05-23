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

import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.exception.DataFormatException
import kotlinx.io.Sink

private val table: CharArray = charArrayOf( // @formatter:off
    // 0–31 (control chars)
    '\u0000','\u0001','\u0002','\u0003','\u0004','\u0005','\u0006','\u0007',
    '\b','\t','\n','\u000b','\u000c','\r','\u000e','\u000f',
    '\u0010','\u0011','\u0012','\u0013','\u0014','\u0015','\u0016','\u0017',
    '\u0018','\u0019','\u001a','\u001b','\u001c','\u001d','\u001e','\u001f',
    // 32–127 (standard ASCII)
    ' ','!','"','#','$','%','&','\'','(',')','*','+',',','-','.','/',
    '0','1','2','3','4','5','6','7','8','9',':',';','<','=','>','?',
    '@','A','B','C','D','E','F','G','H','I','J','K','L','M','N','O',
    'P','Q','R','S','T','U','V','W','X','Y','Z','[','\\',']','^','_',
    '`','a','b','c','d','e','f','g','h','i','j','k','l','m','n','o',
    'p','q','r','s','t','u','v','w','x','y','z','{','|','}','~','\u007F',
    // 128–255 (CP437 extended)
    'Ç','ü','é','â','ä','à','å','ç',
    'ê','ë','è','ï','î','ì','Ä','Å',
    'É','æ','Æ','ô','ö','ò','û','ù',
    'ÿ','Ö','Ü','¢','£','¥','₧','ƒ',
    'á','í','ó','ú','ñ','Ñ','ª','º',
    '¿','⌐','¬','½','¼','¡','«','»',
    '░','▒','▓','│','┤','Á','Â','À',
    '©','╣','║','╗','╝','¢','╜','╛',
    '┐','└','┴','┬','├','─','┼','╞',
    '╟','╚','╔','╩','╦','╠','═','╬',
    '╧','╨','╤','╥','╙','╘','╒','╓',
    '╫','╪','┘','┌','█','▄','▌','▐',
    '▀','α','ß','Γ','π','Σ','σ','µ',
    'τ','Φ','Θ','Ω','δ','∞','φ','ε',
    '∩','≡','±','≥','≤','⌠','⌡','÷',
    '≈','°','∙','·','√','ⁿ','²','■','\u00A0'
) // @formatter:on

private val reverseLookup: Map<Char, Byte> = buildMap {
    for (index in table.indices) {
        this[table[index]] = index.toByte()
    }
}

/**
 * @throws dev.karmakrafts.kompress.DataFormatException when this String contains characters
 *  outside the valid CP437 range.
 */
@InternalCompressionApi
fun String.encodeToCP437(): ByteArray {
    val result = ByteArray(length)
    for (index in indices) {
        val codepoint = this[index]
        if (codepoint.code > 0xFF) throw DataFormatException("Character outside CP437 range")
        result[index] = reverseLookup[codepoint] ?: throw DataFormatException("Character outside CP437 range")
    }
    return result
}

@InternalCompressionApi
fun Sink.writeCP437String(value: String) = write(value.encodeToCP437())