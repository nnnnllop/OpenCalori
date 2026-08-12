package com.opencalori.app.data.local

/**
 * Builds SQLite FTS4 MATCH expressions for the product search.
 *
 * Two traps live here, both of which silently return zero rows instead of failing:
 *
 * 1. The quoted form `"молок"*` does NOT prefix-match in FTS4 - the star after a closing
 *    quote is ignored and the term is matched exactly. Only the bare `молок*` form works,
 *    so tokens are emitted unquoted and scrubbed of anything with syntactic meaning.
 * 2. The index must use the unicode61 tokenizer, otherwise Cyrillic is never case-folded.
 *
 * Kept as a pure function so both are covered by unit tests.
 */
object FtsQuery {

    /** Anything with a special meaning inside a MATCH expression. */
    private val SPECIAL = Regex("[\"*():^\\-,.;!?/\\\\]")

    /** Bare words that FTS4 would read as operators. */
    private val OPERATORS = setOf("and", "or", "not", "near")

    /**
     * Turns free user input into a prefix-matching AND query, e.g.
     * `грудка кур` -> `грудка* AND кур*`.
     *
     * Returns null when nothing searchable is left, in which case callers should fall
     * back to a plain LIKE query instead of running an invalid MATCH.
     */
    fun build(input: String): String? {
        // Special characters become separators, so "кока-кола" turns into two tokens and
        // still matches the way the tokenizer indexed it.
        val tokens = SPECIAL.replace(input, " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.lowercase() !in OPERATORS }

        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { it + "*" }
    }
}
