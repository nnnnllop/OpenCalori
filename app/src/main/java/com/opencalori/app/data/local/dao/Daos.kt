package com.opencalori.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMeal(mealId: Long)

    @Query("DELETE FROM food_items WHERE mealId = :mealId")
    suspend fun deleteFoodItemsForMeal(mealId: Long)

    @Transaction
    suspend fun deleteMealWithItems(mealId: Long) {
        deleteFoodItemsForMeal(mealId)
        deleteMeal(mealId)
    }

    @Query("SELECT * FROM meals WHERE dateEpochDay = :epochDay ORDER BY createdAt ASC")
    fun getMealsForDay(epochDay: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM food_items WHERE mealId IN (:mealIds)")
    fun getFoodItemsForMeals(mealIds: List<Long>): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE mealId = :mealId")
    suspend fun getFoodItemsForMeal(mealId: Long): List<FoodItemEntity>
}

@Dao
interface WeightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_history ORDER BY dateEpochDay ASC")
    fun getAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_history ORDER BY dateEpochDay DESC LIMIT 1")
    fun getLatest(): Flow<WeightEntryEntity?>

    @Query("DELETE FROM weight_history WHERE dateEpochDay = :epochDay")
    suspend fun delete(epochDay: Long)
}
