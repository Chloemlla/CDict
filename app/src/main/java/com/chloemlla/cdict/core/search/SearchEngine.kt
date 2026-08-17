package com.chloemlla.cdict.core.search

import com.chloemlla.cdict.core.data.WordEntity

/**
 * Offline search ranking & typo tolerance (PRD §3.1).
 *
 * The database serves raw FTS5 results ordered by frequency; these helpers give the
 * *final* relevance ordering (Exact > Prefix > Frequency) and, when a query finds
 * nothing, a "Did you mean: …" suggestion within a bounded edit distance.
 */
object SearchEngine {

    /**
     * Re-ranks FTS results for [query] so an exact (case-insensitive) headword wins,
     * then prefix matches, then everything else. Within each tier the dictionary's own
     * frequency ordering is preserved (core IELTS words surface first).
     */
    fun reorderForSearch(query: String, words: List<WordEntity>): List<WordEntity> {
        val q = query.trim()
        if (words.size <= 1 || q.isEmpty()) return words
        fun rank(w: WordEntity): Int = when {
            w.word.equals(q, ignoreCase = true) -> 0
            w.word.startsWith(q, ignoreCase = true) -> 1
            else -> 2
        }
        return words.sortedWith(
            compareBy<WordEntity>({ rank(it) }, { it.frequencyGroup }, { it.frequency }, { it.word }),
        )
    }

    /**
     * Builds a safe FTS4 MATCH expression from raw user input.
     *
     * FTS4 treats `"`, `(`, `)`, `:`, `^` and `*` as query operators; a malformed expression
     * (unbalanced quote, stray parenthesis, unknown `column:` filter) throws SQLiteException
     * instead of returning empty results, and a leading `-` is parsed as the NOT operator,
     * silently inverting the search. The common single-word prefix case stays byte-for-byte
     * (`apple` -> `apple*`); any input containing an operator character or a token starting
     * with `-` is quoted token-by-token (embedded quotes doubled) and joined with AND, so
     * arbitrary punctuation cannot crash or hijack the query — at the cost of exact-phrase
     * (non-prefix) matching on that input.
     */
    fun ftsPrefixQuery(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return q
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasOperatorChar = q.any { it in FTS_OPERATOR_CHARS }
        val hasLeadingNegation = tokens.any { it.startsWith("-") }
        if (!hasOperatorChar && !hasLeadingNegation) return "$q*"
        return tokens.joinToString(" AND ") { token ->
            "\"${token.replace("\"", "\"\"")}\""
        }
    }

    /**
     * Returns the closest dictionary word to [query] within [maxDistance] (inclusive)
     * edit distance, or null when nothing is close enough. Confidence quirk: trim before
     * comparing so whitespace never counts as an edit.
     */
    fun suggest(
        query: String,
        candidates: List<WordEntity>,
        maxDistance: Int = 2,
    ): WordEntity? {
        val q = query.trim()
        if (q.isEmpty()) return null
        var best: WordEntity? = null
        var bestDistance = maxDistance + 1
        for (candidate in candidates) {
            val d = levenshtein(candidate.word.lowercase(), q.lowercase())
            if (d < bestDistance || (d == bestDistance && prefer(best?.word, candidate.word))) {
                best = candidate
                bestDistance = d
            }
        }
        return if (bestDistance <= maxDistance) best else null
    }

    private fun prefer(current: String?, candidate: String): Boolean {
        if (current == null) return true
        // Ties: prefer the shorter (closer to the typo) then alphabetical for determinism.
        val c = candidate.length.compareTo(current.length)
        return c < 0 || (c == 0 && candidate < current)
    }

    /** Classic Wagner–Fischer edit distance; small symmetric cost is enough for ≤2 tolerance. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    private const val FTS_OPERATOR_CHARS = "\"():^*"
}