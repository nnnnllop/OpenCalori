package com.opencalori.app.ui.profile

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ProfileUiState(
    val loaded: Boolean = false,
    val gender: Gender = Gender.MALE,
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.MAINTAIN,
    val saved: Boolean = false
) {
    val ageValue: Int? get() = age.toIntOrNull()?.takeIf { it in 10..120 }
    val heightValue: Float? get() = NumberFormat.parse(height)?.takeIf { it in 100f..250f }
    val weightValue: Float? get() = NumberFormat.parse(weight)?.takeIf { it in 30f..300f }
    val isValid: Boolean get() = ageValue != null && heightValue != null && weightValue != null
}

/**
 * Lets the user change the numbers the calorie target is built from.
 *
 * Before this screen existed the profile was write-once during onboarding, so the goal
 * stayed frozen at the starting weight forever.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPrefs: UserPreferences,
    private val mealRepository: MealRepository,
    private val calculateTdee: CalculateTdeeUseCase,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = userPrefs.profile.first()
            _uiState.value = ProfileUiState(
                loaded = true,
                gender = profile.gender,
                age = profile.age.toString(),
                height = NumberFormat.compact(profile.heightCm),
                weight = NumberFormat.compact(profile.weightKg),
                activityLevel = profile.activityLevel,
                goal = profile.goal
            )
        }
    }

    fun setGender(value: Gender) = _uiState.update { it.copy(gender = value, saved = false) }

    fun setAge(value: String) = _uiState.update {
        it.copy(age = value.filter(Char::isDigit).take(3), saved = false)
    }

    fun setHeight(value: String) = _uiState.update {
        it.copy(height = NumberFormat.sanitizeDecimalInput(value, maxLength = 5), saved = false)
    }

    fun setWeight(value: String) = _uiState.update {
        it.copy(weight = NumberFormat.sanitizeDecimalInput(value, maxLength = 5), saved = false)
    }

    fun setActivity(value: ActivityLevel) = _uiState.update { it.copy(activityLevel = value, saved = false) }

    fun setGoal(value: Goal) = _uiState.update { it.copy(goal = value, saved = false) }

    /** Live preview of the target so the effect of a change is visible before saving. */
    fun preview(): CalorieGoal? {
        val state = _uiState.value
        return calculateTdee(state.toProfile(onboardingCompleted = true) ?: return null)
    }

    fun save() {
        val state = _uiState.value
        val profile = state.toProfile(onboardingCompleted = true) ?: return
        viewModelScope.launch {
            val existing = userPrefs.profile.first()
            // Only the metrics this screen owns are written. Copying a whole profile used to
            // silently reset every setting the screen does not know about.
            userPrefs.updateMetrics(
                gender = profile.gender,
                age = profile.age,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                activityLevel = profile.activityLevel,
                goal = profile.goal
            )
            // A changed weight is also a weigh-in, otherwise the chart and the profile drift apart.
            if (existing.weightKg != profile.weightKg) {
                mealRepository.addWeight(LocalDate.now(clock).toEpochDay(), profile.weightKg)
            }
            _uiState.update { it.copy(saved = true) }
        }
    }

    private fun ProfileUiState.toProfile(onboardingCompleted: Boolean): UserProfile? {
        val age = ageValue ?: return null
        val height = heightValue ?: return null
        val weight = weightValue ?: return null
        return UserProfile(
            gender = gender,
            age = age,
            heightCm = height,
            weightKg = weight,
            activityLevel = activityLevel,
            goal = goal,
            onboardingCompleted = onboardingCompleted
        )
    }
}
