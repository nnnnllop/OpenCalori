package com.opencalori.app.ui.foodsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.repository.MealRepository
import com.opencalori.app.data.repository.ProductRepository
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val mealRepository: MealRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    val results: StateFlow<List<Product>> = query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { productRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun setQuery(q: String) { query.value = q }

    fun addProductToMeal(product: Product, grams: Float, mealType: MealType) {
        viewModelScope.launch {
            mealRepository.addMeal(
                Meal(
                    dateEpochDay = LocalDate.now().toEpochDay(),
                    mealType = mealType,
                    items = listOf(
                        FoodItem(
                            name = product.name,
                            grams = grams,
                            caloriesPer100g = product.caloriesPer100g,
                            proteinPer100g = product.proteinPer100g,
                            fatPer100g = product.fatPer100g,
                            carbsPer100g = product.carbsPer100g
                        )
                    )
                )
            )
            _saved.value = true
        }
    }

    fun resetSaved() { _saved.value = false }
}
