package com.opencalori.app.domain.model

/**
 * Decides which source is allowed to provide nutrition values during food recognition.
 *
 * LOCAL_DATABASE keeps the entire flow deterministic and offline. AI_ONLY is useful
 * when a user explicitly prefers the model's estimate. HYBRID uses AI only for visual
 * understanding and resolves nutrition values through the local catalogue.
 */
enum class NutritionSourceMode {
    LOCAL_DATABASE,
    AI_ONLY,
    HYBRID;

    val usesAi: Boolean
        get() = this != LOCAL_DATABASE

    val usesLocalCatalogue: Boolean
        get() = this != AI_ONLY

    companion object {
        fun fromStorage(value: String?): NutritionSourceMode =
            value?.let { raw -> entries.firstOrNull { it.name == raw } } ?: AI_ONLY
    }
}
