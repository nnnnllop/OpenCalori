package com.opencalori.app.data.repository

import com.opencalori.app.data.local.dao.MealDao
import com.opencalori.app.data.local.dao.WeightDao
import com.opencalori.app.data.local.entity.FoodItemEntity
import com.opencalori.app.data.local.entity.MealEntity
import com.opencalori.app.data.local.entity.WeightEntryEntity
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.WeightEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    private val mealDao: MealDao,
    private val weightDao: WeightDao
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getMealsForDay(epochDay: Long): Flow<List<Meal>> =
        mealDao.getMealsForDay(epochDay).flatMapLatest { meals ->
            if (meals.isEmpty()) {
                flowOf(emptyList())
            } else {
                val ids = meals.map { it.id }
                mealDao.getFoodItemsForMeals(ids).map { items ->
                    val grouped = items.groupBy { it.mealId }
                    meals.map { meal ->
                        Meal(
                            id = meal.id,
                            dateEpochDay = meal.dateEpochDay,
                            mealType = runCatching { MealType.valueOf(meal.mealType) }
                                .getOrDefault(MealType.SNACK),
                            items = grouped[meal.id]?.map { it.toDomain() } ?: emptyList(),
                            createdAt = meal.createdAt
                        )
                    }
                }
            }
        }

    suspend fun addMeal(meal: Meal): Long {
        val mealId = mealDao.insertMeal(
            MealEntity(
                dateEpochDay = meal.dateEpochDay,
                mealType = meal.mealType.name,
                createdAt = meal.createdAt
            )
        )
        mealDao.insertFoodItems(meal.items.map { it.toEntity(mealId) })
        return mealId
    }

    suspend fun deleteMeal(mealId: Long) = mealDao.deleteMealWithItems(mealId)

    // ---- Weight ----

    fun getWeightHistory(): Flow<List<WeightEntry>> =
        weightDao.getAll().map { list ->
            list.map { WeightEntry(it.dateEpochDay, it.weightKg) }
        }

    fun getLatestWeight(): Flow<WeightEntry?> =
        weightDao.getLatest().map { it?.let { e -> WeightEntry(e.dateEpochDay, e.weightKg) } }

    suspend fun addWeight(dateEpochDay: Long, weightKg: Float) =
        weightDao.upsert(WeightEntryEntity(dateEpochDay, weightKg))

    suspend fun deleteWeight(dateEpochDay: Long) = weightDao.delete(dateEpochDay)

    // ---- Mappers ----

    private fun FoodItemEntity.toDomain() = FoodItem(
        id = id, name = name, grams = grams,
        caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g, carbsPer100g = carbsPer100g
    )

    private fun FoodItem.toEntity(mealId: Long) = FoodItemEntity(
        mealId = mealId, name = name, grams = grams,
        caloriesPer100g = caloriesPer100g, proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g, carbsPer100g = carbsPer100g
    )
}
