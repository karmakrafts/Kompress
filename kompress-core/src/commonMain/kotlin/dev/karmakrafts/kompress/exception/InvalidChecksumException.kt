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

package dev.karmakrafts.kompress.exception

/**
 * Exception thrown when a checksum validation fails during a Kompress operation.
 *
 * @property expectedChecksum The expected checksum value.
 * @property actualChecksum The actual checksum value that was calculated.
 * @constructor Creates a new [InvalidChecksumException] with the given [expectedChecksum] and [actualChecksum].
 */
open class InvalidChecksumException(
    val expectedChecksum: UInt,
    val actualChecksum: UInt,
    message: String? = "Invalid checksum, expected 0x${expectedChecksum.toHexString()} but got 0x${actualChecksum.toHexString()}",
    cause: Throwable? = null
) : KompressException(message, cause)