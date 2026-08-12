package com.opencalori.app.ui.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Number helpers for editable fields.
 *
 * Pure and unit-tested: the old screens rendered `200.0` into text fields, refused to let
 * the field go empty and happily accepted a minus sign in a weight.
 */
object NumberFormat {

    /** `200.0` -> "200", `2.55` -> "2.6", `0.0` -> "0". */
    fun compact(value: Float): String {
        if (!value.isFinite()) return "0"
        val rounded = (value * 10f).roundToInt() / 10f
        return if (abs(rounded - rounded.toInt()) < 0.05f) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    /**
     * Keeps only what can belong to a positive decimal number, tolerating the comma that
     * Russian keyboards produce and refusing a second separator.
     */
    fun sanitizeDecimalInput(input: String, maxLength: Int = 7): String {
        val builder = StringBuilder()
        var separatorUsed = false
        for (char in input) {
            when {
                char.isDigit() -> builder.append(char)
                (char == '.' || char == ',') && !separatorUsed && builder.isNotEmpty() -> {
                    builder.append('.')
                    separatorUsed = true
                }
            }
            if (builder.length >= maxLength) break
        }
        return builder.toString()
    }

    /** Parses sanitized input; an empty or half-typed value is simply "nothing yet". */
    fun parse(input: String): Float? = input.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
}
