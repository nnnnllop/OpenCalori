package com.opencalori.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionSourceModeTest {
    @Test
    fun `unknown or absent persisted value keeps user on recommended AI mode`() {
        assertEquals(NutritionSourceMode.AI_ONLY, NutritionSourceMode.fromStorage(null))
        assertEquals(NutritionSourceMode.AI_ONLY, NutritionSourceMode.fromStorage("obsolete_mode"))
    }

    @Test
    fun `stored enum value restores exactly`() {
        NutritionSourceMode.entries.forEach { mode ->
            assertEquals(mode, NutritionSourceMode.fromStorage(mode.name))
        }
    }

    @Test
    fun `mode capability flags never expose AI in offline mode`() {
        assertFalse(NutritionSourceMode.LOCAL_DATABASE.usesAi)
        assertTrue(NutritionSourceMode.LOCAL_DATABASE.usesLocalCatalogue)
        assertTrue(NutritionSourceMode.AI_ONLY.usesAi)
        assertFalse(NutritionSourceMode.AI_ONLY.usesLocalCatalogue)
        assertTrue(NutritionSourceMode.HYBRID.usesAi)
        assertTrue(NutritionSourceMode.HYBRID.usesLocalCatalogue)
    }
}
