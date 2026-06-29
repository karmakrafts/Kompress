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

package dev.karmakrafts.kompress.archive

import kotlinx.io.Buffer

abstract class AbstractBufferedUnarchiver<E, M : Enum<M>> : Unarchiver<E, M> {
    protected val buffer: Buffer = Buffer()

    // Allows buffering N bytes on demand based on the bytes already in the buffer
    protected fun ensureBufferFilled(size: Long): Boolean {
        var missing = size - buffer.size
        var read = source.readAtMostTo(buffer, missing)
        if (read == -1L) return false
        missing -= read
        while (missing > 0) {
            read = source.readAtMostTo(buffer, missing)
            if (read == -1L) break
            missing -= read
        }
        return missing == 0L
    }
}