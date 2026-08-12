package com.opencalori.app.testing

import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.domain.repository.AiRepository
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

    override suspend fun setWeight(weightKg: Float) {
        state.value = state.value.copy(weightKg = weightKg)
    }

    override suspend fun setAiEnabled(enabled: Boolean) {
        state.value = state.value.copy(aiEnabled = enabled)
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
    var dishResult: Result<RecognizedDish> = Result.success(RecognizedDish("Блюдо", emptyList()))
    var nutritionResult: Result<List<EstimatedIngredient>> = Result.success(emptyList())

    var recognizeCalls = 0
        private set
    var estimateCalls = 0
        private set
    var lastIngredients: List<String> = emptyList()
        private set

    override suspend fun validateApi(): ApiValidationResult = validation

    override suspend fun recognizeDish(imageBase64: String): Result<RecognizedDish> {
        recognizeCalls++
        return dishResult
    }

    override suspend fun estimateNutrition(
        imageBase64: String,
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>> {
        estimateCalls++
        lastIngredients = correctedIngredients
        return nutritionResult
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
