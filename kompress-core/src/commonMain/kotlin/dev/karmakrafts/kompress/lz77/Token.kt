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

import dev.karmakrafts.kompress.InternalCompressionApi

/**
 * Base interface for the two possible LZ77 token types included
 * in any LZ77 token stream.
 */
@InternalCompressionApi
sealed interface Token {
    /**
     * A literal byte token.
     */
    data class Literal(val value: UByte) : Token

    /**
     * A length-distance pair based token.
     */
    data class Match( // @formatter:off
        val length: Int,
        val distance: Int
    ) : Token // @formatter:on
}