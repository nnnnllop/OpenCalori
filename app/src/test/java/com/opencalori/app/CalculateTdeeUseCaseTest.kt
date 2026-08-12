package com.opencalori.app

import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateTdeeUseCaseTest {

    private val useCase = CalculateTdeeUseCase()

    @Test
    fun `male BMR uses Mifflin-St Jeor with +5`() {
        val profile = UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.MAINTAIN
        )
        val result = useCase(profile)
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780
        assertEquals(1780.0, result.bmr, 0.1)
    }

    @Test
    fun `female BMR uses Mifflin-St Jeor with -161`() {
        val profile = UserProfile(
            gender = Gender.FEMALE,
            age = 25,
            heightCm = 165f,
            weightKg = 60f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.MAINTAIN
        )
        val result = useCase(profile)
        // BMR = 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        assertEquals(1345.25, result.bmr, 0.1)
    }

    @Test
    fun `TDEE multiplies BMR by activity factor`() {
        val profile = UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = ActivityLevel.MODERATE,
            goal = Goal.MAINTAIN
        )
        val result = useCase(profile)
        assertEquals(1780.0 * 1.55, result.tdee, 0.1)
    }

    @Test
    fun `weight loss goal subtracts 500 kcal`() {
        val profile = UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.LOSE
        )
        val result = useCase(profile)
        val expected = (1780.0 * 1.2 - 500).toInt()
        assertEquals(expected, result.targetCalories)
    }

    @Test
    fun `target calories never below 1200`() {
        val profile = UserProfile(
            gender = Gender.FEMALE,
            age = 60,
            heightCm = 150f,
            weightKg = 45f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.LOSE
        )
        val result = useCase(profile)
        assertTrue(result.targetCalories >= 1200)
    }

    @Test
    fun `protein is 1_6g per kg for maintain`() {
        val profile = UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.MAINTAIN
        )
        val result = useCase(profile)
        assertEquals((1.6 * 80).toInt(), result.proteinGrams)
    }

    @Test
    fun `protein is 2g per kg for gain`() {
        val profile = UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.GAIN
        )
        val result = useCase(profile)
        assertEquals(160, result.proteinGrams)
    }

    @Test
    fun `macro calories roughly sum to target`() {
        val profile = UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 80f,
            activityLevel = ActivityLevel.MODERATE,
            goal = Goal.MAINTAIN
        )
        val result = useCase(profile)
        val macroCal = result.proteinGrams * 4 + result.fatGrams * 9 + result.carbsGrams * 4
        // Allow 10% tolerance due to rounding
        val tolerance = result.targetCalories * 0.1
        assertTrue(
            "Macro calories ($macroCal) should be close to target (${result.targetCalories})",
            kotlin.math.abs(macroCal - result.targetCalories) <= tolerance
        )
    }
}
