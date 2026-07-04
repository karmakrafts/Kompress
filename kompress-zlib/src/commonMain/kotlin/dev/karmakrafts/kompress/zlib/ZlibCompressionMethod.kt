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

package dev.karmakrafts.kompress.zlib

/**
 * Defines the compression methods supported by the Zlib wrapper.
 *
 * @property encodedValue The method identifier stored in the CMF header byte.
 */
enum class ZlibCompressionMethod(val encodedValue: UByte) {
    /** DEFLATE compression method. */
    DEFLATE(0x08U);

    companion object {
        /**
         * Resolves a compression method by its encoded identifier.
         *
         * @param encodedValue The encoded method identifier.
         * @return The matching compression method.
         */
        fun byEncodedValue(encodedValue: UByte): ZlibCompressionMethod = entries.first { method ->
            method.encodedValue == encodedValue
        }
    }
}