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

/** Result of the first AI recognition pass. */
data class RecognizedFood(
    val name: String,
    val confidence: Float = 1f
)

/** Result of the second AI estimation pass (after user correction). */
data class EstimatedFoodItem(
    val name: String,
    val grams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val notes: String = ""
)
