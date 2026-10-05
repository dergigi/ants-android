package org.dergigi.ants

import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/** Fuse.js 7.1.0 Bitap, specialized for threshold=.35 and ignoreLocation=true.
 * Copyright Kiro Risk; MIT license in licenses/fuse-js-MIT.txt. */
internal class ResultFuzzyFilter(query: String) {
    private val pattern = query.lowercase(Locale.ROOT)
    private val chunks = if (pattern.length <= 32) listOf(pattern) else buildList {
        var index = 0
        while (index + 32 <= pattern.length) { add(pattern.substring(index, index + 32)); index += 32 }
        if (index < pattern.length) add(pattern.takeLast(32))
    }

    fun score(content: String): Double? {
        val text = content.lowercase(Locale.ROOT)
        if (pattern == text) return 0.0
        val scores = chunks.map { chunkScore(text, it) }
        if (scores.all { it == null }) return null
        val score = scores.sumOf { it ?: 1.0 } / scores.size
        val words = Regex("[^ ]+").findAll(content).count().coerceAtLeast(1)
        val norm = round(1000 / sqrt(words.toDouble())) / 1000
        return score.pow(norm)
    }

    private fun chunkScore(text: String, pattern: String): Double? {
        if (pattern.isEmpty() || text.contains(pattern)) return 0.001
        val length = pattern.length
        val alphabet = mutableMapOf<Char, Int>()
        pattern.forEachIndexed { index, char -> alphabet[char] = (alphabet[char] ?: 0) or (1 shl (length - index - 1)) }
        val finish = text.length + length
        val mask = 1 shl (length - 1)
        var previous = IntArray(finish + 2)
        for (errors in 0 until length) {
            if (errors.toDouble() / length > 0.35) break
            val bits = IntArray(finish + 2)
            bits[finish + 1] = (1 shl errors) - 1
            for (j in finish downTo 1) {
                val charMatch = text.getOrNull(j - 1)?.let { alphabet[it] } ?: 0
                bits[j] = ((bits[j + 1] shl 1) or 1) and charMatch
                if (errors > 0) bits[j] = bits[j] or (((previous[j + 1] or previous[j]) shl 1) or 1 or previous[j + 1])
                if (bits[j] and mask != 0) return max(0.001, errors.toDouble() / length)
            }
            previous = bits
        }
        return null
    }
}
