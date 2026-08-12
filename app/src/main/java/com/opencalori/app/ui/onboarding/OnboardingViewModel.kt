package com.opencalori.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.data.preferences.UserPreferencesStore
import com.opencalori.app.data.repository.MealRepository
import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.CalorieGoal
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPrefs: UserPreferencesStore,
    private val mealRepository: MealRepository,
    private val calculateTdee: CalculateTdeeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setGender(g: Gender) = _uiState.update { it.copy(gender = g) }
    fun setAge(v: String) = _uiState.update { it.copy(age = v.filter { c -> c.isDigit() }.take(3)) }
    fun setHeight(v: String) = _uiState.update { it.copy(height = v.filter { c -> c.isDigit() }.take(3)) }
    fun setWeight(v: String) = _uiState.update { it.copy(weight = v.filter { c -> c.isDigit() || c == '.' }.take(5)) }
    fun setActivity(a: ActivityLevel) = _uiState.update { it.copy(activityLevel = a) }
    fun setGoal(g: Goal) = _uiState.update { it.copy(goal = g) }

    fun next() {
        val s = _uiState.value
        if (s.step < 3) {
            _uiState.update { it.copy(step = it.step + 1) }
        } else {
            // compute result on final step
            val profile = buildProfile()
            _uiState.update { it.copy(result = calculateTdee(profile)) }
        }
    }

    fun back() {
        if (_uiState.value.step > 0 && _uiState.value.result == null) {
            _uiState.update { it.copy(step = it.step - 1) }
        }
    }

    private fun buildProfile(): UserProfile {
        val s = _uiState.value
        return UserProfile(
            gender = s.gender,
            age = s.age.toIntOrNull() ?: 25,
            heightCm = s.height.toFloatOrNull() ?: 175f,
            weightKg = s.weight.toFloatOrNull() ?: 70f,
            activityLevel = s.activityLevel,
            goal = s.goal,
            onboardingCompleted = true
        )
    }

    fun saveAndFinish(onDone: () -> Unit) {
        val profile = buildProfile()
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            userPrefs.saveProfile(profile)
            mealRepository.addWeight(LocalDate.now().toEpochDay(), profile.weightKg)
            onDone()
        }
    }

    fun canProceed(): Boolean {
        val s = _uiState.value
        return when (s.step) {
            1 -> (s.age.toIntOrNull() ?: 0) in 10..120 &&
                    (s.height.toFloatOrNull() ?: 0f) in 100f..250f &&
                    (s.weight.toFloatOrNull() ?: 0f) in 30f..300f
            else -> true
        }
    }
}
