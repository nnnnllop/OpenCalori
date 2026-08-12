package com.opencalori.app.data.backup

import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    private val delta = 0.001f

    private fun snapshot() = BackupSnapshot(
        profile = UserProfile(
            gender = Gender.FEMALE,
            age = 33,
            heightCm = 168f,
            weightKg = 61.5f,
            activityLevel = ActivityLevel.MODERATE,
            goal = Goal.LOSE,
            onboardingCompleted = true,
            aiEnabled = true,
            aiSkipGramsReview = true
        ),
        meals = listOf(
            Meal(
                id = 7,
                dateEpochDay = 20_000,
                mealType = MealType.BREAKFAST,
                createdAt = 1_700_000_000_000,
                items = listOf(
                    FoodItem(id = 1, name = "Овсянка", grams = 250f, caloriesPer100g = 88f, proteinPer100g = 3f, fatPer100g = 1.7f, carbsPer100g = 15f),
                    FoodItem(id = 2, name = "Банан", grams = 120f, caloriesPer100g = 96f, proteinPer100g = 1.5f, fatPer100g = 0.2f, carbsPer100g = 21.8f)
                )
            )
        ),
        weights = listOf(WeightEntry(20_000, 61.5f), WeightEntry(20_007, 61f)),
        customProducts = listOf(
            Product(id = 3, name = "Мой протеин", caloriesPer100g = 380f, proteinPer100g = 75f, fatPer100g = 5f, carbsPer100g = 8f, source = ProductSource.CUSTOM)
        ),
        exportedAt = 1_700_000_000_000
    )

    @Test
    fun `a full snapshot survives a round trip`() {
        val restored = BackupSerializer.decode(BackupSerializer.encode(snapshot()))

        assertEquals(1, restored.meals.size)
        assertEquals(2, restored.meals.first().items.size)
        assertEquals(2, restored.weights.size)
        assertEquals(1, restored.customProducts.size)
    }

    @Test
    fun `profile values are preserved exactly`() {
        val restored = BackupSerializer.decode(BackupSerializer.encode(snapshot())).profile

        assertEquals(Gender.FEMALE, restored.gender)
        assertEquals(33, restored.age)
        assertEquals(168f, restored.heightCm, delta)
        assertEquals(61.5f, restored.weightKg, delta)
        assertEquals(ActivityLevel.MODERATE, restored.activityLevel)
        assertEquals(Goal.LOSE, restored.goal)
        assertTrue(restored.aiSkipGramsReview)
    }

    @Test
    fun `meal type and date are preserved`() {
        val meal = BackupSerializer.decode(BackupSerializer.encode(snapshot())).meals.first()

        assertEquals(MealType.BREAKFAST, meal.mealType)
        assertEquals(20_000L, meal.dateEpochDay)
    }

    @Test
    fun `calorie totals are unchanged after a round trip`() {
        val original = snapshot().meals.first().totalCalories
        val restored = BackupSerializer.decode(BackupSerializer.encode(snapshot())).meals.first()

        assertEquals(original, restored.totalCalories, 0.01f)
    }

    @Test
    fun `the export is human-readable json`() {
        val text = BackupSerializer.encode(snapshot())

        assertTrue(text.contains("formatVersion"))
        assertTrue(text.contains("Овсянка"))
        assertTrue(text.contains("\n"))
    }

    @Test
    fun `an empty diary exports and imports cleanly`() {
        val empty = BackupSnapshot(UserProfile(), emptyList(), emptyList(), emptyList())
        val restored = BackupSerializer.decode(BackupSerializer.encode(empty))

        assertTrue(restored.meals.isEmpty())
        assertTrue(restored.weights.isEmpty())
    }

    @Test
    fun `unknown enum values fall back instead of crashing the import`() {
        val text = "{\"formatVersion\":1,\"profile\":{\"gender\":\"ROBOT\",\"activityLevel\":\"TELEPORT\"}}"
        val restored = BackupSerializer.decode(text)

        assertEquals(Gender.MALE, restored.profile.gender)
        assertEquals(ActivityLevel.SEDENTARY, restored.profile.activityLevel)
    }

    @Test
    fun `unknown fields from a future version are ignored`() {
        val text = "{\"formatVersion\":1,\"somethingNew\":42,\"meals\":[]}"
        val restored = BackupSerializer.decode(text)

        assertTrue(restored.meals.isEmpty())
    }

    @Test
    fun `a newer format version is refused with a clear message`() {
        val text = "{\"formatVersion\":99}"
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSerializer.decode(text)
        }
        assertTrue(error.message.orEmpty().contains("новой версией"))
    }

    @Test
    fun `garbage input is rejected`() {
        assertThrows(Exception::class.java) { BackupSerializer.decode("это не json") }
    }
}
