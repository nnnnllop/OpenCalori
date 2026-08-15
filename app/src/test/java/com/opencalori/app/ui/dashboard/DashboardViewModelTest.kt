package com.opencalori.app.ui.dashboard

import app.cash.turbine.test
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.domain.usecase.CalculateTdeeUseCase
import com.opencalori.app.testing.FakeApiConfigStore
import com.opencalori.app.testing.FakeMealRepository
import com.opencalori.app.testing.FakeUserPreferences
import com.opencalori.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 12)
    private val clock = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone)

    private val meals = FakeMealRepository()
    private val prefs = FakeUserPreferences(UserProfile(onboardingCompleted = true, weightKg = 80f))
    private val apiConfig = FakeApiConfigStore(ApiConfig(apiKey = ""))

    private fun viewModel() = DashboardViewModel(
        mealRepository = meals,
        userPrefs = prefs,
        apiConfigStore = apiConfig,
        calculateTdee = CalculateTdeeUseCase(),
        clock = clock
    )

    private fun food(name: String, grams: Float, kcal: Float) = FoodItem(
        name = name, grams = grams,
        caloriesPer100g = kcal, proteinPer100g = 10f, fatPer100g = 5f, carbsPer100g = 20f
    )

    @Test
    fun `starts on today`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(today, vm.uiState.value.date)
        assertTrue(vm.uiState.value.isToday)
    }

    @Test
    fun `consumed calories sum the day's meals`() = runTest {
        meals.seedMeal(today.toEpochDay(), MealType.BREAKFAST, food("Овсянка", 200f, 90f))
        meals.seedMeal(today.toEpochDay(), MealType.LUNCH, food("Рис", 150f, 120f))

        val vm = viewModel()
        advanceUntilIdle()

        // 200g * 0.9 + 150g * 1.2 = 180 + 180
        assertEquals(360f, vm.uiState.value.consumedCalories, 0.1f)
    }

    @Test
    fun `yesterday's meals do not leak into today`() = runTest {
        meals.seedMeal(today.minusDays(1).toEpochDay(), MealType.DINNER, food("Паста", 300f, 130f))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.meals.isEmpty())
    }

    @Test
    fun `stepping back a day loads that day's meals`() = runTest {
        meals.seedMeal(today.minusDays(1).toEpochDay(), MealType.DINNER, food("Паста", 100f, 130f))

        val vm = viewModel()
        advanceUntilIdle()
        vm.previousDay()
        advanceUntilIdle()

        assertEquals(today.minusDays(1), vm.uiState.value.date)
        assertFalse(vm.uiState.value.isToday)
        assertEquals(1, vm.uiState.value.meals.size)
    }

    @Test
    fun `cannot navigate into the future`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.nextDay()
        advanceUntilIdle()

        assertEquals(today, vm.uiState.value.date)
    }

    @Test
    fun `jumping back to today works from any day`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repeat(5) { vm.previousDay() }
        advanceUntilIdle()

        vm.goToToday()
        advanceUntilIdle()

        assertEquals(today, vm.uiState.value.date)
    }

    @Test
    fun `scanner stays locked until a key is configured`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.aiEnabled)
        assertFalse(vm.uiState.value.scannerReady)
    }

    @Test
    fun `scanner unlocks once the api is configured`() = runTest {
        apiConfig.state.value = ApiConfig(apiKey = "sk-test")

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.scannerReady)
    }

    @Test
    fun `disabling ai hides the scanner entirely`() = runTest {
        apiConfig.state.value = ApiConfig(apiKey = "sk-test")
        prefs.state.value = prefs.state.value.copy(aiEnabled = false)

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.scannerReady)
    }

    @Test
    fun `deleting a meal can be undone`() = runTest {
        val meal = meals.seedMeal(today.toEpochDay(), MealType.LUNCH, food("Суп", 300f, 50f))
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteMeal(meal)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.meals.isEmpty())
        assertTrue(vm.uiState.value.canUndo)

        vm.undoDelete()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.meals.size)
        assertEquals(150f, vm.uiState.value.consumedCalories, 0.1f)
    }

    @Test
    fun `undo restores the meal onto the same day it was deleted from`() = runTest {
        val yesterday = today.minusDays(1)
        val meal = meals.seedMeal(yesterday.toEpochDay(), MealType.DINNER, food("Рыба", 200f, 100f))
        val vm = viewModel()
        advanceUntilIdle()
        vm.previousDay()
        advanceUntilIdle()

        vm.deleteMeal(meal)
        advanceUntilIdle()
        vm.undoDelete()
        advanceUntilIdle()

        assertEquals(yesterday.toEpochDay(), meals.addCalls.last().first)
    }

    @Test
    fun `deleting a single item leaves the rest of the meal alone`() = runTest {
        val meal = meals.seedMeal(
            today.toEpochDay(),
            MealType.BREAKFAST,
            food("Яйцо", 100f, 155f),
            food("Хлеб", 50f, 250f)
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteFoodItem(meal, meal.items.first())
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.meals.single().items.size)
    }

    @Test
    fun `editing grams updates the calorie total`() = runTest {
        val meal = meals.seedMeal(today.toEpochDay(), MealType.SNACK, food("Орехи", 100f, 600f))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateItemGrams(meal.items.first(), 50f)
        advanceUntilIdle()

        assertEquals(300f, vm.uiState.value.consumedCalories, 0.1f)
    }

    @Test
    fun `zero grams is ignored rather than wiping the entry`() = runTest {
        val meal = meals.seedMeal(today.toEpochDay(), MealType.SNACK, food("Орехи", 100f, 600f))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateItemGrams(meal.items.first(), 0f)
        advanceUntilIdle()

        assertEquals(600f, vm.uiState.value.consumedCalories, 0.1f)
    }

    @Test
    fun `repeat previous day copies yesterday's meals into the shown day`() = runTest {
        meals.seedMeal(today.minusDays(1).toEpochDay(), MealType.BREAKFAST, food("Каша", 250f, 90f))
        meals.seedMeal(today.minusDays(1).toEpochDay(), MealType.LUNCH, food("Суп", 300f, 50f))

        val vm = viewModel()
        advanceUntilIdle()
        vm.requestRepeatPreviousDay()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.pendingRepeat?.mealCount)
        vm.confirmRepeatPreviousDay()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.meals.size)
        assertTrue(meals.addCalls.all { it.first == today.toEpochDay() })
        vm.undoLastAction()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.meals.isEmpty())
    }

    @Test
    fun `repeat previous day says so when there is nothing to copy`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.requestRepeatPreviousDay()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.message)
        assertTrue(vm.uiState.value.meals.isEmpty())
    }

    @Test
    fun `logging weight also updates the profile the goal is built from`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val goalBefore = vm.uiState.value.goal?.targetCalories

        vm.addWeight(70f)
        advanceUntilIdle()

        assertEquals(70f, prefs.state.value.weightKg, 0.01f)
        assertTrue(goalBefore != vm.uiState.value.goal?.targetCalories)
    }

    @Test
    fun `absurd weights are rejected`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addWeight(5f)
        vm.addWeight(500f)
        advanceUntilIdle()

        assertEquals(80f, prefs.state.value.weightKg, 0.01f)
    }

    @Test
    fun `backdated weight does not overwrite the current profile weight`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.addWeight(75f)
        advanceUntilIdle()

        vm.previousDay()
        advanceUntilIdle()
        vm.addWeight(90f)
        advanceUntilIdle()

        assertEquals(75f, prefs.state.value.weightKg, 0.01f)
    }

    // ---- Diary hierarchy: date -> meal -> dish -> products ----

    @Test
    fun `dishes of one meal type are grouped under a single meal section`() = runTest {
        meals.seedMeal(
            today.toEpochDay(),
            MealType.BREAKFAST,
            food("Паста", 180f, 160f),
            dishName = "Паста карбонара"
        )
        meals.seedMeal(
            today.toEpochDay(),
            MealType.BREAKFAST,
            food("Огурец", 100f, 15f),
            food("Помидор", 120f, 20f),
            dishName = "Овощной салат"
        )
        meals.seedMeal(today.toEpochDay(), MealType.DINNER, food("Рыба", 200f, 100f))

        val vm = viewModel()
        advanceUntilIdle()

        val sections = vm.uiState.value.sections
        assertEquals(listOf(MealType.BREAKFAST, MealType.DINNER), sections.map { it.mealType })
        val breakfast = sections.first()
        assertEquals(2, breakfast.dishes.size)
        assertEquals(
            listOf("Паста карбонара", "Овощной салат"),
            breakfast.dishes.map { it.displayName }
        )
        assertEquals(3, breakfast.productCount)
        // 180g*1.6 + 100g*0.15 + 120g*0.2 = 288 + 15 + 24
        assertEquals(327f, breakfast.calories, 0.1f)
    }

    @Test
    fun `an empty meal type is not rendered as a section`() = runTest {
        meals.seedMeal(today.toEpochDay(), MealType.LUNCH, food("Суп", 300f, 50f))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.sections.size)
        assertEquals(MealType.LUNCH, vm.uiState.value.sections.single().mealType)
    }

    @Test
    fun `deleting one dish keeps the other dish of the same meal`() = runTest {
        val pasta = meals.seedMeal(
            today.toEpochDay(),
            MealType.BREAKFAST,
            food("Паста", 180f, 160f),
            dishName = "Паста карбонара"
        )
        meals.seedMeal(
            today.toEpochDay(),
            MealType.BREAKFAST,
            food("Огурец", 100f, 15f),
            dishName = "Овощной салат"
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteMeal(pasta)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.sections.single().dishes.size)
        assertEquals("Овощной салат", vm.uiState.value.sections.single().dishes.single().displayName)
    }

    @Test
    fun `undo puts the deleted dish back under its own title`() = runTest {
        val salad = meals.seedMeal(
            today.toEpochDay(),
            MealType.BREAKFAST,
            food("Огурец", 100f, 15f),
            dishName = "Овощной салат"
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteMeal(salad)
        advanceUntilIdle()
        vm.undoDelete()
        advanceUntilIdle()

        assertEquals("Овощной салат", meals.addedDishNames.last())
        assertEquals("Овощной салат", vm.uiState.value.meals.single().displayName)
    }

    @Test
    fun `undo of a product deletion restores only that product`() = runTest {
        val dish = meals.seedMeal(
            today.toEpochDay(),
            MealType.LUNCH,
            food("Рис", 150f, 120f),
            food("Курица", 100f, 170f),
            dishName = "Рис с курицей"
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteFoodItem(dish, dish.items.last())
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.meals.single().items.size)

        vm.undoDelete()
        advanceUntilIdle()

        assertEquals(listOf("Рис", "Курица"), vm.uiState.value.meals.single().items.map { it.name })
        assertEquals("Рис с курицей", meals.addedDishNames.last())
    }

    @Test
    fun `state emits an update when a meal is added`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            assertEquals(0, awaitItem().meals.size)
            meals.addItems(today.toEpochDay(), MealType.DINNER, listOf(food("Курица", 200f, 150f)))
            assertEquals(1, awaitItem().meals.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
