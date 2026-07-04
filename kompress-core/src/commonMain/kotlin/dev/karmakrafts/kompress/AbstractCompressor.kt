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

package dev.karmakrafts.kompress

abstract class AbstractCompressor : Compressor {
    override var input: ByteArray = ByteArray(0)
        protected set
    override var inputOffset: Int = 0
        protected set
    override var inputSize: Int = 0
        protected set
    override var remaining: Int = 0
        protected set

    override var bytesRead: Long = 0L
        protected set
    override var bytesWritten: Long = 0L
        protected set

    override fun setInput(data: ByteArray, offset: Int, size: Int) {
        input = data
        inputOffset = offset
        inputSize = size
        remaining = size
    }
}