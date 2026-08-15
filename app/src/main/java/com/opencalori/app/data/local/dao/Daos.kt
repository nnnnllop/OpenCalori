package com.opencalori.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.opencalori.app.data.local.entity.CustomProductEntity
import com.opencalori.app.data.local.entity.FoodItemEntity
import com.opencalori.app.data.local.entity.MealEntity
import com.opencalori.app.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItems(items: List<FoodItemEntity>)

    @Query("SELECT * FROM meals WHERE dateEpochDay = :epochDay AND mealType = :mealType LIMIT 1")
    suspend fun findMeal(epochDay: Long, mealType: String): MealEntity?
    @Query("SELECT * FROM meals WHERE dateEpochDay = :epochDay AND mealType = :mealType AND dishName = :dishName LIMIT 1")
    suspend fun findMealByDishName(epochDay: Long, mealType: String, dishName: String): MealEntity?

    /**
     * Appends items to the meal of the given type on the given day, creating it only if
     * it does not exist yet. Keeps the diary from filling up with duplicate "Завтрак" cards.
     */
    @Transaction
    suspend fun addItemsToMeal(
        epochDay: Long,
        mealType: String,
        createdAt: Long,
        items: List<FoodItemEntity>
    ): Long {
        val existing = findMeal(epochDay, mealType)
        val mealId = existing?.id ?: insertMeal(
            MealEntity(dateEpochDay = epochDay, mealType = mealType, createdAt = createdAt)
        )
        insertFoodItems(items.map { it.copy(id = 0, mealId = mealId) })
        return mealId
    }

    /** Appends products to a named dish, preserving the dish as the primary diary entry. */
    @Transaction
    suspend fun addItemsToDish(
        epochDay: Long,
        mealType: String,
        dishName: String,
        createdAt: Long,
        items: List<FoodItemEntity>
    ): Long {
        val existing = findMealByDishName(epochDay, mealType, dishName)
        val mealId = existing?.id ?: insertMeal(
            MealEntity(
                dateEpochDay = epochDay,
                mealType = mealType,
                dishName = dishName,
                createdAt = createdAt
            )
        )
        insertFoodItems(items.map { it.copy(id = 0, mealId = mealId) })
        return mealId
    }
    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    @Query("DELETE FROM food_items WHERE id = :itemId")
    suspend fun deleteFoodItem(itemId: Long)

    @Query("UPDATE food_items SET grams = :grams WHERE id = :itemId")
    suspend fun updateFoodItemGrams(itemId: Long, grams: Float)

    /** Deletes meals that no longer hold any items, so empty cards do not linger. */
    @Query("DELETE FROM meals WHERE id NOT IN (SELECT DISTINCT mealId FROM food_items)")
    suspend fun deleteEmptyMeals()

    @Query("SELECT * FROM meals WHERE dateEpochDay = :epochDay ORDER BY createdAt ASC")
    fun getMealsForDay(epochDay: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay ASC, createdAt ASC")
    suspend fun getMealsBetween(from: Long, to: Long): List<MealEntity>

    @Query("SELECT * FROM food_items WHERE mealId IN (:mealIds) ORDER BY id ASC")
    fun getFoodItemsForMeals(mealIds: List<Long>): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE mealId IN (:mealIds) ORDER BY id ASC")
    suspend fun getFoodItemsForMealsOnce(mealIds: List<Long>): List<FoodItemEntity>

    @Query(
        """
        SELECT fi.* FROM food_items fi
        JOIN meals m ON m.id = fi.mealId
        WHERE fi.id IN (SELECT MAX(f2.id) FROM food_items f2 GROUP BY f2.name)
        ORDER BY m.dateEpochDay DESC, fi.id DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentFoodItems(limit: Int): List<FoodItemEntity>

    @Query("SELECT * FROM food_items WHERE mealId = :mealId ORDER BY id ASC")
    suspend fun getFoodItemsForMeal(mealId: Long): List<FoodItemEntity>
}

@Dao
interface WeightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_history ORDER BY dateEpochDay ASC")
    fun getAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_history ORDER BY dateEpochDay ASC")
    suspend fun getAllOnce(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_history ORDER BY dateEpochDay DESC LIMIT 1")
    fun getLatest(): Flow<WeightEntryEntity?>

    @Query("DELETE FROM weight_history WHERE dateEpochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}

@Dao
interface CustomProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: CustomProductEntity): Long

    @Query("DELETE FROM custom_products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM custom_products ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CustomProductEntity>>

    @Query("SELECT * FROM custom_products ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<CustomProductEntity>

    @Query(
        "SELECT * FROM custom_products WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC LIMIT :limit"
    )
    fun search(query: String, limit: Int = 30): Flow<List<CustomProductEntity>>
}
