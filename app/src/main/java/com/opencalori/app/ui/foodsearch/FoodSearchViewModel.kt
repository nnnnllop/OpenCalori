package com.opencalori.app.ui.foodsearch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.domain.repository.ProductRepository
import com.opencalori.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FoodSearchUiState(
    val query: String = "",
    val recents: List<Product> = emptyList(),
    val saved: Boolean = false,
    val message: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val mealRepository: MealRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** The day the diary is showing - not necessarily today. */
    private val targetEpochDay: Long =
        savedStateHandle.get<Long>(Routes.ARG_DATE) ?: LocalDate.now().toEpochDay()

    private val query = MutableStateFlow("")

    private val _uiState = MutableStateFlow(FoodSearchUiState())
    val uiState: StateFlow<FoodSearchUiState> = _uiState.asStateFlow()

    val results: StateFlow<List<Product>> = query
        .debounce(220)
        .distinctUntilChanged()
        .flatMapLatest { productRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val recents = mealRepository.getRecentFoodItems(RECENT_LIMIT)
                .map { it.toProduct() }
            _uiState.update { it.copy(recents = recents) }
        }
    }

    fun setQuery(value: String) {
        query.value = value
        _uiState.update { it.copy(query = value) }
    }

    fun addProductToMeal(product: Product, grams: Float, mealType: MealType) {
        if (grams <= 0f) return
        viewModelScope.launch {
            mealRepository.addItems(targetEpochDay, mealType, listOf(product.toFoodItem(grams)))
            _uiState.update { it.copy(saved = true) }
        }
    }

    /** Saves a product the user typed in themselves and immediately logs it. */
    fun addCustomProduct(product: Product, grams: Float, mealType: MealType) {
        viewModelScope.launch {
            productRepository.addCustomProduct(product)
            mealRepository.addItems(targetEpochDay, mealType, listOf(product.toFoodItem(grams)))
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun resetSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun FoodItem.toProduct() = Product(
        id = id,
        name = name,
        caloriesPer100g = caloriesPer100g,
        proteinPer100g = proteinPer100g,
        fatPer100g = fatPer100g,
        carbsPer100g = carbsPer100g,
        source = ProductSource.RECENT
    )

    private companion object {
        const val RECENT_LIMIT = 20
    }
}
