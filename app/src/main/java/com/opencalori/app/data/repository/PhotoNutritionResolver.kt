package com.opencalori.app.data.repository

import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.FoodNameMatching
import com.opencalori.app.domain.repository.DishRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a photo recognition result into nutrition data sourced only from bundled local data.
 *
 * The AI may identify a dish name and visible ingredients, but it never supplies or overwrites
 * calories or macros. Unknown dish names stay an in-memory draft until the user confirms
 * locally matched ingredients; this class never writes into the shared dish catalogue.
 */
@Singleton
class PhotoNutritionResolver @Inject constructor(
    private val dishRepository: DishRepository,
    private val localNutritionResolver: LocalNutritionResolver
) {

    suspend fun resolve(
        dishName: String,
        ingredientNames: List<String>
    ): PhotoNutritionResolution {
        val localDish = findLocalDish(dishName)
        if (localDish != null) {
            val portion = localDish.portionGrams
            return PhotoNutritionResolution(
                matchedDish = localDish,
                items = listOf(
                    EstimatedIngredient(
                        name = localDish.name,
                        rawGrams = portion,
                        cookedGrams = portion,
                        caloriesPer100g = localDish.caloriesPer100g,
                        proteinPer100g = localDish.proteinPer100g,
                        fatPer100g = localDish.fatPer100g,
                        carbsPer100g = localDish.carbsPer100g,
                        notes = "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u043e\u0435 \u0431\u043b\u044e\u0434\u043e"
                    )
                )
            )
        }

        val ingredients = ingredientNames.map(String::trim).filter(String::isNotBlank).distinct()
        val local = localNutritionResolver.resolveNames(ingredients)
        return PhotoNutritionResolution(
            matchedDish = null,
            items = local.resolved,
            unmatchedNames = local.unmatchedNames
        )
    }

    private suspend fun findLocalDish(name: String): Dish? {
        val normalized = normalize(name)
        if (normalized.isBlank()) return null
        return dishRepository.search(name).first().firstOrNull { dish ->
            (listOf(dish.name) + dish.aliases).any { alias -> matches(normalized, normalize(alias)) }
        }
    }

    /**
     * Exact canonical names and aliases are preferred. A deliberately conservative word-stem
     * fallback accepts ordinary Russian inflections (for example, \u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0443 -> \u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0430) only after the FTS catalogue has returned the candidate.
     */
    private fun matches(query: String, candidate: String): Boolean {
        if (query == candidate) return true
        val queryTokens = query.split(' ').filter { it.length > 2 }
        val candidateTokens = candidate.split(' ').filter { it.length > 2 }
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return false
        if (queryTokens.size == 1 && queryTokens.single().length < 5) return false
        return queryTokens.all { queryToken ->
            candidateTokens.any { candidateToken -> sameWordFamily(queryToken, candidateToken) }
        }
    }

    private fun sameWordFamily(left: String, right: String): Boolean {
        if (left == right || left.startsWith(right) || right.startsWith(left)) return true
        val prefix = left.zip(right).takeWhile { (a, b) -> a == b }.size
        return prefix >= minOf(left.length, right.length) - 2 && prefix >= 5
    }

    private fun normalize(value: String): String = FoodNameMatching.fold(value)
        .replace(Regex("[^a-z\u0430-\u044f0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

data class PhotoNutritionResolution(
    val matchedDish: Dish?,
    val items: List<EstimatedIngredient>,
    val unmatchedNames: List<String> = emptyList()
) {
    val isDraft: Boolean get() = matchedDish == null
    val isComplete: Boolean get() = items.isNotEmpty() && unmatchedNames.isEmpty()
}
