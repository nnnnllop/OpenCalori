package com.opencalori.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.CalorieGoal
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.domain.repository.UserPreferences
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import com.opencalori.app.ui.util.NumberFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val gender: Gender = Gender.MALE,
    val age: String = "25",
    val height: String = "175",
    val weight: String = "70",
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.MAINTAIN,
    val result: CalorieGoal? = null,
    val saving: Boolean = false
) {
    val ageValue: Int? get() = age.toIntOrNull()?.takeIf { it in 10..120 }
    val heightValue: Float? get() = NumberFormat.parse(height)?.takeIf { it in 100f..250f }
    val weightValue: Float? get() = NumberFormat.parse(weight)?.takeIf { it in 30f..300f }
    val metricsValid: Boolean get() = ageValue != null && heightValue != null && weightValue != null
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPrefs: UserPreferences,
    private val mealRepository: MealRepository,
    private val calculateTdee: CalculateTdeeUseCase,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setGender(value: Gender) = _uiState.update { it.copy(gender = value) }

    fun setAge(value: String) = _uiState.update { it.copy(age = value.filter(Char::isDigit).take(3)) }

    fun setHeight(value: String) = _uiState.update {
        it.copy(height = NumberFormat.sanitizeDecimalInput(value, maxLength = 5))
    }

    fun setWeight(value: String) = _uiState.update {
        it.copy(weight = NumberFormat.sanitizeDecimalInput(value, maxLength = 5))
    }

    fun setActivity(value: ActivityLevel) = _uiState.update { it.copy(activityLevel = value) }

    fun setGoal(value: Goal) = _uiState.update { it.copy(goal = value) }

    fun next() {
        val state = _uiState.value
        if (state.step < LAST_STEP) {
            _uiState.update { it.copy(step = it.step + 1) }
        } else {
            _uiState.update { it.copy(result = calculateTdee(buildProfile())) }
        }
    }

    fun back() {
        if (_uiState.value.step > 0 && _uiState.value.result == null) {
            _uiState.update { it.copy(step = it.step - 1) }
        }
    }

    /** Returns from the result card back to the questionnaire. */
    fun editAgain() = _uiState.update { it.copy(result = null) }

    fun saveAndFinish(onDone: () -> Unit) {
        if (_uiState.value.saving) return
        val profile = buildProfile()
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            userPrefs.saveProfile(profile)
            mealRepository.addWeight(LocalDate.now(clock).toEpochDay(), profile.weightKg)
            onDone()
        }
    }

    fun canProceed(): Boolean {
        val state = _uiState.value
        return if (state.step == 1) state.metricsValid else true
    }

    private fun buildProfile(): UserProfile {
        val state = _uiState.value
        return UserProfile(
            gender = state.gender,
            age = state.ageValue ?: 25,
            heightCm = state.heightValue ?: 175f,
            weightKg = state.weightValue ?: 70f,
            activityLevel = state.activityLevel,
            goal = state.goal,
            onboardingCompleted = true
        )
    }

    private companion object {
        const val LAST_STEP = 3
    }
}
