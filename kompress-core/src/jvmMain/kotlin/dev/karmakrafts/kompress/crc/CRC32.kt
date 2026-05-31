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

@file:JvmName("CRC32$")

package dev.karmakrafts.kompress.crc

import dev.karmakrafts.kompress.util.has256BitSimd

// Decide which implementation we use based on module presence
private val crc32Factory: (UInt, UInt) -> CRC32 by lazy {
    if (has256BitSimd) ::FastCRC32
    else ::CRC32Impl
}

actual fun CRC32(polynomial: UInt, initialValue: UInt): CRC32 = crc32Factory(polynomial, initialValue)