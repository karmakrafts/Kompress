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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalCompressionApi::class)
class LZ77EncodeTest {
    @Test
    fun `empty input`() {
        val lz77 = LZ77()
        val tokens = lz77.encode(byteArrayOf())
        assertTrue(tokens.isEmpty(), "Tokens should be empty for empty input")
    }

    @Test
    fun `small input`() {
        val lz77 = LZ77()
        val data = byteArrayOf(1, 2)
        val tokens = lz77.encode(data)
        assertEquals(2, tokens.size)
        assertIs<Token.Literal>(tokens[0])
        assertEquals(1.toUByte(), (tokens[0] as Token.Literal).value)
        assertIs<Token.Literal>(tokens[1])
        assertEquals(2.toUByte(), (tokens[1] as Token.Literal).value)
    }

    @Test
    fun `no matches`() {
        val lz77 = LZ77()
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val tokens = lz77.encode(data)
        assertEquals(5, tokens.size)
        tokens.forEachIndexed { index, token ->
            assertIs<Token.Literal>(token)
            assertEquals((index + 1).toUByte(), (token).value)
        }
    }

    @Test
    fun `simple match`() {
        val lz77 = LZ77()
        val data = "abcabc ".encodeToByteArray()
        val tokens = lz77.encode(data)
        assertEquals(5, tokens.size)
        assertEquals(Token.Literal('a'.code.toUByte()), tokens[0])
        assertEquals(Token.Literal('b'.code.toUByte()), tokens[1])
        assertEquals(Token.Literal('c'.code.toUByte()), tokens[2])
        assertEquals(Token.Match(3, 3), tokens[3])
        assertEquals(Token.Literal(' '.code.toUByte()), tokens[4])
    }

    @Test
    fun `long match`() {
        val lz77 = LZ77(maxMatch = 10)
        val data = "aaaaaaaaaaa ".encodeToByteArray() // 11 'a's + space
        val tokens = lz77.encode(data)
        assertTrue(tokens.any { it is Token.Match })
        val match = tokens.find { it is Token.Match } as Token.Match
        assertEquals(10, match.length)
        assertEquals(1, match.distance)
    }

    @Test
    fun `window size`() {
        val lz77 = LZ77(windowSize = 5)
        val data = "abcdefgabc ".encodeToByteArray()
        val tokens = lz77.encode(data)
        assertTrue(tokens.none { it is Token.Match }, "Should not find match outside window")
    }

    @Test
    fun `overlapping match`() {
        val lz77 = LZ77()
        val data = "abababa ".encodeToByteArray()
        val tokens = lz77.encode(data)
        assertTrue(tokens.any { it is Token.Match })
    }

    @Test
    fun `repeated pattern`() {
        val lz77 = LZ77()
        val data = "abcdeabcdeabcde ".encodeToByteArray()
        val tokens = lz77.encode(data)
        val matches = tokens.filterIsInstance<Token.Match>()
        assertEquals(2, matches.size)
        assertEquals(Token.Match(5, 5), matches[0])
        assertEquals(Token.Match(5, 5), matches[1])
    }

    @Test
    fun `max match`() {
        val lz77 = LZ77(maxMatch = 5)
        val data = "aaaaaaaaaaaaaaaa ".encodeToByteArray()
        val tokens = lz77.encode(data)
        val matches = tokens.filterIsInstance<Token.Match>()
        assertTrue(matches.isNotEmpty())
        for (match in matches) {
            assertTrue(match.length <= 5, "Match length ${match.length} should not exceed maxMatch 5")
        }
    }

    @Test
    fun `different levels`() {
        val data = "abcabcabcabcabcabcabcabcabcabc ".encodeToByteArray()
        for (level in listOf(1, 4, 7, 9)) {
            val lz77 = LZ77(level = level)
            val tokens = lz77.encode(data)
            assertTrue(tokens.any { it is Token.Match }, "Should find matches at level $level")
        }
    }

    @Test
    fun `large distance`() {
        val lz77 = LZ77()
        val builder = StringBuilder()
        builder.append("abc")
        repeat(10000) {
            builder.append('x')
        }
        builder.append("abc ")
        val data = builder.toString().encodeToByteArray()
        val tokens = lz77.encode(data)
        // "abc" at 0 and at 10003. Distance 10003.
        assertTrue(tokens.any { it is Token.Match && it.distance == 10003 }, "Should find match with large distance")
    }

    @Test
    fun `exact window size`() {
        val lz77 = LZ77(windowSize = 5)
        val data = "abcdeabc ".encodeToByteArray()
        val tokens = lz77.encode(data)
        assertTrue(tokens.any { it is Token.Match && it.distance == 5 }, "Should find match at exact window size")
    }
}