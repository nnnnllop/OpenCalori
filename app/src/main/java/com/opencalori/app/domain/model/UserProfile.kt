package com.opencalori.app.domain.model

enum class Gender { MALE, FEMALE }

enum class ActivityLevel(val factor: Double, val labelRes: String) {
    SEDENTARY(1.2, "Сидячий образ жизни"),
    LIGHT(1.375, "Лёгкая активность (1–3 трен./нед.)"),
    MODERATE(1.55, "Умеренная активность (3–5 трен./нед.)"),
    ACTIVE(1.725, "Высокая активность (6–7 трен./нед.)"),
    VERY_ACTIVE(1.9, "Очень высокая активность")
}

enum class Goal(val calorieDelta: Int, val labelRes: String) {
    LOSE(-500, "Похудение"),
    MAINTAIN(0, "Удержание веса"),
    GAIN(300, "Набор мышечной массы")
}

data class UserProfile(
    val gender: Gender = Gender.MALE,
    val age: Int = 25,
    val heightCm: Float = 175f,
    val weightKg: Float = 70f,
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.MAINTAIN,
    val onboardingCompleted: Boolean = false,
    // AI settings
    val aiEnabled: Boolean = true,
    val aiSkipListReview: Boolean = false,      // skip "edit recognized list" step
    val aiSkipGramsReview: Boolean = false,     // skip "edit grams" step
    val aiSkipFinalReview: Boolean = false      // skip "final confirmation" step
)

data class CalorieGoal(
    val bmr: Double,
    val tdee: Double,
    val targetCalories: Int,
    val proteinGrams: Int,
    val fatGrams: Int,
    val carbsGrams: Int
)
