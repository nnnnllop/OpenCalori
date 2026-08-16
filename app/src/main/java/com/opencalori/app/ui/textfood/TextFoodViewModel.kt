package com.opencalori.app.ui.textfood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.network.aiUserMessage
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.MealDishItems
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.ui.navigation.Routes
import com.opencalori.app.ui.util.FoodQuantityValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Where the text-entry flow currently is. Each step is explicit and reversible. */
enum class TextFoodStage { INPUT, DISHES, GRAMS }

/** One dish parsed out of the user's description. */
data class TextDishDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ingredients: List<RecognizedIngredient> = emptyList(),
    val estimated: List<EstimatedIngredient> = emptyList()
) {
    val names: List<String> get() = ingredients.map { it.name.trim() }.filter { it.isNotEmpty() }

    val totalCalories: Float get() = estimated.sumOf { it.totalCalories.toDouble() }.toFloat()

    /** Every product needs a weight the user typed in: AI never confirms grams here. */
    val hasValidGrams: Boolean
        get() = estimated.isNotEmpty() && estimated.all { FoodQuantityValidation.isValid(it.effectiveGrams) }
}

data class TextFoodState(
    val query: String = "",
    val mealType: MealType = MealType.SNACK,
    val stage: TextFoodStage = TextFoodStage.INPUT,
    val dishes: List<TextDishDraft> = emptyList(),
    val busy: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** Retry of the failed AI step stays available until the query changes. */
    val manualRetryAvailable: Boolean = false
) {
    val canRecognize: Boolean get() = query.trim().length >= 2 && !busy

    val canCalculate: Boolean get() = !busy && dishes.isNotEmpty() && dishes.all { it.names.isNotEmpty() }

    val canSave: Boolean get() = !busy && dishes.isNotEmpty() && dishes.all { it.hasValidGrams }

    val canRetry: Boolean get() = !busy && manualRetryAvailable

    val totalCalories: Float get() = dishes.sumOf { it.totalCalories.toDouble() }.toFloat()
}

/**
 * "Describe your food" entry point.
 *
 * The AI is only allowed to name dishes and list their products. Weights always stay at zero
 * until the user types them, because a made-up gram value silently poisons the diary.
 */
