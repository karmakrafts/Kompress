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

import dev.karmakrafts.kompress.InternalCompressionApi
import dev.karmakrafts.kompress.WrappingCompressor
import dev.karmakrafts.kompress.deflate.Deflater
import dev.karmakrafts.kompress.util.Adler32
import kotlinx.io.writeUByte
import kotlinx.io.writeUInt

@OptIn(InternalCompressionApi::class)
class ZlibCompressor(
    level: Int = Deflater.DEFAULT_LEVEL,
    private val cmf: ZlibCMF = ZlibCMF(),
    private val flags: ZlibFlags = ZlibFlags(ZlibCompressionLevel.fromDeflaterLevel(level))
) : WrappingCompressor(Deflater(level)) {
    private val adler32: Adler32 = Adler32()

    override fun appendPrologue() {
        buffer.writeUByte(cmf.value)
        buffer.writeUByte(flags.withCheckBits(cmf))
    }

    override fun onDataRead(offset: Int, size: Int) {
        adler32.round(input, offset, size)
    }

    override fun appendEpilogue() {
        buffer.writeUInt(adler32.checksum)
    }

    override fun reset() {
        super.reset()
        adler32.reset()
    }
}