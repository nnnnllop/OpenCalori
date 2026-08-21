package com.opencalori.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real Groq rate-limit bodies, captured live while testing 0.4.8. The planner must respect
 * the provider's pause when the window is exhausted - the 0.4.8 bug hammered the API four
 * times in four seconds instead.
 */
class TpmPlannerTest {

    @Test
    fun `exhausted window waits instead of retrying immediately`() {
        // prompt = 5353 - 4500 = 853, free now = 8000 - 7500 - 853 < 512 -> must wait
        val body = "Rate limit reached ... tokens per minute (TPM): " +
            "Limit 8000, Used 7500, Requested 5353. Please try again in 27.4s."
        val plan = TpmPlanner.plan(body, lastReserved = 4500)!!
        assertFalse(plan.retryImmediately)
        // fresh window = limit - prompt - reserve
        assertEquals(8000 - 853 - 256, plan.budget.toLong())
    }

    @Test
    fun `partially used window with room retries immediately`() {
        // prompt = 853, free = 8000 - 6302 - 853 = 845 >= floor -> immediate, fitted 845
        val body = "Rate limit reached ... Limit 8000, Used 6302, Requested 5353. " +
            "Please try again in 27.4s."
        val plan = TpmPlanner.plan(body, lastReserved = 4500)!!
        assertTrue(plan.retryImmediately)
        assertEquals(845, plan.budget)
    }

    @Test
    fun `window with room retries immediately with the fitted budget`() {
        val body = "Rate limit reached ... Limit 8000, Used 1000, Requested 5000. " +
            "Please try again in 5.4s."
        val plan = TpmPlanner.plan(body, lastReserved = 4500)!!
        assertTrue(plan.retryImmediately)
        // prompt = 500, free = 8000 - 1000 - 500 = 6500
        assertEquals(6500, plan.budget)
    }

    @Test
    fun `413 request-too-large body without Used plans a clean window`() {
        val body = "Request too large for model ... (TPM): Limit 8000, Requested 16830, " +
            "please reduce your message size and try again."
        val plan = TpmPlanner.plan(body, lastReserved = 16384)!!
        // prompt = 446, nothing used yet -> the window has room right now (verified live
        // on Groq: this exact chain clamped to 7554 and succeeded on the immediate retry).
        assertTrue(plan.retryImmediately)
        assertEquals(7554, plan.budget)
    }

    @Test
    fun `body without quota numbers yields no plan`() {
        assertNull(TpmPlanner.plan("Сбой на стороне провайдера (503).", lastReserved = 1000))
        assertNull(TpmPlanner.plan("", lastReserved = 1000))
    }

    @Test
    fun `tiny free space falls back to the floor budget and waits`() {
        val body = "Limit 8000, Used 7900, Requested 6000. Please try again in 12s."
        val plan = TpmPlanner.plan(body, lastReserved = 5500)!!
        // prompt = 500, free = -400 -> fresh window path
        assertFalse(plan.retryImmediately)
        assertEquals((8000 - 500 - 256).toInt(), plan.budget)
    }
}
