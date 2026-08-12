package com.opencalori.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberFormatTest {

    // ---- compact ----

    @Test
    fun `whole numbers lose the decimal tail`() {
        assertEquals("200", NumberFormat.compact(200f))
        assertEquals("0", NumberFormat.compact(0f))
        assertEquals("1", NumberFormat.compact(1.0f))
    }

    @Test
    fun `fractional values keep one decimal`() {
        assertEquals("2.5", NumberFormat.compact(2.5f))
        assertEquals("0.3", NumberFormat.compact(0.3f))
    }

    @Test
    fun `fractional values are rounded to one decimal`() {
        assertEquals("2.6", NumberFormat.compact(2.55f))
        assertEquals("12.3", NumberFormat.compact(12.34f))
    }

    @Test
    fun `non-finite values degrade to zero`() {
        assertEquals("0", NumberFormat.compact(Float.NaN))
        assertEquals("0", NumberFormat.compact(Float.POSITIVE_INFINITY))
    }

    // ---- sanitizeDecimalInput ----

    @Test
    fun `letters and symbols are stripped`() {
        assertEquals("200", NumberFormat.sanitizeDecimalInput("200 г"))
        assertEquals("150", NumberFormat.sanitizeDecimalInput("abc150xyz"))
    }

    @Test
    fun `a comma becomes a dot because russian keyboards produce it`() {
        assertEquals("2.5", NumberFormat.sanitizeDecimalInput("2,5"))
    }

    @Test
    fun `only the first separator survives`() {
        assertEquals("2.55", NumberFormat.sanitizeDecimalInput("2.5.5"))
        assertEquals("2.55", NumberFormat.sanitizeDecimalInput("2,5,5"))
    }

    @Test
    fun `a leading separator is dropped`() {
        assertEquals("5", NumberFormat.sanitizeDecimalInput(".5"))
    }

    @Test
    fun `minus sign never makes it through`() {
        assertEquals("5", NumberFormat.sanitizeDecimalInput("-5"))
    }

    @Test
    fun `input can be cleared completely`() {
        assertEquals("", NumberFormat.sanitizeDecimalInput(""))
    }

    @Test
    fun `a half-typed decimal is preserved while typing`() {
        assertEquals("1.", NumberFormat.sanitizeDecimalInput("1."))
    }

    @Test
    fun `length is capped`() {
        assertEquals(5, NumberFormat.sanitizeDecimalInput("123456789", maxLength = 5).length)
    }

    // ---- parse ----

    @Test
    fun `parse returns null for empty or partial input`() {
        assertNull(NumberFormat.parse(""))
        assertNull(NumberFormat.parse("."))
    }

    @Test
    fun `parse rejects negatives`() {
        assertNull(NumberFormat.parse("-3"))
    }

    @Test
    fun `parse reads valid numbers`() {
        assertEquals(2.5f, NumberFormat.parse("2.5"))
        assertEquals(0f, NumberFormat.parse("0"))
    }

    @Test
    fun `sanitize then parse round-trips a typed weight`() {
        val typed = NumberFormat.sanitizeDecimalInput("78,4 кг")
        assertEquals(78.4f, NumberFormat.parse(typed))
    }
}
