package com.opencalori.app.data.repository

import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.Product
import com.opencalori.app.testing.FakeDishRepository
import com.opencalori.app.testing.FakeProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoNutritionResolverTest {
    private val dishes = FakeDishRepository()
    private val products = FakeProductRepository()
    private val resolver = PhotoNutritionResolver(dishes, LocalNutritionResolver(products))

    @Test
    fun `known dish alias uses local serving and macros`() = runTest {
        dishes.catalogue.value = listOf(
            Dish(
                id = 10,
                name = "\u041f\u0430\u0441\u0442\u0430 \u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0430",
                aliases = listOf("carbonara", "\u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0443"),
                ingredients = listOf("\u043f\u0430\u0441\u0442\u0430"),
                portionGrams = 280f,
                caloriesPer100g = 210f,
                proteinPer100g = 11f,
                fatPer100g = 12f,
                carbsPer100g = 22f
            )
        )

        val result = resolver.resolve("carbonara", listOf("\u043f\u0430\u0441\u0442\u0430"))

        assertFalse(result.isDraft)
        assertTrue(result.isComplete)
        assertEquals("\u041f\u0430\u0441\u0442\u0430 \u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0430", result.matchedDish?.name)
        assertEquals(280f, result.items.single().cookedGrams, 0.01f)
        assertEquals(210f, result.items.single().caloriesPer100g, 0.01f)
    }

    @Test
    fun `unknown dish stays a local draft while matched ingredients use local macros`() = runTest {
        products.catalogue.value = listOf(
            Product(1, "\u041a\u0443\u0440\u0438\u0446\u0430", 170f, 30f, 4f, 0f)
        )

        val result = resolver.resolve("\u0414\u043e\u043c\u0430\u0448\u043d\u0435\u0435 \u0431\u043b\u044e\u0434\u043e", listOf("\u043a\u0443\u0440\u0438\u0446\u0430"))

        assertTrue(result.isDraft)
        assertTrue(result.isComplete)
        assertNull(result.matchedDish)
        assertEquals("\u041a\u0443\u0440\u0438\u0446\u0430", result.items.single().name)
        assertEquals(100f, result.items.single().cookedGrams, 0.01f)
        assertEquals(170f, result.items.single().caloriesPer100g, 0.01f)
    }

    @Test
    fun `unmatched ingredient keeps the draft incomplete and does not invent macros`() = runTest {
        products.catalogue.value = listOf(
            Product(1, "\u041a\u0443\u0440\u0438\u0446\u0430", 170f, 30f, 4f, 0f)
        )

        val result = resolver.resolve(
            "\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435 \u0431\u043b\u044e\u0434\u043e",
            listOf("\u043a\u0443\u0440\u0438\u0446\u0430", "\u043e\u0431\u043b\u0430\u0447\u043d\u044b\u0439 \u0441\u043e\u0443\u0441")
        )

        assertTrue(result.isDraft)
        assertFalse(result.isComplete)
        assertEquals(listOf("\u043e\u0431\u043b\u0430\u0447\u043d\u044b\u0439 \u0441\u043e\u0443\u0441"), result.unmatchedNames)
        assertEquals(1, result.items.size)
    }
}
