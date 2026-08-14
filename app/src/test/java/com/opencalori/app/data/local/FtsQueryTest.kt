package com.opencalori.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `a single word becomes a bare prefix match`() {
        // The quoted form ("гречк"*) matches exactly and never prefix-matches in FTS4.
        assertEquals("гречк*", FtsQuery.build("гречк"))
    }

    @Test
    fun `the query is never quoted`() {
        assertFalse(FtsQuery.build("молоко").orEmpty().contains('"'))
    }

    @Test
    fun `several words are joined with AND`() {
        assertEquals("грудка* AND кур*", FtsQuery.build("грудка кур"))
    }

    @Test
    fun `extra whitespace is ignored`() {
        assertEquals("рис* AND бурый*", FtsQuery.build("   рис    бурый  "))
    }

    @Test
    fun `quotes cannot escape the match expression`() {
        assertEquals("сыр*", FtsQuery.build("сыр\""))
    }

    @Test
    fun `a user-typed star does not double up`() {
        assertEquals("молоко*", FtsQuery.build("молоко*"))
    }

    @Test
    fun `brackets and punctuation are neutralised`() {
        assertEquals("хлеб*", FtsQuery.build("(хлеб)"))
        assertEquals("творог*", FtsQuery.build("творог,"))
    }

    @Test
    fun `a lone hyphen does not become a NOT operator`() {
        assertNull(FtsQuery.build("-"))
    }

    @Test
    fun `hyphenated words become separate tokens like the index has them`() {
        assertEquals("кока* AND кола*", FtsQuery.build("кока-кола"))
    }

    @Test
    fun `fts operators typed as words are dropped`() {
        assertEquals("рис*", FtsQuery.build("рис AND"))
        assertNull(FtsQuery.build("OR"))
    }

    @Test
    fun `blank input has no query`() {
        assertNull(FtsQuery.build(""))
        assertNull(FtsQuery.build("    "))
    }

    @Test
    fun `punctuation only input has no query`() {
        assertNull(FtsQuery.build("\"\""))
        assertNull(FtsQuery.build("..."))
    }

    @Test
    fun `case is left to the tokenizer to fold`() {
        assertEquals("Гречка*", FtsQuery.build("Гречка"))
    }
    @Test
    fun `expanded query retains inflection and adds a safe Russian stem`() {
        assertEquals("(\u043a\u0443\u0440\u0438\u0446\u0435\u0439* OR \u043a\u0443\u0440\u0438\u0446*)", FtsQuery.buildExpanded("\u043a\u0443\u0440\u0438\u0446\u0435\u0439"))
    }
    @Test
    fun `expanded query leaves short Russian words unchanged`() {
        assertEquals("\u0441\u0443\u0448\u0438*", FtsQuery.buildExpanded("\u0441\u0443\u0448\u0438"))
    }
}
