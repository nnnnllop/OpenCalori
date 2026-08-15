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

data class RepeatPreview(
    val sourceDate: LocalDate,
    val mealCount: Int,
    val itemCount: Int
)

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
    val canUndo: Boolean = false,
    val pendingRepeat: RepeatPreview? = null
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
    val dishName: String?,
    val items: List<FoodItem>
)

/** Complete pre-copy day and newly created meal cards; makes copying fully reversible. */
private data class RepeatUndo(
    val originalMeals: List<Meal>,
    val createdMealIds: List<Long>
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
    private var repeatUndo: RepeatUndo? = null

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
                _uiState.update { current ->
                    fresh.copy(
                        message = current.message,
                        canUndo = undoBuffer != null || repeatUndo != null,
                        pendingRepeat = current.pendingRepeat
                    )
                }
            }
        }
    }

    fun previousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun nextDay() {
        if (selectedDate.value.isBefore(today)) selectedDate.value = selectedDate.value.plusDays(1)
    }
    fun goToToday() { selectedDate.value = today }

    fun deleteMeal(meal: Meal) {
        repeatUndo = null
        undoBuffer = UndoBuffer(meal.dateEpochDay, meal.mealType, meal.dishName, meal.items)
        viewModelScope.launch {
            mealRepository.deleteMeal(meal.id)
            _uiState.update { it.copy(message = "Удалено: ${meal.displayName}", canUndo = true) }
        }
    }

    fun deleteFoodItem(meal: Meal, item: FoodItem) {
        repeatUndo = null
        undoBuffer = UndoBuffer(meal.dateEpochDay, meal.mealType, meal.dishName, listOf(item))
        viewModelScope.launch {
            mealRepository.deleteFoodItem(item.id)
            _uiState.update { it.copy(message = "Удалено: ${item.name}", canUndo = true) }
        }
    }

    fun undoLastAction() {
        val deleted = undoBuffer
        if (deleted != null) {
            undoBuffer = null
            viewModelScope.launch {
                mealRepository.addItems(deleted.epochDay, deleted.mealType, deleted.items, deleted.dishName)
                _uiState.update { it.copy(message = null, canUndo = false) }
            }
            return
        }
        val repeat = repeatUndo ?: return
        repeatUndo = null
        viewModelScope.launch {
            repeat.createdMealIds.distinct().forEach { mealId -> mealRepository.deleteMeal(mealId) }
            repeat.originalMeals.forEach { meal ->
                if (meal.items.isNotEmpty()) mealRepository.addItems(meal.dateEpochDay, meal.mealType, meal.items, meal.dishName)
            }
            _uiState.update { it.copy(message = null, canUndo = false) }
        }
    }

    /** Kept for existing callers; all undo routes now go through one explicit action. */
    fun undoDelete() = undoLastAction()

    fun updateItemGrams(item: FoodItem, grams: Float) {
        if (grams <= 0f) return
        viewModelScope.launch { mealRepository.updateFoodItemGrams(item.id, grams) }
    }

    /** Shows a non-destructive preview before touching the visible day's records. */
    fun requestRepeatPreviousDay() {
        val target = selectedDate.value
        val source = target.minusDays(1)
        viewModelScope.launch {
            val meals = mealRepository.getMealsBetween(source.toEpochDay(), source.toEpochDay())
            if (meals.isEmpty()) {
                _uiState.update { it.copy(message = "За предыдущий день записей нет") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    pendingRepeat = RepeatPreview(
                        sourceDate = source,
                        mealCount = meals.size,
                        itemCount = meals.sumOf { meal -> meal.items.size }
                    )
                )
            }
        }
    }

    fun dismissRepeatPreviousDay() = _uiState.update { it.copy(pendingRepeat = null) }

    /** Replaces the current day, so the action is deterministic and fully undoable. */
    fun confirmRepeatPreviousDay() {
        val preview = _uiState.value.pendingRepeat ?: return
        val target = selectedDate.value
        viewModelScope.launch {
            val sourceMeals = mealRepository.getMealsBetween(preview.sourceDate.toEpochDay(), preview.sourceDate.toEpochDay())
            if (sourceMeals.isEmpty()) {
                _uiState.update { it.copy(pendingRepeat = null, message = "За предыдущий день записей нет") }
                return@launch
            }
            val originalMeals = _uiState.value.meals
            originalMeals.forEach { mealRepository.deleteMeal(it.id) }
            val createdIds = sourceMeals.mapNotNull { meal ->
                if (meal.items.isEmpty()) null
                else mealRepository.addItems(target.toEpochDay(), meal.mealType, meal.items, meal.dishName)
            }
            undoBuffer = null
            repeatUndo = RepeatUndo(originalMeals, createdIds)
            _uiState.update {
                it.copy(
                    pendingRepeat = null,
                    message = "Дневник заменён: скопировано продуктов ${sourceMeals.sumOf { meal -> meal.items.size }}",
                    canUndo = true
                )
            }
        }
    }

    /** Compatibility entry point for existing code; UI now requires explicit confirmation. */
    fun repeatPreviousDay() = requestRepeatPreviousDay()

    fun addWeight(weightKg: Float) {
        if (weightKg !in MIN_WEIGHT..MAX_WEIGHT) {
            _uiState.update { it.copy(message = "Введите вес от 30 до 300 кг") }
            return
        }
        val date = selectedDate.value
        viewModelScope.launch {
            mealRepository.addWeight(date.toEpochDay(), weightKg)
            val latest = _uiState.value.weightHistory.maxOfOrNull { it.dateEpochDay } ?: Long.MIN_VALUE
            if (date.toEpochDay() >= latest) userPrefs.setWeight(weightKg)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, canUndo = false) }

    private companion object {
        const val MIN_WEIGHT = 30f
        const val MAX_WEIGHT = 300f
    }
}
