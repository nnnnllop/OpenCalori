package com.opencalori.app.domain.repository

import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow

/**
 * Diary storage: meals, food items and weight history.
 * Implemented by Room; faked in unit tests.
 */
interface MealRepository {
    fun getMealsForDay(epochDay: Long): Flow<List<Meal>>

    /**
     * Adds items to the diary. If a meal of the same type already exists on that day,
     * the items are appended to it instead of creating a duplicate card.
     * Returns the id of the meal the items ended up in.
     */
    suspend fun addItems(epochDay: Long, mealType: MealType, items: List<FoodItem>): Long

    suspend fun deleteMeal(mealId: Long)

    suspend fun deleteFoodItem(itemId: Long)

    suspend fun updateFoodItemGrams(itemId: Long, grams: Float)

    /** Distinct food items eaten recently, newest first. Powers "recent" suggestions. */
    suspend fun getRecentFoodItems(limit: Int = 40): List<FoodItem>

    suspend fun getMealsBetween(fromEpochDay: Long, toEpochDay: Long): List<Meal>

    fun getWeightHistory(): Flow<List<WeightEntry>>

    suspend fun addWeight(dateEpochDay: Long, weightKg: Float)

    suspend fun deleteWeight(dateEpochDay: Long)
}

/** Searchable food catalogue: bundled offline DB plus the user's own products. */
interface ProductRepository {
    fun search(query: String): Flow<List<Product>>

    suspend fun addCustomProduct(product: Product): Long

    suspend fun deleteCustomProduct(id: Long)

    fun customProducts(): Flow<List<Product>>

    suspend fun builtInCount(): Int
}

/** BYOK vision pipeline. */
interface AiRepository {
    suspend fun validateApi(): ApiValidationResult

    suspend fun recognizeDish(imageBase64: String): Result<RecognizedDish>

    suspend fun estimateNutrition(
        imageBase64: String,
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>>
}

/** User profile + AI scenario switches. */
interface UserPreferences {
    val profile: Flow<UserProfile>
    suspend fun saveProfile(profile: UserProfile)
    suspend fun setWeight(weightKg: Float)
    suspend fun setAiEnabled(enabled: Boolean)
    suspend fun setAiSkipListReview(skip: Boolean)
    suspend fun setAiSkipGramsReview(skip: Boolean)
    suspend fun setAiSkipFinalReview(skip: Boolean)
    suspend fun setNutritionSourceMode(mode: NutritionSourceMode)
}

/** Encrypted BYOK credential storage. */
interface ApiConfigStore {
    val config: Flow<ApiConfig>
    suspend fun current(): ApiConfig
    suspend fun save(config: ApiConfig)
    suspend fun clear()
}
