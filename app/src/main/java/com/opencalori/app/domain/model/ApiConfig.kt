package com.opencalori.app.domain.model

import java.util.UUID

/** BYOK configuration for an OpenAI-compatible API. */
data class ApiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val modelId: String = "gpt-4o"
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && modelId.isNotBlank()

    /** Normalized chat-completions endpoint. Tolerates URLs that already include the path. */
    val chatCompletionsUrl: String
        get() {
            val base = baseUrl.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else base + "/chat/completions"
        }
}

enum class ValidationStatus {
    IDLE, VALIDATING, SUCCESS, NO_VISION, AUTH_ERROR, NETWORK_ERROR, UNKNOWN_ERROR
}

data class ApiValidationResult(
    val status: ValidationStatus,
    val message: String = ""
)

/**
 * Result of the first AI recognition pass for a single visually separate dish.
 *
 * A photo may contain several of them; the pipeline keeps every dish separate all the way
 * into the diary instead of merging names into one string.
 */
data class RecognizedDish(
    val dishName: String,
    val ingredients: List<RecognizedIngredient>,
    /** Model self-reported confidence in 0..1, or null when the model did not report one. */
    val confidence: Float? = null,
    val id: String = UUID.randomUUID().toString()
) {
    /** True when the model admitted it could not name the dish. */
    val isUnknown: Boolean
        get() = dishName.isBlank() || UNKNOWN_NAMES.any { dishName.trim().lowercase() == it }

    /** Low confidence is surfaced in the UI so the user double-checks before saving. */
    val isLowConfidence: Boolean get() = confidence != null && confidence < LOW_CONFIDENCE

    companion object {
        const val LOW_CONFIDENCE = 0.6f
        const val UNKNOWN_LABEL = "\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435 \u0431\u043b\u044e\u0434\u043e"
        private val UNKNOWN_NAMES = setOf(
            "unknown",
            "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e",
            "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435 \u0431\u043b\u044e\u0434\u043e",
            "\u043d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u043e\u043f\u0440\u0435\u0434\u0435\u043b\u0438\u0442\u044c"
        )
    }
}

data class RecognizedIngredient(
    val name: String,
    val id: String = UUID.randomUUID().toString(),
    val confidence: Float? = null,
    /**
     * Weight the model claims to actually see, in grams. Almost always null: the contract
     * forbids inventing grams, and a null here means "the user has to weigh it".
     */
    val visibleQuantityGrams: Float? = null
) {
    val isLowConfidence: Boolean
        get() = confidence != null && confidence < RecognizedDish.LOW_CONFIDENCE
}

/**
 * Result of the second AI estimation pass (after user correction of ingredients).
 *
 * Macros are always expressed per 100 g of the product **as eaten** (cooked/prepared),
 * which is how standard nutrition tables work. [rawGrams] lets the user reason in dry
 * terms ("I cooked 80 g of buckwheat"); both weights stay linked by the cooking yield.
 */
data class EstimatedIngredient(
    val name: String,
    val rawGrams: Float,
    val cookedGrams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val notes: String = "",
    val id: String = UUID.randomUUID().toString()
) {
    /** Grams actually eaten - always the cooked weight when it is known. */
    val effectiveGrams: Float get() = if (cookedGrams > 0f) cookedGrams else rawGrams

    val totalCalories: Float get() = caloriesPer100g * effectiveGrams / 100f
    val totalProtein: Float get() = proteinPer100g * effectiveGrams / 100f
    val totalFat: Float get() = fatPer100g * effectiveGrams / 100f
    val totalCarbs: Float get() = carbsPer100g * effectiveGrams / 100f

    /** cooked / raw. 1.0 when one of the weights is unknown. */
    val yieldRatio: Float
        get() = if (rawGrams > 0f && cookedGrams > 0f) cookedGrams / rawGrams else 1f

    /** Editing the raw weight scales the cooked weight by the same yield ratio. */
    fun withRawGrams(value: Float): EstimatedIngredient {
        val ratio = yieldRatio
        return copy(rawGrams = value, cookedGrams = value * ratio)
    }

    /** Editing the cooked weight scales the raw weight back by the yield ratio. */
    fun withCookedGrams(value: Float): EstimatedIngredient {
        val ratio = yieldRatio
        return copy(cookedGrams = value, rawGrams = if (ratio > 0f) value / ratio else value)
    }

    fun toFoodItem() = FoodItem(
        name = name,
        grams = effectiveGrams,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g
    )
}
