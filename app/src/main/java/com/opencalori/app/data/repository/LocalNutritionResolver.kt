package com.opencalori.app.data.repository

import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.repository.ProductRepository
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic nutrition source for hybrid mode. The vision model may suggest names and weights,
 * but values per 100 g are always taken from the bundled catalogue or the user's own products.
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

    suspend fun resolve(name: String): Product? {
        val normalized = normalize(name)
        if (normalized.isBlank()) return null
        val candidates = productRepository.search(name.trim()).first()
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
        return queryTokens.sumOf { query ->
            when {
                query in candidateTokens -> 4
                candidateTokens.any { it.startsWith(query) || query.startsWith(it) } -> 2
                else -> 0
            }
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

data class LocalNutritionResolution(
    val resolved: List<EstimatedIngredient>,
    val unmatchedNames: List<String>
) {
    val isComplete: Boolean get() = unmatchedNames.isEmpty()
}
