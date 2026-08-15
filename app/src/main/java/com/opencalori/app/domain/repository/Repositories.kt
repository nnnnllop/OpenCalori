package com.opencalori.app.domain.repository

import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.Dish
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
data class MealDishItems(
    val dishName: String?,
    val items: List<FoodItem>
)

interface MealRepository {
    fun getMealsForDay(epochDay: Long): Flow<List<Meal>>

    /**
     * Adds items to the diary. Manual entries are appended to the day/type group.
     * A non-blank [dishName] keeps a separately titled dish entry so its ingredients can
     * be reviewed only when the user opens that dish.
     */
    suspend fun addItems(
        epochDay: Long,
        mealType: MealType,
        items: List<FoodItem>,
        dishName: String? = null
    ): Long

    /** Writes all separate dishes in one database transaction or writes none of them. */
    suspend fun addDishItems(
        epochDay: Long,
        mealType: MealType,
        dishes: List<MealDishItems>
    )

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
/** Curated local meals with aliases, ingredients, and a default serving size. */
interface DishRepository {
    fun search(query: String): Flow<List<Dish>>
    suspend fun count(): Int
}

interface AiRepository {
    suspend fun validateApi(): ApiValidationResult

    /**
     * Stage 1 of the photo pipeline: every visually separate dish on the plate, each with its
     * own ingredient list. An empty list means "no food recognised", never an error.
     */
    suspend fun recognizeDishes(imageBase64: String): Result<List<RecognizedDish>>

    /** Stage 1 of the text pipeline. Grams are never estimated here. */
    suspend fun recognizeTextDishes(description: String): Result<List<RecognizedDish>>

    /** Legacy single-dish helpers, kept for callers that only need the first dish. */
    suspend fun recognizeDish(imageBase64: String): Result<RecognizedDish> =
        recognizeDishes(imageBase64).map { dishes ->
            dishes.firstOrNull() ?: RecognizedDish("", emptyList())
        }

    suspend fun recognizeText(description: String): Result<RecognizedDish> =
        recognizeTextDishes(description).map { dishes ->
            dishes.firstOrNull() ?: RecognizedDish("", emptyList())
        }

    suspend fun estimateNutrition(
        imageBase64: String,
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>>

    suspend fun estimateTextNutrition(
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
