package com.opencalori.app.data.local

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class DishesAssetDatabaseTest {
    companion object {
        private const val ASSET = "src/main/assets/databases/dishes_catalog.db"
        private const val EXPECTED_IDENTITY_HASH = "c54fee39f9f24ee1b5ae03371c252866"
        private lateinit var connection: Connection

        @BeforeClass
        @JvmStatic
        fun open() {
            val file = File(ASSET)
            assertTrue("Missing bundled dish database at $ASSET", file.exists())
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.absolutePath)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            connection.close()
        }
    }

    private fun scalar(sql: String): String? =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun search(input: String): List<String> {
        val query = FtsQuery.buildExpanded(input) ?: return emptyList()
        val sql = """
            SELECT d.name FROM dishes d
            JOIN dishes_fts fts ON d.id = fts.docid
            WHERE dishes_fts MATCH ?
            ORDER BY LENGTH(d.name), d.name
            LIMIT 50
        """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, query)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    @Test
    fun `dish fts index uses unicode61 tokenizer`() {
        val ddl = scalar("SELECT sql FROM sqlite_master WHERE name = \"dishes_fts\"").orEmpty()
        assertTrue(ddl.contains("unicode61"))
    }

    @Test
    fun `dish asset has the Room identity hash`() {
        assertEquals(EXPECTED_IDENTITY_HASH, scalar("SELECT identity_hash FROM room_master_table WHERE id = 42"))
    }

    @Test
    fun `dish catalogue exceeds the minimum curated size`() {
        val count = scalar("SELECT COUNT(*) FROM dishes")?.toInt() ?: 0
        assertTrue("Only $count dishes bundled", count >= 1_000)
    }

    @Test
    fun `dish macros and serving sizes are plausible`() {
        val invalid = scalar("""
            SELECT COUNT(*) FROM dishes
            WHERE portionGrams <= 0
               OR caloriesPer100g < 0 OR caloriesPer100g > 950
               OR proteinPer100g < 0 OR proteinPer100g > 100
               OR fatPer100g < 0 OR fatPer100g > 100
               OR carbsPer100g < 0 OR carbsPer100g > 100
        """.trimIndent())?.toInt() ?: 0
        assertEquals(0, invalid)
    }

    @Test
    fun `dish synonyms and Russian inflection retrieve canonical dishes`() {
        assertTrue(search("\u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0443").any { it.contains("\u041a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0430") })
        assertTrue(search("sushi").any { it.contains("\u0421\u0443\u0448\u0438") })
    }
}
