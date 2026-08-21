package com.opencalori.app.data.repository

import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.FoodNameMatching
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.repository.ProductRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic nutrition source. AI can suggest only names and visible composition; all
 * calories and macros are always selected from the bundled catalogue or user products.
 */
@Singleton
class LocalNutritionResolver @Inject constructor(
    private val productRepository: ProductRepository
) {
    suspend fun replaceMacros(estimated: List<EstimatedIngredient>): LocalNutritionResolution {
        val resolved = mutableListOf<EstimatedIngredient>()
        val unmatched = mutableListOf<String>()
        estimated.forEach { item ->
            val product = resolve(item.name)
            if (product == null) {
                unmatched += item.name
            } else {
                resolved += item.copy(
                    name = product.name,
                    caloriesPer100g = product.caloriesPer100g,
                    proteinPer100g = product.proteinPer100g,
                    fatPer100g = product.fatPer100g,
                    carbsPer100g = product.carbsPer100g
                )
            }
        }
        return LocalNutritionResolution(resolved, unmatched)
    }

    /**
     * Resolves visible ingredient names into neutral 100 g editable local entries. No AI macro
     * values or AI estimated portions are accepted in this code path.
     */
    suspend fun resolveNames(names: List<String>): LocalNutritionResolution {
        val resolved = mutableListOf<EstimatedIngredient>()
        val unmatched = mutableListOf<String>()
        names.map(String::trim).filter(String::isNotBlank).distinct().forEach { name ->
            val product = resolve(name)
            if (product == null) {
                unmatched += name
            } else {
                resolved += EstimatedIngredient(
                    name = product.name,
                    rawGrams = DEFAULT_PORTION_GRAMS,
                    cookedGrams = DEFAULT_PORTION_GRAMS,
                    caloriesPer100g = product.caloriesPer100g,
                    proteinPer100g = product.proteinPer100g,
                    fatPer100g = product.fatPer100g,
                    carbsPer100g = product.carbsPer100g,
                    notes = "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0439 \u043f\u0440\u043e\u0434\u0443\u043a\u0442"
                )
            }
        }
        return LocalNutritionResolution(resolved, unmatched)
    }

    suspend fun resolve(name: String): Product? {
        val normalized = normalize(name)
        if (normalized.isBlank()) return null
        val candidates = productRepository.search(normalized).first()
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { normalize(it.name) == normalized }?.let { return it }
        val nameTokens = normalized.split(' ').filter { it.length > 1 }.toSet()
        return candidates
            .map { product -> product to similarityScore(nameTokens, normalize(product.name)) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Product, Int>> { it.second }
                    .thenBy { it.first.name.length }
            )
            .firstOrNull()
            ?.first
    }

    private fun similarityScore(queryTokens: Set<String>, candidateName: String): Int {
        val candidateTokens = candidateName.split(' ').filter { it.length > 1 }.toSet()
        return queryTokens.map { query ->
            when {
                query in candidateTokens -> 4
                candidateTokens.any { it.startsWith(query) || query.startsWith(it) } -> 2
                else -> 0
            }
        }.sum()
    }

    private fun normalize(value: String): String = FoodNameMatching.fold(value)
        .replace(Regex("[^a-z\u0430-\u044f0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private companion object {
        const val DEFAULT_PORTION_GRAMS = 100f
    }
}

data class LocalNutritionResolution(
    val resolved: List<EstimatedIngredient>,
    val unmatchedNames: List<String>
) {
    val isComplete: Boolean get() = unmatchedNames.isEmpty()
}
