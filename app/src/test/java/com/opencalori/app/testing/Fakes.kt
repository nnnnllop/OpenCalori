package com.opencalori.app.testing

import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.PhotoQuality
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.DishRepository
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.ProductRepository
import com.opencalori.app.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserPreferences(initial: UserProfile = UserProfile(onboardingCompleted = true)) : UserPreferences {

    val state = MutableStateFlow(initial)
    override val profile: Flow<UserProfile> = state

    override suspend fun saveProfile(profile: UserProfile) {
        state.value = profile
    }

    override suspend fun updateMetrics(
        gender: Gender,
        age: Int,
        heightCm: Float,
        weightKg: Float,
        activityLevel: ActivityLevel,
        goal: Goal
    ) {
        state.value = state.value.copy(
            gender = gender,
            age = age,
            heightCm = heightCm,
            weightKg = weightKg,
            activityLevel = activityLevel,
            goal = goal,
            onboardingCompleted = true
        )
    }

    override suspend fun setWeight(weightKg: Float) {
        state.value = state.value.copy(weightKg = weightKg)
    }

    override suspend fun setAiEnabled(enabled: Boolean) {
        state.value = state.value.copy(aiEnabled = enabled)
    }
    override suspend fun setNutritionSourceMode(mode: NutritionSourceMode) {
        state.value = state.value.copy(nutritionSourceMode = mode)
    }
    override suspend fun setPhotoQuality(quality: PhotoQuality) {
        state.value = state.value.copy(photoQuality = quality)
    }

    override suspend fun setAiSkipListReview(skip: Boolean) {
        state.value = state.value.copy(aiSkipListReview = skip)
    }

    override suspend fun setAiSkipGramsReview(skip: Boolean) {
        state.value = state.value.copy(aiSkipGramsReview = skip)
    }

    override suspend fun setAiSkipFinalReview(skip: Boolean) {
        state.value = state.value.copy(aiSkipFinalReview = skip)
    }
}

class FakeDishRepository : DishRepository {
    val catalogue = MutableStateFlow<List<Dish>>(emptyList())
    override fun search(query: String): Flow<List<Dish>> {
        val normalized = query.trim().lowercase()
        return catalogue.map { dishes ->
            if (normalized.isBlank()) emptyList() else dishes.filter { dish ->
                dish.name.lowercase().contains(normalized) || dish.aliases.any { it.lowercase().contains(normalized) }
            }
        }
    }
    override suspend fun count(): Int = catalogue.value.size
}
class FakeApiConfigStore(initial: ApiConfig = ApiConfig()) : ApiConfigStore {

    val state = MutableStateFlow(initial)
    override val config: Flow<ApiConfig> = state

    var saveCount = 0
        private set
    var clearCount = 0
        private set

    override suspend fun current(): ApiConfig = state.value

    override suspend fun save(config: ApiConfig) {
        saveCount++
        state.value = config
    }

    override suspend fun clear() {
        clearCount++
        state.value = ApiConfig(apiKey = "")
    }
}

class FakeAiRepository : AiRepository {

    var validation: ApiValidationResult = ApiValidationResult(ValidationStatus.SUCCESS)

    /** Single-dish answer, wrapped into a one-element list unless [dishesResult] is set. */
    var dishResult: Result<RecognizedDish> = Result.success(RecognizedDish("Блюдо", emptyList()))

    /** Multi-dish answer. Takes precedence over [dishResult] when not null. */
    var dishesResult: Result<List<RecognizedDish>>? = null
    var nutritionResult: Result<List<EstimatedIngredient>> = Result.success(emptyList())

    /** Per-dish nutrition answers keyed by dish name; falls back to [nutritionResult]. */
    val nutritionByDish = mutableMapOf<String, Result<List<EstimatedIngredient>>>()

    var recognizeCalls = 0
        private set
    var estimateCalls = 0
        private set
    var lastIngredients: List<String> = emptyList()
        private set
    var lastDescription: String? = null
        private set
    val estimatedDishNames = mutableListOf<String>()

    override suspend fun validateApi(): ApiValidationResult = validation

    override suspend fun recognizeDishes(imageBase64: String): Result<List<RecognizedDish>> {
        recognizeCalls++
        return dishesResult ?: dishResult.map { listOf(it) }
    }

    override suspend fun recognizeTextDishes(description: String): Result<List<RecognizedDish>> {
        recognizeCalls++
        lastDescription = description
        return dishesResult ?: dishResult.map { listOf(it) }
    }

    override suspend fun estimateNutrition(
        imageBase64: String,
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>> {
        estimateCalls++
        lastIngredients = correctedIngredients
        estimatedDishNames += dishName
        return nutritionByDish[dishName] ?: nutritionResult
    }

    override suspend fun estimateTextNutrition(
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>> {
        estimateCalls++
        lastIngredients = correctedIngredients
        estimatedDishNames += dishName
        return nutritionByDish[dishName] ?: nutritionResult
    }
}

class FakeProductRepository : ProductRepository {

    val catalogue = MutableStateFlow<List<Product>>(emptyList())
    val custom = MutableStateFlow<List<Product>>(emptyList())

    private var nextId = 1L

    override fun search(query: String): Flow<List<Product>> = catalogue.map { all ->
        if (query.isBlank()) emptyList()
        else all.filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun addCustomProduct(product: Product): Long {
        val id = nextId++
        custom.value = custom.value + product.copy(id = id, source = ProductSource.CUSTOM)
        return id
    }

    override suspend fun deleteCustomProduct(id: Long) {
        custom.value = custom.value.filterNot { it.id == id }
    }

    override fun customProducts(): Flow<List<Product>> = custom

    override suspend fun builtInCount(): Int = catalogue.value.size
}
