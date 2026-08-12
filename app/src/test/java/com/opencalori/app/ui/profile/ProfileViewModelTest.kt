package com.opencalori.app.ui.profile

import com.opencalori.app.domain.model.ActivityLevel
import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import com.opencalori.app.testing.FakeMealRepository
import com.opencalori.app.testing.FakeUserPreferences
import com.opencalori.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 12)
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val prefs = FakeUserPreferences(
        UserProfile(
            gender = Gender.MALE,
            age = 30,
            heightCm = 180f,
            weightKg = 85f,
            activityLevel = ActivityLevel.SEDENTARY,
            goal = Goal.MAINTAIN,
            onboardingCompleted = true,
            aiEnabled = true,
            aiSkipGramsReview = true
        )
    )
    private val meals = FakeMealRepository()

    private fun viewModel() = ProfileViewModel(prefs, meals, CalculateTdeeUseCase(), clock)

    @Test
    fun `the form is prefilled from the stored profile`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals("30", state.age)
        assertEquals("180", state.height)
        // 85f must not render as "85.0" in an editable field.
        assertEquals("85", state.weight)
    }

    @Test
    fun `out of range values block saving`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setAge("200")
        assertFalse(vm.uiState.value.isValid)

        vm.setAge("35")
        assertTrue(vm.uiState.value.isValid)

        vm.setWeight("5")
        assertFalse(vm.uiState.value.isValid)
    }

    @Test
    fun `an empty field is invalid rather than silently zero`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setWeight("")

        assertNull(vm.uiState.value.weightValue)
        assertFalse(vm.uiState.value.isValid)
    }

    @Test
    fun `the preview reacts to the goal before anything is saved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val maintain = vm.preview()?.targetCalories

        vm.setGoal(Goal.LOSE)
        val losing = vm.preview()?.targetCalories

        assertEquals(500, (maintain ?: 0) - (losing ?: 0))
    }

    @Test
    fun `saving updates the calorie target`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val before = CalculateTdeeUseCase()(prefs.profile.first()).targetCalories

        vm.setWeight("78")
        vm.setActivity(ActivityLevel.MODERATE)
        vm.save()
        advanceUntilIdle()

        val after = CalculateTdeeUseCase()(prefs.profile.first()).targetCalories
        assertTrue(after != before)
        assertEquals(78f, prefs.state.value.weightKg, 0.01f)
    }

    @Test
    fun `a changed weight is also logged in the weight diary`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setWeight("78")
        vm.save()
        advanceUntilIdle()

        val history = meals.getWeightHistory().first()
        assertEquals(1, history.size)
        assertEquals(today.toEpochDay(), history.single().dateEpochDay)
        assertEquals(78f, history.single().weightKg, 0.01f)
    }

    @Test
    fun `saving without touching the weight does not spam the chart`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setGoal(Goal.GAIN)
        vm.save()
        advanceUntilIdle()

        assertTrue(meals.getWeightHistory().first().isEmpty())
    }

    @Test
    fun `ai preferences survive a profile edit`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setWeight("80")
        vm.save()
        advanceUntilIdle()

        assertTrue(prefs.state.value.aiEnabled)
        assertTrue(prefs.state.value.aiSkipGramsReview)
    }

    @Test
    fun `onboarding is not re-triggered after an edit`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setWeight("80")
        vm.save()
        advanceUntilIdle()

        assertTrue(prefs.state.value.onboardingCompleted)
    }

    @Test
    fun `a comma typed on a russian keyboard is accepted`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setWeight("78,4")

        assertEquals(78.4f, vm.uiState.value.weightValue!!, 0.01f)
    }
}
