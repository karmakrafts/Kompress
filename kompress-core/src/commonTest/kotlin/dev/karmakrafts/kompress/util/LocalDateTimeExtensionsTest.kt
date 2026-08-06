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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalCompressionApi::class)
class LocalDateTimeExtensionsTest {
    @Test
    fun testPackLowDateTime() {
        val localDateTime = LocalDate(1980, 1, 1).atTime(0, 0, 0)
        val packedDate = localDateTime.packDateWord()
        val packedTime = localDateTime.packTimeWord()
        assertEquals(packedDate, 33.toUShort())
        assertEquals(packedTime, 0.toUShort())
    }

    @Test
    fun testPackAndUnpackLowDate() {
        val date = LocalDate(1980, 1, 1)
        val time = LocalTime(0, 0, 0)
        val localDateTime = date.atTime(time)
        val packedDate = localDateTime.packDateWord()
        assertEquals(date, packedDate.unpackDateWord())
    }

    @Test
    fun testPackAndUnpackLowTime() {
        val date = LocalDate(1980, 1, 1)
        val time = LocalTime(0, 0, 0)
        val localDateTime = date.atTime(time)
        val packedTime = localDateTime.packTimeWord()
        assertEquals(time, packedTime.unpackTimeWord())
    }

    @Test
    fun testPackAndUnpackHighDate() {
        val date = LocalDate(2099, 12, 31)
        val time = LocalTime(0, 0, 0)
        val localDateTime = date.atTime(time)
        val packedDate = localDateTime.packDateWord()
        assertEquals(date, packedDate.unpackDateWord())
    }

    @Test
    fun testPackAndUnpackHighTime() {
        val date = LocalDate(1980, 1, 1)
        val time = LocalTime(23, 59, 58)
        val localDateTime = date.atTime(time)
        val packedTime = localDateTime.packTimeWord()
        assertEquals(time, packedTime.unpackTimeWord())
    }
}