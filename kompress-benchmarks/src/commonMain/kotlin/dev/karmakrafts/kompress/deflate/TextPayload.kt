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

package dev.karmakrafts.kompress.deflate

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random

private val VOCABULARY: List<String> = listOf(
    "deflate", "inflate", "kompress", "huffman", "window", "symbol", "literal", "distance",
    "block", "stream", "buffer", "length", "offset", "table", "encode", "decode", "match",
    "checksum", "dictionary", "compression"
)

/**
 * Builds a deterministic, text like payload of [size] bytes.
 *
 * The other inflate benchmarks decompress a single repeated byte, which collapses into a handful of
 * maximum length matches and barely touches the Huffman decoder. Prose sized words interleaved with
 * punctuation keep the literal and short match paths busy instead, which is what real input looks like.
 */
internal fun textBenchmarkData(size: Int = STORED_DATA_SIZE): ByteArray {
    val random = Random(0x5EED)
    val buffer = Buffer()
    var written = 0
    while (written < size) {
        val word = VOCABULARY[random.nextInt(VOCABULARY.size)]
        val separator = if (random.nextInt(12) == 0) ".\n" else " "
        val chunk = (word + separator).encodeToByteArray()
        val length = minOf(chunk.size, size - written)
        buffer.write(chunk, 0, length)
        written += length
    }
    return buffer.readByteArray()
}

/**
 * The text payload every text inflate benchmark decompresses, deflated at the default level.
 *
 * Deflated in chunks rather than in one call, so the stream carries a run of dynamic blocks the way
 * real deflate output of this size does, rather than one outsized block.
 */
internal fun deflatedTextBenchmarkData(chunkSize: Int = 64 * 1024): ByteArray {
    val data = textBenchmarkData()
    val output = Buffer()
    val chunk = ByteArray(4096)
    Deflater(Deflater.DEFAULT_LEVEL).use { deflater ->
        var offset = 0
        while (offset < data.size) {
            val size = minOf(chunkSize, data.size - offset)
            deflater.setInput(data, offset, size)
            offset += size
            if (offset == data.size) deflater.finish()
            while (true) {
                val compressed = deflater.compress(chunk)
                if (compressed == 0) break
                output.write(chunk, 0, compressed)
            }
        }
    }
    return output.readByteArray()
}
