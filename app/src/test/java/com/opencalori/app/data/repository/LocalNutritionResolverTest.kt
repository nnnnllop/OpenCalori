package com.opencalori.app.data.repository

import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.Product
import com.opencalori.app.testing.FakeProductRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNutritionResolverTest {
    private val products = FakeProductRepository()
    private val resolver = LocalNutritionResolver(products)

    @Test
    fun `local catalogue replaces AI macros while preserving estimated weight`() = runTest {
        products.catalogue.value = listOf(
            Product(1, "Chicken breast", 165f, 31f, 3.6f, 0f)
        )

        val resolution = resolver.replaceMacros(
            listOf(
                EstimatedIngredient(
                    name = "Chicken breast",
                    rawGrams = 120f,
                    cookedGrams = 90f,
                    caloriesPer100g = 999f,
                    proteinPer100g = 99f,
                    fatPer100g = 99f,
                    carbsPer100g = 99f
                )
            )
        )

        assertTrue(resolution.isComplete)
        val item = resolution.resolved.single()
        assertEquals(90f, item.cookedGrams)
        assertEquals(120f, item.rawGrams)
        assertEquals(165f, item.caloriesPer100g)
        assertEquals(31f, item.proteinPer100g)
        assertEquals(3.6f, item.fatPer100g)
        assertEquals(0f, item.carbsPer100g)
    }

    @Test
    fun `resolver uses exact normalised match before fuzzy candidates`() = runTest {
        products.catalogue.value = listOf(
            Product(1, "Oat meal", 380f, 13f, 7f, 67f),
            Product(2, "Oatmeal", 370f, 12f, 6f, 65f)
        )

        val result = resolver.resolve("  OATMEAL! ")

        assertEquals(2L, result?.id)
    }

    @Test
    fun `unmatched ingredients are never assigned guessed macros`() = runTest {
        products.catalogue.value = listOf(Product(1, "Rice", 130f, 2.7f, 0.3f, 28f))

        val resolution = resolver.replaceMacros(
            listOf(
                EstimatedIngredient("Unknown dish", 100f, 100f, 250f, 5f, 5f, 40f)
            )
        )

        assertFalse(resolution.isComplete)
        assertTrue(resolution.resolved.isEmpty())
        assertEquals(listOf("Unknown dish"), resolution.unmatchedNames)
    }
}
