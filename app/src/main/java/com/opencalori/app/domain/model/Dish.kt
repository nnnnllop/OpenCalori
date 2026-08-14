package com.opencalori.app.domain.model

/** A curated, portioned meal from the bundled offline dish catalogue. */
data class Dish(
    val id: Long,
    val name: String,
    val aliases: List<String>,
    val ingredients: List<String>,
    val portionGrams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float
) {
    fun toProduct() = Product(
        id = -id,
        name = name,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g,
        source = ProductSource.DISH,
        suggestedGrams = portionGrams
    )
}
