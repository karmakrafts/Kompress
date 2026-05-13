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

import kotlin.jvm.JvmInline

/**
 * See [RFC1952](https://datatracker.ietf.org/doc/html/rfc1952) 2.3.1.
 * middle of page 6.
 */
@JvmInline
internal value class GZipEntryFlags(val value: UByte) {
    companion object {
        // @formatter:off
        const val FNONE: UByte    = 0b0000_0000U
        const val FTEXT: UByte    = 0b0000_0001U
        const val FHCRC: UByte    = 0b0000_0010U
        const val FEXTRA: UByte   = 0b0000_0100U
        const val FNAME: UByte    = 0b0000_1000U
        const val FCOMMENT: UByte = 0b0001_0000U
        // @formatter:on
    }

    constructor(
        ftext: Boolean = false,
        fhcrc: Boolean = false,
        fextra: Boolean = false,
        fname: Boolean = false,
        fcomment: Boolean = false
    ) : this( // @formatter:off
            (if(ftext) FTEXT else FNONE)
                or (if(fhcrc) FHCRC else FNONE)
                or (if(fextra) FEXTRA else FNONE)
                or (if(fname) FNAME else FNONE)
                or (if(fcomment) FCOMMENT else FNONE)
    ) // @formatter:on

    inline val ftext: Boolean
        get() = value and FTEXT != FNONE

    inline val fhcrc: Boolean
        get() = value and FHCRC != FNONE

    inline val fextra: Boolean
        get() = value and FEXTRA != FNONE

    inline val fname: Boolean
        get() = value and FNAME != FNONE

    inline val fcomment: Boolean
        get() = value and FCOMMENT != FNONE
}