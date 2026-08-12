package com.opencalori.app.ui.onboarding

import com.opencalori.app.domain.model.Gender
import com.opencalori.app.domain.model.Goal
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 12)
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val prefs = FakeUserPreferences()
    private val meals = FakeMealRepository()

    private fun viewModel() = OnboardingViewModel(prefs, meals, CalculateTdeeUseCase(), clock)

    @Test
    fun `walking through the steps produces a calorie goal`() = runTest {
        val vm = viewModel()

        vm.setGender(Gender.FEMALE)
        vm.next()
        vm.setAge("28")
        vm.setHeight("165")
        vm.setWeight("60")
        vm.next()
        vm.next()
        vm.setGoal(Goal.LOSE)
        vm.next()

        assertNotNull(vm.uiState.value.result)
    }

    @Test
    fun `nonsense body metrics block the next step`() = runTest {
        val vm = viewModel()
        vm.next()

        vm.setAge("5")
        assertFalse(vm.canProceed())

        vm.setAge("28")
        vm.setHeight("40")
        assertFalse(vm.canProceed())

        vm.setHeight("165")
        assertTrue(vm.canProceed())
    }

    @Test
    fun `other steps never trap the user`() = runTest {
        val vm = viewModel()

        assertTrue(vm.canProceed())
        vm.next()
        vm.next()
        assertTrue(vm.canProceed())
    }

    @Test
    fun `back from the result screen returns to the questionnaire`() = runTest {
        val vm = viewModel()
        repeat(4) { vm.next() }
        assertNotNull(vm.uiState.value.result)

        vm.editAgain()

        assertNull(vm.uiState.value.result)
        assertEquals(3, vm.uiState.value.step)
    }

    @Test
    fun `finishing stores the profile and the first weigh-in`() = runTest {
        val vm = viewModel()
        vm.next()
        vm.setWeight("72")
        repeat(3) { vm.next() }

        var finished = false
        vm.saveAndFinish { finished = true }
        advanceUntilIdle()

        assertTrue(finished)
        val profile = prefs.profile.first()
        assertTrue(profile.onboardingCompleted)
        assertEquals(72f, profile.weightKg, 0.01f)

        val history = meals.getWeightHistory().first()
        assertEquals(today.toEpochDay(), history.single().dateEpochDay)
    }

    @Test
    fun `tapping start twice does not double-save`() = runTest {
        val vm = viewModel()
        repeat(4) { vm.next() }

        var calls = 0
        vm.saveAndFinish { calls++ }
        vm.saveAndFinish { calls++ }
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `age input rejects letters`() = runTest {
        val vm = viewModel()

        vm.setAge("3a0")

        assertEquals("30", vm.uiState.value.age)
    }
}
