package com.opencalori.app.ui.util

/**
 * Shared safety rules for quantities entered in food-related forms. The upper bound avoids
 * accidental entries such as 100000 g while keeping multi-portion meals possible.
 */
object FoodQuantityValidation {
    const val MIN_GRAMS = 0.1f
    const val MAX_GRAMS = 5000f

    fun isValid(grams: Float): Boolean = grams.isFinite() && grams in MIN_GRAMS..MAX_GRAMS

    fun errorMessage(grams: Float): String? = when {
        !grams.isFinite() || grams < MIN_GRAMS -> "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043c\u0430\u0441\u0441\u0443 \u043e\u0442 0,1 \u0433"
        grams > MAX_GRAMS -> "\u041c\u0430\u043a\u0441\u0438\u043c\u0443\u043c \u0434\u043b\u044f \u043e\u0434\u043d\u043e\u0439 \u0437\u0430\u043f\u0438\u0441\u0438 — 5 000 \u0433"
        else -> null
    }
}
