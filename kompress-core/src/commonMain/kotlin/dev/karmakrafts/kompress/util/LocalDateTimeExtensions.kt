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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number

@InternalCompressionApi
fun UShort.unpackTimeWord(): LocalTime {
    val hours = ((toUInt() shr 11) and 0b11111U).toInt()
    val minutes = ((toUInt() shr 5) and 0b111111U).toInt()
    val seconds = ((toUInt() and 0b11111U) shl 1).toInt()
    return LocalTime(hours, minutes, seconds)
}

@InternalCompressionApi
fun UShort.unpackDateWord(): LocalDate {
    val year = (((toUInt() shr 9) and 0b1111111U) + 1980U).toInt()
    val month = ((toUInt() shr 5) and 0b1111U).toInt()
    val day = (toUInt() and 0b11111U).toInt()
    return LocalDate(year, month, day)
}

@InternalCompressionApi
fun LocalDateTime.packTimeWord(): UShort {
    val hours = (hour and 0b11111).toUInt()
    val minutes = (minute and 0b111111).toUInt()
    val seconds = ((second shr 1) and 0b11111).toUInt()
    return ((hours shl 11) or (minutes shl 5) or seconds).toUShort()
}

@InternalCompressionApi
fun LocalDateTime.packDateWord(): UShort {
    val year = ((year - 1980) and 0b1111111).toUInt()
    val month = (month.number and 0b1111).toUInt()
    val day = (day and 0b11111).toUInt()
    return ((year shl 9) or (month shl 5) or day).toUShort()
}