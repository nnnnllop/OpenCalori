package com.opencalori.app.domain.model

enum class MealType(val label: String) {
    BREAKFAST("Завтрак"),
    LUNCH("Обед"),
    DINNER("Ужин"),
    SNACK("Перекус")
}

/** A single food entry inside a meal (either from AI scan or manual input). */
data class FoodItem(
    val id: Long = 0,
    val name: String,
    val grams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
) {
    val calories: Float get() = caloriesPer100g * grams / 100f
    val protein: Float get() = proteinPer100g * grams / 100f
    val fat: Float get() = fatPer100g * grams / 100f
    val carbs: Float get() = carbsPer100g * grams / 100f
}

data class Meal(
    val id: Long = 0,
    val dateEpochDay: Long,        // LocalDate.toEpochDay()
    val mealType: MealType,
    val items: List<FoodItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalCalories: Float get() = items.sumOf { it.calories.toDouble() }.toFloat()
    val totalProtein: Float get() = items.sumOf { it.protein.toDouble() }.toFloat()
    val totalFat: Float get() = items.sumOf { it.fat.toDouble() }.toFloat()
    val totalCarbs: Float get() = items.sumOf { it.carbs.toDouble() }.toFloat()
}

data class WeightEntry(
    val dateEpochDay: Long,
    val weightKg: Float
)

/** Product from the offline local database. */
data class Product(
    val id: Long,
    val name: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
)
