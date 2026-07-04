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

package dev.karmakrafts.kompress.gzip

import kotlin.time.Instant

/**
 * Metadata describing a single GZip archive entry.
 *
 * @property modificationTime Modification time stored in the header.
 * @property os Originating operating system marker.
 * @property isText Whether the payload should be treated as text data.
 * @property name Optional original entry name.
 * @property comment Optional entry comment.
 * @property extraField Optional extra metadata field stored in the header.
 */
data class GZipEntry( // @formatter:off
    val modificationTime: Instant,
    val os: GZipOs,
    val isText: Boolean = false,
    val name: String? = null,
    val comment: String? = null,
    val extraField: ByteArray? = null
) { // @formatter:on
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GZipEntry

        if (isText != other.isText) return false
        if (modificationTime != other.modificationTime) return false
        if (os != other.os) return false
        if (name != other.name) return false
        if (comment != other.comment) return false
        if (!extraField.contentEquals(other.extraField)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isText.hashCode()
        result = 31 * result + modificationTime.hashCode()
        result = 31 * result + os.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + (extraField?.contentHashCode() ?: 0)
        return result
    }

}