package com.opencalori.app.domain.model

/**
 * How aggressively photos are downscaled/compressed before going to the vision model.
 * Even ECONOMY keeps dishes reliably recognizable while noticeably cutting traffic
 * and input tokens; HIGH matches the pre-setting pipeline.
 */
enum class PhotoQuality(val maxDimension: Int, val jpegQuality: Int, val label: String) {
    HIGH(896, 82, "Высокое"),
    BALANCED(704, 70, "Сбалансированное"),
    ECONOMY(544, 55, "Экономное");

    companion object {
        fun fromStorage(value: String?): PhotoQuality =
            values().firstOrNull { it.name == value } ?: HIGH
    }
}
