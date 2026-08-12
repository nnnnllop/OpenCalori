package com.opencalori.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.domain.model.CalorieGoal
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.WeightEntry
import com.opencalori.app.domain.repository.ApiConfigStore
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.domain.repository.UserPreferences
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val goal: CalorieGoal? = null,
    val meals: List<Meal> = emptyList(),
    val weightHistory: List<WeightEntry> = emptyList(),
    val currentWeight: Float? = null,
    val aiEnabled: Boolean = true,
    val apiConfigured: Boolean = false,
    val message: String? = null,
    val canUndo: Boolean = false
) {
    val isToday: Boolean get() = date == today
    val consumedCalories: Float get() = meals.sumOf { it.totalCalories.toDouble() }.toFloat()
    val consumedProtein: Float get() = meals.sumOf { it.totalProtein.toDouble() }.toFloat()
    val consumedFat: Float get() = meals.sumOf { it.totalFat.toDouble() }.toFloat()
    val consumedCarbs: Float get() = meals.sumOf { it.totalCarbs.toDouble() }.toFloat()

    /** The scanner is only useful once a BYOK key is in place. */
    val scannerReady: Boolean get() = aiEnabled && apiConfigured
}

/** What a delete removed, so it can be put back. */
private data class UndoBuffer(
    val epochDay: Long,
    val mealType: MealType,
    val items: List<FoodItem>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val userPrefs: UserPreferences,
    private val apiConfigStore: ApiConfigStore,
    private val calculateTdee: CalculateTdeeUseCase,
    private val clock: Clock
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now(clock)

    private val selectedDate = MutableStateFlow(today)

    private val _uiState = MutableStateFlow(DashboardUiState(date = today, today = today))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var undoBuffer: UndoBuffer? = null

    init {
        viewModelScope.launch {
            combine(
                selectedDate.flatMapLatest { mealRepository.getMealsForDay(it.toEpochDay()) },
                userPrefs.profile,
                mealRepository.getWeightHistory(),
                selectedDate,
                apiConfigStore.config
            ) { meals, profile, weights, date, apiConfig ->
                DashboardUiState(
                    date = date,
                    today = today,
                    goal = calculateTdee(profile),
                    meals = meals,
                    weightHistory = weights,
                    currentWeight = weights.maxByOrNull { it.dateEpochDay }?.weightKg ?: profile.weightKg,
                    aiEnabled = profile.aiEnabled,
                    apiConfigured = apiConfig.isConfigured
                )
            }.collect { fresh ->
                // Keep transient UI bits (snackbar, undo) that are not part of the DB state.
                _uiState.update { current ->
                    fresh.copy(message = current.message, canUndo = undoBuffer != null)
                }
            }
        }
    }

    fun previousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        if (selectedDate.value.isBefore(today)) {
            selectedDate.value = selectedDate.value.plusDays(1)
        }
    }

    fun goToToday() {
        selectedDate.value = today
    }

    fun deleteMeal(meal: Meal) {
        undoBuffer = UndoBuffer(meal.dateEpochDay, meal.mealType, meal.items)
        viewModelScope.launch {
            mealRepository.deleteMeal(meal.id)
            _uiState.update {
                it.copy(message = "Удалено: " + meal.mealType.label, canUndo = true)
            }
        }
    }

    fun deleteFoodItem(meal: Meal, item: FoodItem) {
        undoBuffer = UndoBuffer(meal.dateEpochDay, meal.mealType, listOf(item))
        viewModelScope.launch {
            mealRepository.deleteFoodItem(item.id)
            _uiState.update { it.copy(message = "Удалено: " + item.name, canUndo = true) }
        }
    }

    fun undoDelete() {
        val buffer = undoBuffer ?: return
        undoBuffer = null
        viewModelScope.launch {
            mealRepository.addItems(buffer.epochDay, buffer.mealType, buffer.items)
            _uiState.update { it.copy(message = null, canUndo = false) }
        }
    }

    fun updateItemGrams(item: FoodItem, grams: Float) {
        if (grams <= 0f) return
        viewModelScope.launch { mealRepository.updateFoodItemGrams(item.id, grams) }
    }

    /** Copies everything eaten on the previous day into the day being viewed. */
    fun repeatPreviousDay() {
        val target = selectedDate.value
        val source = target.minusDays(1).toEpochDay()
        viewModelScope.launch {
            val meals = mealRepository.getMealsBetween(source, source)
            if (meals.isEmpty()) {
                _uiState.update { it.copy(message = "За предыдущий день записей нет") }
                return@launch
            }
            meals.forEach { meal ->
                mealRepository.addItems(target.toEpochDay(), meal.mealType, meal.items)
            }
            _uiState.update { it.copy(message = "Скопировано приёмов пищи: " + meals.size) }
        }
    }

    fun addWeight(weightKg: Float) {
        if (weightKg !in MIN_WEIGHT..MAX_WEIGHT) return
        val date = selectedDate.value
        viewModelScope.launch {
            mealRepository.addWeight(date.toEpochDay(), weightKg)
            // The calorie target is derived from body weight, so keep the profile in step
            // instead of freezing it at whatever was typed during onboarding.
            val latest = _uiState.value.weightHistory.maxOfOrNull { it.dateEpochDay } ?: Long.MIN_VALUE
            if (date.toEpochDay() >= latest) userPrefs.setWeight(weightKg)
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        const val MIN_WEIGHT = 30f
        const val MAX_WEIGHT = 300f
    }
}
