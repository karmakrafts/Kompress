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

import dev.karmakrafts.kompress.zip.ZipGPBF.Companion.OMIT_CHECKSUM_AND_SIZES
import dev.karmakrafts.kompress.zip.ZipGPBF.Companion.PATCHED_DATA
import dev.karmakrafts.kompress.zip.ZipGPBF.Companion.STRONG_ENCRYPTION
import dev.karmakrafts.kompress.zip.ZipGPBF.Deflate
import dev.karmakrafts.kompress.zip.ZipGPBF.LZMA
import kotlin.jvm.JvmInline

/**
 * See [PKWARE APPNOTE](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT) 4.4.4.
 */
sealed interface ZipGPBF {
    companion object {
        const val OMIT_CHECKSUM_AND_SIZES: UShort = 0b00000000_00000100U
        const val PATCHED_DATA: UShort = 0b00000000_00010000U
        const val STRONG_ENCRYPTION: UShort = 0b00000000_00100000U
    }

    @JvmInline
    value class Deflate(override val value: UShort) : ZipGPBF {
        inline val compressionType: ZipDeflateCompressionType
            get() = ZipDeflateCompressionType.byEncodedValue(value and 0b11U)
    }

    @JvmInline
    value class LZMA(override val value: UShort) : ZipGPBF {
        companion object {
            const val EOS_MARKER_PRESENT: UShort = 0b00000000_00000001U
        }

        inline val eosMarkerPresent: Boolean
            get() = value and EOS_MARKER_PRESENT != 0U.toUShort()
    }

    val value: UShort
}

inline val ZipGPBF.deflate: Deflate get() = Deflate(value)
inline val ZipGPBF.lzma: LZMA get() = LZMA(value)

inline val ZipGPBF.omitCheckSumAndSizes: Boolean
    get() = value and OMIT_CHECKSUM_AND_SIZES != 0U.toUShort()

inline val ZipGPBF.isPatchedData: Boolean
    get() = value and PATCHED_DATA != 0U.toUShort()

inline val ZipGPBF.hasStrongEncryption: Boolean
    get() = value and STRONG_ENCRYPTION != 0U.toUShort()