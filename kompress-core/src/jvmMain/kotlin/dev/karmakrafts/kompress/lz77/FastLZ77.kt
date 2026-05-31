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

package dev.karmakrafts.kompress.lz77

internal class FastLZ77( // @formatter:off
    level: Int ,
    override val minMatch: Int,
    override val maxMatch: Int,
    override val windowSize: Int
) : LZ77 { // @formatter:on
    private var maxChain: Int = LZ77.getMaxChainDepth(level)

    override var level: Int = level
        set(value) {
            maxChain = LZ77.getMaxChainDepth(value)
            field = value
        }

    override fun encode( // @formatter:off
        tokens: MutableList<Token>,
        data: ByteArray,
        offset: Int,
        size: Int
    ) { // @formatter:on
        TODO("Not yet implemented")
    }

    override fun reset() {
        TODO("Not yet implemented")
    }
}