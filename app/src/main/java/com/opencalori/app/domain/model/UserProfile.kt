package com.opencalori.app.domain.model

enum class Gender(val label: String) {
    MALE("Мужской"),
    FEMALE("Женский")
}

enum class ActivityLevel(val factor: Double, val label: String, val hint: String) {
    SEDENTARY(1.2, "Сидячий образ жизни", "Офис, мало ходьбы"),
    LIGHT(1.375, "Лёгкая активность", "1-3 тренировки в неделю"),
    MODERATE(1.55, "Умеренная активность", "3-5 тренировок в неделю"),
    ACTIVE(1.725, "Высокая активность", "6-7 тренировок в неделю"),
    VERY_ACTIVE(1.9, "Очень высокая активность", "Физическая работа или две тренировки в день")
}

enum class Goal(val calorieDelta: Int, val label: String, val hint: String) {
    LOSE(-500, "Похудение", "Дефицит 500 ккал"),
    MAINTAIN(0, "Удержание веса", "Норма поддержания"),
    GAIN(300, "Набор мышечной массы", "Профицит 300 ккал")
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
