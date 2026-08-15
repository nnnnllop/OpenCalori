package com.opencalori.app.data.repository

import com.opencalori.app.data.local.dao.MealDao
import com.opencalori.app.data.local.dao.WeightDao
import com.opencalori.app.data.local.entity.FoodItemEntity
import com.opencalori.app.data.local.entity.WeightEntryEntity
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.WeightEntry
import com.opencalori.app.domain.repository.MealRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepositoryImpl @Inject constructor(
    private val mealDao: MealDao,
    private val weightDao: WeightDao
) : MealRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMealsForDay(epochDay: Long): Flow<List<Meal>> =
        mealDao.getMealsForDay(epochDay).flatMapLatest { meals ->
            if (meals.isEmpty()) {
                flowOf(emptyList())
            } else {
                val ids = meals.map { it.id }
                mealDao.getFoodItemsForMeals(ids).map { items ->
                    val grouped = items.groupBy { it.mealId }
                    meals.map { meal -> meal.toDomain(grouped[meal.id].orEmpty()) }
                }
            }
        }

    override suspend fun addItems(
        epochDay: Long,
        mealType: MealType,
        items: List<FoodItem>,
        dishName: String?
    ): Long {
        if (items.isEmpty()) return -1
        val title = dishName?.trim()?.takeIf { it.isNotEmpty() }
        val createdAt = System.currentTimeMillis()
        val entities = items.map { it.toEntity(0) }
        return if (title == null) {
            mealDao.addItemsToMeal(
                epochDay = epochDay,
                mealType = mealType.name,
                createdAt = createdAt,
                items = entities
            )
        } else {
            mealDao.addItemsToDish(
                epochDay = epochDay,
                mealType = mealType.name,
                dishName = title,
                createdAt = createdAt,
                items = entities
            )
        }
    }

    override suspend fun deleteMeal(mealId: Long) = mealDao.deleteMeal(mealId)

    override suspend fun deleteFoodItem(itemId: Long) {
        mealDao.deleteFoodItem(itemId)
        mealDao.deleteEmptyMeals()
    }

    override suspend fun updateFoodItemGrams(itemId: Long, grams: Float) =
        mealDao.updateFoodItemGrams(itemId, grams)

    override suspend fun getRecentFoodItems(limit: Int): List<FoodItem> =
        mealDao.getRecentFoodItems(limit).map { it.toDomain() }

    override suspend fun getMealsBetween(fromEpochDay: Long, toEpochDay: Long): List<Meal> {
        val meals = mealDao.getMealsBetween(fromEpochDay, toEpochDay)
        if (meals.isEmpty()) return emptyList()
        val grouped = mealDao.getFoodItemsForMealsOnce(meals.map { it.id }).groupBy { it.mealId }
        return meals.map { meal -> meal.toDomain(grouped[meal.id].orEmpty()) }
    }

    // ---- Weight ----

    override fun getWeightHistory(): Flow<List<WeightEntry>> =
        weightDao.getAll().map { list -> list.map { WeightEntry(it.dateEpochDay, it.weightKg) } }

    override suspend fun addWeight(dateEpochDay: Long, weightKg: Float) =
        weightDao.upsert(WeightEntryEntity(dateEpochDay, weightKg))

    override suspend fun deleteWeight(dateEpochDay: Long) = weightDao.delete(dateEpochDay)

    // ---- Mappers ----

    private fun com.opencalori.app.data.local.entity.MealEntity.toDomain(
        items: List<FoodItemEntity>
    ) = Meal(
        id = id,
        dateEpochDay = dateEpochDay,
        mealType = runCatching { MealType.valueOf(mealType) }.getOrDefault(MealType.SNACK),
        dishName = dishName,
        items = items.map { it.toDomain() },
        createdAt = createdAt
    )

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
