package com.opencalori.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.preferences.UserPreferencesStore
import com.opencalori.app.data.repository.MealRepository
import com.opencalori.app.domain.model.CalorieGoal
import com.opencalori.app.domain.model.Meal
import com.opencalori.app.domain.model.WeightEntry
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val date: LocalDate = LocalDate.now(),
    val goal: CalorieGoal? = null,
    val meals: List<Meal> = emptyList(),
    val weightHistory: List<WeightEntry> = emptyList(),
    val currentWeight: Float? = null
) {
    val consumedCalories: Float get() = meals.sumOf { it.totalCalories.toDouble() }.toFloat()
    val consumedProtein: Float get() = meals.sumOf { it.totalProtein.toDouble() }.toFloat()
    val consumedFat: Float get() = meals.sumOf { it.totalFat.toDouble() }.toFloat()
    val consumedCarbs: Float get() = meals.sumOf { it.totalCarbs.toDouble() }.toFloat()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val userPrefs: UserPreferencesStore,
    private val calculateTdee: CalculateTdeeUseCase
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedDate.flatMapLatest { mealRepository.getMealsForDay(it.toEpochDay()) },
                userPrefs.profile,
                mealRepository.getWeightHistory(),
                selectedDate
            ) { meals, profile, weight, date ->
                val goal = calculateTdee(profile)
                DashboardUiState(
                    date = date,
                    goal = goal,
                    meals = meals,
                    weightHistory = weight,
                    currentWeight = weight.lastOrNull()?.weightKg ?: profile.weightKg
                )
            }.collect { _uiState.value = it }
        }
    }

    fun previousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun nextDay() {
        if (selectedDate.value.isBefore(LocalDate.now())) {
            selectedDate.value = selectedDate.value.plusDays(1)
        }
    }

    fun deleteMeal(mealId: Long) {
        viewModelScope.launch { mealRepository.deleteMeal(mealId) }
    }

    fun addWeight(weightKg: Float) {
        viewModelScope.launch {
            mealRepository.addWeight(LocalDate.now().toEpochDay(), weightKg)
        }
    }
}
