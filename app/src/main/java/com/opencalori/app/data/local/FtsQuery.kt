package com.opencalori.app.data.local

/** Builds safe SQLite FTS4 MATCH expressions for food search. */
object FtsQuery {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val operators = setOf("and", "or", "not", "near")

    fun build(input: String): String? = tokens(input)
        ?.joinToString(" AND ") { it + "*" }

    fun buildExpanded(input: String): String? = tokens(input)
        ?.joinToString(" AND ") { token ->
            val stem = russianStem(token)
            if (stem == token) token + "*" else "(" + token + "* OR " + stem + "*)"
        }

    private fun tokens(input: String): List<String>? {
        val result = input.trim()
            .split(separators)
            .filter { it.isNotBlank() && it.lowercase() !in operators }
        return result.ifEmpty { null }
    }

    private fun russianStem(token: String): String {
        val lower = token.lowercase()
        if (!lower.all(::isRussianLetter) || lower.length < 5) return token
        val suffix = russianSuffixes.firstOrNull { lower.endsWith(it) && lower.length - it.length >= 3 }
            ?: return token
        return token.dropLast(suffix.length)
    }

    private fun isRussianLetter(char: Char): Boolean {
        val code = char.code
        return code in 0x0430..0x044F || code == 0x0451
    }

    private val russianSuffixes = listOf(
        "\u0438\u044f\u043c\u0438", "\u044f\u043c\u0438", "\u0430\u043c\u0438", "\u043e\u0432\u044b\u043c", "\u0435\u0432\u0430\u043c",
        "\u043e\u0433\u043e", "\u0435\u0433\u043e", "\u044b\u043c\u0438", "\u043e\u043c\u0443", "\u0435\u043c\u0443", "\u0430\u044f",
        "\u0438\u044f", "\u0430\u043c", "\u044f\u043c", "\u0430\u0445", "\u044f\u0445", "\u043e\u043c", "\u0435\u043c",
        "\u0438\u0432", "\u043e\u0432", "\u0435\u0439", "\u043e\u0439", "\u044b\u0435", "\u0438\u0435", "\u043e\u0435",
        "\u044b", "\u0438", "\u0430", "\u044f", "\u0443", "\u044e", "\u0435", "\u043e"
    )
}
