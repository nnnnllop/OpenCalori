package com.opencalori.app.data.local

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real search queries against the database that actually ships in the APK.
 *
 * This is the test that would have caught the original bug: the FTS index was built with
 * the default tokenizer, so a Cyrillic query typed in lowercase - which is how everyone
 * types - matched nothing at all, and the screen just said "Ничего не найдено".
 */
class ProductsAssetDatabaseTest {

    companion object {
        private const val ASSET = "src/main/assets/databases/products.db"
        private const val EXPECTED_IDENTITY_HASH = "c15257b3e491c101b38ed38e4203a2dc"

        private lateinit var connection: Connection

        @BeforeClass
        @JvmStatic
        fun open() {
            val file = File(ASSET)
            assertTrue("Missing bundled database at $ASSET", file.exists())
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.absolutePath)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            connection.close()
        }
    }

    private fun search(input: String): List<String> {
        val match = FtsQuery.build(input) ?: return emptyList()
        val sql = """
            SELECT p.name FROM products p
            JOIN products_fts fts ON p.id = fts.docid
            WHERE products_fts MATCH ?
            ORDER BY LENGTH(p.name), p.name
            LIMIT 50
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, match)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    private fun scalar(sql: String): String? =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    // ---- Schema ----

    @Test
    fun `the fts index uses the unicode61 tokenizer`() {
        val ddl = scalar("SELECT sql FROM sqlite_master WHERE name = 'products_fts'").orEmpty()
        assertTrue("products_fts must be tokenized with unicode61, got: $ddl", ddl.contains("unicode61"))
    }

    @Test
    fun `the identity hash matches what Room expects`() {
        // A mismatch crashes the app on the very first search with IllegalStateException.
        assertEquals(
            EXPECTED_IDENTITY_HASH,
            scalar("SELECT identity_hash FROM room_master_table WHERE id = 42")
        )
    }

    @Test
    fun `the catalogue is worth shipping`() {
        val count = scalar("SELECT COUNT(*) FROM products")?.toInt() ?: 0
        assertTrue("Only $count products bundled; the offline catalogue must contain at least 5,000 validated entries", count >= 5_000)
    }

    @Test
    fun `catalog manifest records its official source and current size`() {
        val manifest = File("src/main/assets/databases/catalog_manifest.json")
        assertTrue("Missing catalogue provenance manifest", manifest.exists())
        val text = manifest.readText()
        assertTrue(text.contains("USDA FoodData Central"))
        assertTrue(text.contains("FNDDS 2021-2023"))
        assertTrue(text.contains("\"products\":"))
    }
    @Test
    fun `every product has plausible macros`() {
        val bad = scalar(
            """
            SELECT COUNT(*) FROM products
            WHERE caloriesPer100g < 0 OR caloriesPer100g > 950
               OR proteinPer100g < 0 OR proteinPer100g > 100
               OR fatPer100g < 0 OR fatPer100g > 100
               OR carbsPer100g < 0 OR carbsPer100g > 100
            """.trimIndent()
        )?.toInt() ?: 0
        assertEquals(0, bad)
    }

    @Test
    fun `product names are unique`() {
        val duplicates = scalar(
            "SELECT COUNT(*) FROM (SELECT name FROM products GROUP BY LOWER(name) HAVING COUNT(*) > 1)"
        )?.toInt() ?: 0
        assertEquals(0, duplicates)
    }

    // ---- Search behaviour ----

    @Test
    fun `lowercase cyrillic finds capitalised products`() {
        assertTrue(search("гречка").isNotEmpty())
        assertTrue(search("молоко").isNotEmpty())
        assertTrue(search("куриц").isNotEmpty())
    }

    @Test
    fun `uppercase input works just as well`() {
        assertEquals(search("гречка").size, search("ГРЕЧКА").size)
        assertEquals(search("молоко").size, search("Молоко").size)
    }

    @Test
    fun `a partially typed word already matches`() {
        // Search runs on every keystroke, so prefixes have to work.
        assertTrue(search("мол").isNotEmpty())
        assertTrue(search("гре").isNotEmpty())
        assertTrue(search("твор").isNotEmpty())
    }

    @Test
    fun `matching gets narrower as the user keeps typing`() {
        val short = search("мол").size
        val long = search("молоко").size
        assertTrue("$short should be at least $long", short >= long)
    }

    @Test
    fun `two words narrow the result down`() {
        val results = search("куриная грудка")
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.contains("уриная", ignoreCase = true) })
    }

    @Test
    fun `words match anywhere in the name not just at the start`() {
        assertTrue(search("варёная").isNotEmpty())
    }

    @Test
    fun `the yo letter is not a dead end`() {
        assertTrue(search("свёкла").isNotEmpty())
    }

    @Test
    fun `nonsense input returns nothing instead of crashing`() {
        assertTrue(search("щщщщщ").isEmpty())
    }

    @Test
    fun `punctuation cannot break the match expression`() {
        // A raw quote used to produce a syntax error on the SQL side.
        search("сыр\"")
        search("хлеб)")
        search("молоко*")
        search("рис AND")
    }

    @Test
    fun `shorter names rank first`() {
        val results = search("молоко")
        assertFalse(results.isEmpty())
        assertEquals(results.minByOrNull { it.length }, results.first())
    }

    @Test
    fun `common everyday foods are all present`() {
        listOf("яйцо", "хлеб", "рис", "банан", "творог", "сыр", "яблоко", "картофель", "кофе")
            .forEach { term ->
                assertTrue("Nothing found for '$term'", search(term).isNotEmpty())
            }
    }
}
