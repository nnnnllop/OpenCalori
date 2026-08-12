package com.opencalori.app.domain.model

/** BYOK configuration for an OpenAI-compatible API. */
data class ApiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelId: String = "gpt-4o"
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank()
}

enum class ValidationStatus {
    IDLE, VALIDATING, SUCCESS, NO_VISION, AUTH_ERROR, NETWORK_ERROR, UNKNOWN_ERROR
}

data class ApiValidationResult(
    val status: ValidationStatus,
    val message: String = ""
)

/** Result of the first AI recognition pass: dish name + ingredient list. */
data class RecognizedDish(
    val dishName: String,
    val ingredients: List<RecognizedIngredient>
)

data class RecognizedIngredient(
    val name: String,
    val confidence: Float = 1f
)

/** Result of the second AI estimation pass (after user correction of ingredients). */
data class EstimatedIngredient(
    val name: String,
    val rawGrams: Float,           // weight in raw/dry form
    val cookedGrams: Float,        // weight in cooked/prepared form
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val notes: String = ""         // e.g. "варёный", "жареный", "сухой"
) {
    /** Effective grams to use for calculation (cooked by default). */
    val effectiveGrams: Float get() = if (cookedGrams > 0) cookedGrams else rawGrams
    val totalCalories: Float get() = caloriesPer100g * effectiveGrams / 100f
    val totalProtein: Float get() = proteinPer100g * effectiveGrams / 100f
    val totalFat: Float get() = fatPer100g * effectiveGrams / 100f
    val totalCarbs: Float get() = carbsPer100g * effectiveGrams / 100f
}