@HiltViewModel
class TextFoodViewModel @Inject constructor(
    private val ai: AiRepository,
    private val meals: MealRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val targetEpochDay: Long =
        savedStateHandle.get<Long>(Routes.ARG_DATE) ?: LocalDate.now().toEpochDay()

    private val _state = MutableStateFlow(TextFoodState())
    val state: StateFlow<TextFoodState> = _state.asStateFlow()

    fun setQuery(value: String) = _state.update {
        it.copy(query = value.take(MAX_QUERY_LENGTH), error = null, manualRetryAvailable = false)
    }

    fun setMealType(value: MealType) = _state.update { it.copy(mealType = value) }

    fun recognize(manualRetry: Boolean = false) {
        val description = _state.value.query.trim()
        if (description.length < 2 || _state.value.busy) return
        _state.update {
            it.copy(
                busy = true,
                error = null,
                manualRetryAvailable = false
            )
        }
        viewModelScope.launch {
            val result = try {
                ai.recognizeTextDishes(description)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result
                .onSuccess { recognized ->
                    val dishes = recognized
                        .filter { it.dishName.isNotBlank() || it.ingredients.isNotEmpty() }
                        .map { dish ->
                            TextDishDraft(
                                name = dish.dishName.ifBlank { RecognizedDish.UNKNOWN_LABEL },
                                ingredients = dish.ingredients
                            )
                        }
                    if (dishes.isEmpty()) {
                        _state.update {
                            it.copy(
                                busy = false,
                                error = "Не удалось найти еду в описании. Опишите блюда подробнее.",
                                manualRetryAvailable = true
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            dishes = dishes,
                            stage = TextFoodStage.DISHES,
                            error = null,
                            manualRetryAvailable = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            error = error.aiUserMessage("Не удалось обработать описание. Повторите текущий шаг."),
                            manualRetryAvailable = true
                        )
                    }
                }
        }
    }

    fun retry() {
        val state = _state.value
        if (!state.canRetry || state.busy) return
        when (state.stage) {
            TextFoodStage.INPUT -> recognize(manualRetry = true)
            TextFoodStage.DISHES -> calculate(manualRetry = true)
            TextFoodStage.GRAMS -> Unit
        }
    }

    // ---- Dish and ingredient editing ----

    fun renameDish(dishIndex: Int, name: String) =
        updateDish(dishIndex) { it.copy(name = name.trim().take(MAX_ITEM_NAME_LENGTH)) }

    fun addDish(name: String) {
        val trimmed = name.trim().take(MAX_ITEM_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        _state.update { it.copy(dishes = it.dishes + TextDishDraft(name = trimmed), error = null) }
    }

    fun removeDish(dishIndex: Int) = _state.update { state ->
        if (dishIndex !in state.dishes.indices) state
        else state.copy(dishes = state.dishes.filterIndexed { index, _ -> index != dishIndex })
    }

    fun addIngredient(dishIndex: Int, name: String) {
        val trimmed = name.trim().take(MAX_ITEM_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        updateDish(dishIndex) { it.copy(ingredients = it.ingredients + RecognizedIngredient(trimmed)) }
    }

    fun renameIngredient(dishIndex: Int, itemIndex: Int, name: String) = updateDish(dishIndex) { dish ->
        if (itemIndex !in dish.ingredients.indices) dish
        else dish.copy(
            ingredients = dish.ingredients.toMutableList().also {
                it[itemIndex] = it[itemIndex].copy(name = name.trim().take(MAX_ITEM_NAME_LENGTH))
            }
        )
    }

    fun removeIngredient(dishIndex: Int, itemIndex: Int) = updateDish(dishIndex) { dish ->
        if (itemIndex !in dish.ingredients.indices) dish
        else dish.copy(ingredients = dish.ingredients.filterIndexed { index, _ -> index != itemIndex })
    }

    fun backToInput() = _state.update { it.copy(stage = TextFoodStage.INPUT, error = null) }

    fun backToDishes() = _state.update { it.copy(stage = TextFoodStage.DISHES, error = null) }

    // ---- Nutrition ----

    fun calculate(manualRetry: Boolean = false) {
        val state = _state.value
        if (!state.canCalculate) return
        _state.update {
            it.copy(
                busy = true,
                error = null,
                manualRetryAvailable = false
            )
        }
        viewModelScope.launch {
            val resolved = mutableListOf<TextDishDraft>()
            state.dishes.forEach { dish ->
                val result = try {
                    ai.estimateTextNutrition(dish.name, dish.names)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                val estimated = result.getOrElse { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            error = error.aiUserMessage("Не удалось рассчитать КБЖУ. Повторите текущий шаг."),
                            manualRetryAvailable = true
                        )
                    }
                    return@launch
                }
                if (estimated.isEmpty()) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "ИИ не вернул продукты для «${dish.name}». Проверьте состав и повторите текущий шаг.",
                            manualRetryAvailable = true
                        )
                    }
                    return@launch
                }
                // The weight is the user's job: whatever the model guessed is discarded here.
                resolved += dish.copy(estimated = estimated.map { it.copy(rawGrams = 0f, cookedGrams = 0f) })
            }
            _state.update {
                it.copy(
                    busy = false,
                    dishes = resolved,
                    stage = TextFoodStage.GRAMS,
                    error = null,
                    manualRetryAvailable = false
                )
            }
        }
    }

    fun setGrams(dishIndex: Int, itemIndex: Int, grams: Float) = updateDish(dishIndex) { dish ->
        if (itemIndex !in dish.estimated.indices) dish
        else dish.copy(
            estimated = dish.estimated.toMutableList().also {
                it[itemIndex] = it[itemIndex].withCookedGrams(grams)
            }
        )
    }

    fun removeEstimated(dishIndex: Int, itemIndex: Int) = updateDish(dishIndex) { dish ->
        if (itemIndex !in dish.estimated.indices) dish
        else dish.copy(estimated = dish.estimated.filterIndexed { index, _ -> index != itemIndex })
    }

    /** One diary entry per dish, so the diary keeps meal -> dish -> products. */
    fun save() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            meals.addDishItems(
                epochDay = targetEpochDay,
                mealType = state.mealType,
                dishes = state.dishes.map { dish ->
                    MealDishItems(
                        dishName = dish.name.takeIf { it.isNotBlank() },
                        items = dish.estimated.map { it.toFoodItem() }
                    )
                }
            )
            _state.update { it.copy(busy = false, saved = true) }
        }
    }

    private fun updateDish(dishIndex: Int, transform: (TextDishDraft) -> TextDishDraft) {
        _state.update { state ->
            if (dishIndex !in state.dishes.indices) state
            else state.copy(
                dishes = state.dishes.mapIndexed { index, dish ->
                    if (index == dishIndex) transform(dish) else dish
                },
                error = null,
                manualRetryAvailable = false
            )
        }
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 1_000
        const val MAX_ITEM_NAME_LENGTH = 120
    }
}
