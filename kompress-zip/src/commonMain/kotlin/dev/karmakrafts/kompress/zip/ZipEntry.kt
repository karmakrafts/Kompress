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

package dev.karmakrafts.kompress.zip

import dev.karmakrafts.kompress.ExperimentalCompressionApi
import kotlin.time.Instant

/**
 * Metadata describing a single ZIP entry.
 *
 * @property modificationTime Last modification timestamp stored for the entry.
 * @property name Entry name as written in the archive.
 * @property comment Optional entry comment.
 * @property extraFields Extra field records associated with the entry.
 * @property compressionMethod Compression method used for entry data.
 * @property gpbf General purpose bit flag values used for the entry.
 */
@ExperimentalCompressionApi
data class ZipEntry(
    val modificationTime: Instant,
    val name: String,
    val comment: String? = null,
    val extraFields: ZipExtraFieldContainer = ZipExtraFieldContainer.empty(),
    val compressionMethod: ZipCompressionMethod = ZipCompressionMethod.DEFLATE,
    val gpbf: ZipGPBF = ZipGPBF()
) {
    /**
     * Whether this entry includes ZIP64 extended information in its extra fields.
     */
    val isZip64: Boolean
        get() = extraFields.any { entry -> entry.headerId == ZipExtraFieldEntryHeaderId.ZIP64_EXTENDED_INFORMATION }
}