package com.opencalori.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodQuantityValidationTest {
    @Test
    fun `accepts ordinary and maximum food quantities`() {
        assertTrue(FoodQuantityValidation.isValid(0.1f))
        assertTrue(FoodQuantityValidation.isValid(250f))
        assertTrue(FoodQuantityValidation.isValid(5_000f))
        assertNull(FoodQuantityValidation.errorMessage(250f))
    }

    @Test
    fun `rejects missing zero and negative quantities`() {
        assertFalse(FoodQuantityValidation.isValid(0f))
        assertFalse(FoodQuantityValidation.isValid(-1f))
        assertEquals("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043c\u0430\u0441\u0441\u0443 \u043e\u0442 0,1 \u0433", FoodQuantityValidation.errorMessage(0f))
    }

    @Test
    fun `rejects accidental excessive and nonfinite quantities`() {
        assertFalse(FoodQuantityValidation.isValid(5_000.1f))
        assertFalse(FoodQuantityValidation.isValid(Float.POSITIVE_INFINITY))
        assertEquals("\u041c\u0430\u043a\u0441\u0438\u043c\u0443\u043c \u0434\u043b\u044f \u043e\u0434\u043d\u043e\u0439 \u0437\u0430\u043f\u0438\u0441\u0438 — 5 000 \u0433", FoodQuantityValidation.errorMessage(5_000.1f))
        assertEquals("\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u043c\u0430\u0441\u0441\u0443 \u043e\u0442 0,1 \u0433", FoodQuantityValidation.errorMessage(Float.NaN))
    }
}
