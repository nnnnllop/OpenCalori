package com.opencalori.app.ui.textfood

import androidx.lifecycle.SavedStateHandle
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import com.opencalori.app.testing.FakeAiRepository
import com.opencalori.app.testing.FakeApiConfigStore
import com.opencalori.app.testing.FakeMealRepository
import com.opencalori.app.testing.FakeUserPreferences
import com.opencalori.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextFoodViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val targetDay = 20_400L
    private val ai = FakeAiRepository()
    private val meals = FakeMealRepository()
    private val prefs = FakeUserPreferences()
    private val apiConfig = FakeApiConfigStore(ApiConfig(apiKey = "sk-test"))

    private fun viewModel() = TextFoodViewModel(
        ai = ai,
        meals = meals,
        userPrefs = prefs,
        apiConfigStore = apiConfig,
        savedStateHandle = SavedStateHandle(mapOf("date" to targetDay))
    )

    private fun twoDishes() = listOf(
        RecognizedDish(
            dishName = "Паста карбонара",
            ingredients = listOf(RecognizedIngredient("паста"), RecognizedIngredient("бекон"))
        ),
        RecognizedDish(
            dishName = "Салат",
            ingredients = listOf(RecognizedIngredient("огурец"))
        )
    )

    /** The model is asked for macros only - it still likes to volunteer a weight. */
    private fun estimate(name: String, calories: Float) = EstimatedIngredient(
        name = name,
        rawGrams = 200f,
        cookedGrams = 200f,
        caloriesPer100g = calories,
        proteinPer100g = 5f,
        fatPer100g = 5f,
        carbsPer100g = 20f
    )

    private fun seedRecognition() {
        ai.dishesResult = Result.success(twoDishes())
        ai.nutritionByDish["Паста карбонара"] = Result.success(
            listOf(estimate("Паста", 160f), estimate("Бекон", 300f))
        )
        ai.nutritionByDish["Салат"] = Result.success(listOf(estimate("Огурец", 15f)))
    }

    private fun describeAndRecognize(vm: TextFoodViewModel) {
        vm.setQuery("паста карбонара и салат")
        vm.recognize()
    }

    @Test
    fun `a description is split into separate dishes`() = runTest {
        seedRecognition()
        val vm = viewModel()

        describeAndRecognize(vm)
        advanceUntilIdle()

        assertEquals(TextFoodStage.DISHES, vm.state.value.stage)
        assertEquals(listOf("Паста карбонара", "Салат"), vm.state.value.dishes.map { it.name })
        assertEquals("паста карбонара и салат", ai.lastDescription)
    }

    @Test
    fun `an empty description is not sent anywhere`() = runTest {
        val vm = viewModel()
        vm.setQuery(" ")
        vm.recognize()
        advanceUntilIdle()

        assertEquals(TextFoodStage.INPUT, vm.state.value.stage)
        assertEquals(0, ai.recognizeCalls)
    }

    @Test
    fun `nothing edible in the answer becomes a readable error`() = runTest {
        ai.dishesResult = Result.success(emptyList())
        val vm = viewModel()

        describeAndRecognize(vm)
        advanceUntilIdle()

        assertEquals(TextFoodStage.INPUT, vm.state.value.stage)
        assertTrue(vm.state.value.error.orEmpty().isNotBlank())
    }

    @Test
    fun `a failing model keeps the typed description and hides raw message`() = runTest {
        ai.dishesResult = Result.failure(IllegalStateException("Сеть недоступна"))
        val vm = viewModel()

        describeAndRecognize(vm)
        advanceUntilIdle()

        assertEquals("Не удалось обработать описание. Повторите текущий шаг.", vm.state.value.error)
        assertTrue(vm.state.value.manualRetryAvailable)
        assertEquals("паста карбонара и салат", vm.state.value.query)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `AI weights are discarded so the user has to type them`() = runTest {
        seedRecognition()
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()

        vm.calculate()
        advanceUntilIdle()

        assertEquals(TextFoodStage.GRAMS, vm.state.value.stage)
        val allItems = vm.state.value.dishes.flatMap { it.estimated }
        assertEquals(3, allItems.size)
        assertTrue(allItems.all { it.effectiveGrams == 0f })
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `saving unlocks only when every product has a valid weight`() = runTest {
        seedRecognition()
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()
        vm.calculate()
        advanceUntilIdle()

        vm.setGrams(0, 0, 180f)
        vm.setGrams(0, 1, 40f)
        assertFalse(vm.state.value.canSave)

        vm.setGrams(1, 0, 120f)
        assertTrue(vm.state.value.canSave)

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        assertEquals(2, meals.addCalls.size)
        assertEquals(listOf("Паста карбонара", "Салат"), meals.addedDishNames)
        assertTrue(meals.addCalls.all { it.first == targetDay })
        assertEquals(2, meals.meals.value.size)
    }

    @Test
    fun `save is ignored while a weight is still missing`() = runTest {
        seedRecognition()
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()
        vm.calculate()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertTrue(meals.addCalls.isEmpty())
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `the chosen meal type travels into the diary`() = runTest {
        seedRecognition()
        val vm = viewModel()
        vm.setMealType(MealType.BREAKFAST)
        describeAndRecognize(vm)
        advanceUntilIdle()
        vm.calculate()
        advanceUntilIdle()
        vm.setGrams(0, 0, 180f)
        vm.setGrams(0, 1, 40f)
        vm.setGrams(1, 0, 120f)
        vm.save()
        advanceUntilIdle()

        assertTrue(meals.addCalls.all { it.second == MealType.BREAKFAST })
    }

    @Test
    fun `dishes and ingredients can be edited before the calculation`() = runTest {
        seedRecognition()
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()

        vm.renameDish(0, "Карбонара")
        vm.renameIngredient(0, 0, "спагетти")
        vm.removeIngredient(0, 1)
        vm.addIngredient(0, "сыр")
        vm.addDish("Яблоко")
        vm.removeDish(1)

        val dishes = vm.state.value.dishes
        assertEquals(listOf("Карбонара", "Яблоко"), dishes.map { it.name })
        assertEquals(listOf("спагетти", "сыр"), dishes[0].ingredients.map { it.name })
    }

    @Test
    fun `a dish without products blocks the calculation`() = runTest {
        seedRecognition()
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()

        vm.addDish("Непонятное")

        assertFalse(vm.state.value.canCalculate)
        vm.calculate()
        advanceUntilIdle()
        assertEquals(TextFoodStage.DISHES, vm.state.value.stage)
        assertEquals(0, ai.estimateCalls)
    }

    /** P0-1: a failing write must not crash the app and must not lock the save button. */
    @Test
    fun `a failing diary write shows an error and unlocks saving`() = runTest {
        seedRecognition()
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()
        vm.calculate()
        advanceUntilIdle()
        vm.setGrams(0, 0, 180f)
        vm.setGrams(0, 1, 40f)
        vm.setGrams(1, 0, 120f)
        meals.writeFailure = IllegalStateException("disk full")

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.busy)
        assertEquals("Не удалось сохранить запись. Попробуйте ещё раз.", vm.state.value.error)
        assertEquals(TextFoodStage.GRAMS, vm.state.value.stage)
        assertTrue(vm.state.value.canSave)
    }

    /** P1-7: the text flow is an AI feature and stays closed while the AI is switched off. */
    @Test
    fun `nothing is sent when the AI is disabled in settings`() = runTest {
        seedRecognition()
        prefs.state.value = prefs.state.value.copy(aiEnabled = false)
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.aiAvailable)
        describeAndRecognize(vm)
        advanceUntilIdle()

        assertEquals(0, ai.recognizeCalls)
        assertEquals(TextFoodStage.INPUT, vm.state.value.stage)
    }

    /** P1-7: an unconfigured BYOK key closes it just like the scanner's NOT_CONFIGURED stage. */
    @Test
    fun `nothing is sent without a stored api key`() = runTest {
        seedRecognition()
        apiConfig.state.value = ApiConfig(apiKey = "")
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.aiAvailable)
        assertFalse(vm.state.value.canRecognize)
        describeAndRecognize(vm)
        advanceUntilIdle()

        assertEquals(0, ai.recognizeCalls)
    }

    @Test
    fun `an empty nutrition answer names the dish that failed`() = runTest {
        ai.dishesResult = Result.success(twoDishes())
        ai.nutritionByDish["Паста карбонара"] = Result.success(emptyList())
        val vm = viewModel()
        describeAndRecognize(vm)
        advanceUntilIdle()

        vm.calculate()
        advanceUntilIdle()

        assertEquals(TextFoodStage.DISHES, vm.state.value.stage)
        assertTrue(vm.state.value.error.orEmpty().contains("Паста карбонара"))
    }
}
