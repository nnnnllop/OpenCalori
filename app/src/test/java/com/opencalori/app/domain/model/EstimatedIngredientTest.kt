package com.opencalori.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EstimatedIngredientTest {

    private val delta = 0.01f

    private fun buckwheat() = EstimatedIngredient(
        name = "Гречка",
        rawGrams = 80f,
        cookedGrams = 200f,
        caloriesPer100g = 130f,
        proteinPer100g = 4.5f,
        fatPer100g = 1.2f,
        carbsPer100g = 25f,
        notes = "варёная"
    )

    @Test
    fun `calories are counted from the cooked weight`() {
        assertEquals(260f, buckwheat().totalCalories, delta)
    }

    @Test
    fun `macros are counted from the cooked weight`() {
        val item = buckwheat()
        assertEquals(9f, item.totalProtein, delta)
        assertEquals(2.4f, item.totalFat, delta)
        assertEquals(50f, item.totalCarbs, delta)
    }

    @Test
    fun `raw weight is used when cooked weight is unknown`() {
        val item = buckwheat().copy(cookedGrams = 0f)
        assertEquals(80f, item.effectiveGrams, delta)
    }

    @Test
    fun `yield ratio reflects how much the food gained while cooking`() {
        assertEquals(2.5f, buckwheat().yieldRatio, delta)
    }

    @Test
    fun `editing the raw weight scales the cooked weight`() {
        // The old UI let you edit the raw weight and then ignored it entirely.
        val item = buckwheat().withRawGrams(40f)
        assertEquals(40f, item.rawGrams, delta)
        assertEquals(100f, item.cookedGrams, delta)
        assertEquals(130f, item.totalCalories, delta)
    }

    @Test
    fun `editing the cooked weight scales the raw weight back`() {
        val item = buckwheat().withCookedGrams(300f)
        assertEquals(300f, item.cookedGrams, delta)
        assertEquals(120f, item.rawGrams, delta)
    }

    @Test
    fun `a salad with no cooking keeps both weights in step`() {
        val salad = EstimatedIngredient(
            name = "Салат",
            rawGrams = 150f,
            cookedGrams = 150f,
            caloriesPer100g = 40f,
            proteinPer100g = 1f,
            fatPer100g = 3f,
            carbsPer100g = 3f
        )
        val updated = salad.withCookedGrams(200f)
        assertEquals(200f, updated.rawGrams, delta)
        assertEquals(200f, updated.cookedGrams, delta)
    }

    @Test
    fun `converting to a diary item carries the eaten weight`() {
        val food = buckwheat().toFoodItem()
        assertEquals("Гречка", food.name)
        assertEquals(200f, food.grams, delta)
        assertEquals(260f, food.calories, delta)
    }

    @Test
    fun `each ingredient gets a unique id for stable list editing`() {
        val a = buckwheat()
        val b = buckwheat()
        assert(a.id != b.id)
    }
}
