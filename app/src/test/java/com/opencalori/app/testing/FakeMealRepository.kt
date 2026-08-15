package com.opencalori.app.testing

import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.WeightEntry
import com.opencalori.app.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the Room-backed diary.
 *
 * Reproduces the behaviour the real repository guarantees - most importantly that adding
 * items merges into an existing meal of the same type on the same day.
 */
class FakeMealRepository : MealRepository {

    val meals = MutableStateFlow<List<Meal>>(emptyList())
    private val weights = MutableStateFlow<List<WeightEntry>>(emptyList())

    private var nextMealId = 1L
    private var nextItemId = 1L

    /** Every (day, type, items) triple that reached the repository, in order. */
    val addCalls = mutableListOf<Triple<Long, MealType, List<FoodItem>>>()

    /** Dish titles passed alongside [addCalls], same order. Null means "no dish title". */
    val addedDishNames = mutableListOf<String?>()

    override fun getMealsForDay(epochDay: Long): Flow<List<Meal>> =
        meals.map { all -> all.filter { it.dateEpochDay == epochDay } }

    override suspend fun addItems(
        epochDay: Long,
        mealType: MealType,
        items: List<FoodItem>,
        dishName: String?
    ): Long {
        addCalls += Triple(epochDay, mealType, items)
        addedDishNames += dishName?.trim()?.takeIf { it.isNotEmpty() }
        if (items.isEmpty()) return -1

        val stamped = items.map { it.copy(id = nextItemId++) }
        val current = meals.value.toMutableList()
        val title = dishName?.trim()?.takeIf { it.isNotEmpty() }
        val index = current.indexOfFirst {
            it.dateEpochDay == epochDay && it.mealType == mealType && it.dishName == title
        }

        return if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(items = existing.items + stamped)
            meals.value = current
            existing.id
        } else {
            val meal = Meal(
                id = nextMealId++,
                dateEpochDay = epochDay,
                mealType = mealType,
                dishName = title,
                items = stamped
            )
            meals.value = current + meal
            meal.id
        }
    }

    override suspend fun deleteMeal(mealId: Long) {
        meals.value = meals.value.filterNot { it.id == mealId }
    }

    override suspend fun deleteFoodItem(itemId: Long) {
        meals.value = meals.value
            .map { meal -> meal.copy(items = meal.items.filterNot { it.id == itemId }) }
            .filter { it.items.isNotEmpty() }
    }

    override suspend fun updateFoodItemGrams(itemId: Long, grams: Float) {
        meals.value = meals.value.map { meal ->
            meal.copy(items = meal.items.map { if (it.id == itemId) it.copy(grams = grams) else it })
        }
    }

    override suspend fun getRecentFoodItems(limit: Int): List<FoodItem> =
        meals.value
            .sortedByDescending { it.dateEpochDay }
            .flatMap { it.items }
            .distinctBy { it.name }
            .take(limit)

    override suspend fun getMealsBetween(fromEpochDay: Long, toEpochDay: Long): List<Meal> =
        meals.value.filter { it.dateEpochDay in fromEpochDay..toEpochDay }

    override fun getWeightHistory(): Flow<List<WeightEntry>> = weights

    override suspend fun addWeight(dateEpochDay: Long, weightKg: Float) {
        weights.value = weights.value.filterNot { it.dateEpochDay == dateEpochDay } +
            WeightEntry(dateEpochDay, weightKg)
    }

    override suspend fun deleteWeight(dateEpochDay: Long) {
        weights.value = weights.value.filterNot { it.dateEpochDay == dateEpochDay }
    }

    fun seedMeal(
        epochDay: Long,
        mealType: MealType,
        vararg items: FoodItem,
        dishName: String? = null
    ): Meal {
        val meal = Meal(
            id = nextMealId++,
            dateEpochDay = epochDay,
            mealType = mealType,
            dishName = dishName,
            items = items.map { it.copy(id = nextItemId++) }
        )
        meals.value = meals.value + meal
        return meal
    }
}
