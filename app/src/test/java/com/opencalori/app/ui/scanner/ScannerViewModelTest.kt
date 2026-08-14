package com.opencalori.app.ui.scanner

import androidx.lifecycle.SavedStateHandle
import com.opencalori.app.data.repository.LocalNutritionResolver
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.NutritionSourceMode
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.testing.FakeAiRepository
import com.opencalori.app.testing.FakeApiConfigStore
import com.opencalori.app.testing.FakeImageProcessor
import com.opencalori.app.testing.FakeMealRepository
import com.opencalori.app.testing.FakeProductRepository
import com.opencalori.app.testing.FakeUserPreferences
import com.opencalori.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val targetDay = 20_310L

    private val ai = FakeAiRepository()
    private val meals = FakeMealRepository()
    private val images = FakeImageProcessor()
    private val prefs = FakeUserPreferences(UserProfile(onboardingCompleted = true))
    private val apiConfig = FakeApiConfigStore(ApiConfig(apiKey = "sk-test"))

    private val products = FakeProductRepository()
    private val localNutrition = LocalNutritionResolver(products)
    private fun viewModel() = ScannerViewModel(
        aiRepository = ai,
        mealRepository = meals,
        imageProcessor = images,
        userPrefs = prefs,
        apiConfigStore = apiConfig,
        localNutritionResolver = localNutrition,
        savedStateHandle = SavedStateHandle(mapOf("date" to targetDay))
    )

    private fun dish() = RecognizedDish(
        dishName = "Гречка с курицей",
        ingredients = listOf(RecognizedIngredient("Гречка"), RecognizedIngredient("Курица"))
    )

    private fun estimated() = listOf(
        EstimatedIngredient("Гречка", 80f, 200f, 130f, 4.5f, 1.2f, 25f, "варёная"),
        EstimatedIngredient("Курица", 150f, 120f, 165f, 31f, 3.6f, 0f, "жареная")
    )

    private fun photo() = ByteArray(8) { it.toByte() }

    @Test
    fun `suggested meal type follows the time of day`() {
        assertEquals(MealType.BREAKFAST, suggestedMealType(LocalTime.of(5, 0)))
        assertEquals(MealType.LUNCH, suggestedMealType(LocalTime.of(11, 0)))
        assertEquals(MealType.DINNER, suggestedMealType(LocalTime.of(16, 0)))
        assertEquals(MealType.SNACK, suggestedMealType(LocalTime.of(22, 0)))
    }

    @Test
    fun `without an api key the scanner explains instead of failing later`() = runTest {
        apiConfig.state.value = ApiConfig(apiKey = "")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ScannerStage.NOT_CONFIGURED, vm.uiState.value.stage)
    }

    @Test
    fun `a configured scanner opens straight into the viewfinder`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ScannerStage.CAPTURE, vm.uiState.value.stage)
    }

    @Test
    fun `a photo runs recognition and lands on the review step`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertEquals(2, vm.uiState.value.dish?.ingredients?.size)
        assertNotNull(vm.uiState.value.photo)
    }

    @Test
    fun `an unrecognised plate gives actionable advice`() = runTest {
        ai.dishResult = Result.success(RecognizedDish("", emptyList()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.error.orEmpty().contains("не распознана"))
    }

    @Test
    fun `recognition failures surface the repository message`() = runTest {
        ai.dishResult = Result.failure(IllegalStateException("Неверный API-ключ (401)"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertEquals("Неверный API-ключ (401)", vm.uiState.value.error)
    }

    @Test
    fun `edited ingredients are what gets sent for estimation`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.updateIngredient(1, "Индейка")
        vm.addIngredient("Огурец")
        vm.removeIngredient(0)
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(listOf("Индейка", "Огурец"), ai.lastIngredients)
    }

    @Test
    fun `blank ingredient names are not added`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.addIngredient("   ")

        assertEquals(2, vm.uiState.value.dish?.ingredients?.size)
    }

    @Test
    fun `skipping the list review goes straight to estimation`() = runTest {
        prefs.state.value = prefs.state.value.copy(aiSkipListReview = true)
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())

        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(1, ai.estimateCalls)
        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
    }

    @Test
    fun `skipping the grams review jumps to the final confirmation`() = runTest {
        prefs.state.value = prefs.state.value.copy(aiSkipGramsReview = true)
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())

        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_FINAL, vm.uiState.value.stage)
    }

    @Test
    fun `skipping every confirmation saves the meal by itself`() = runTest {
        prefs.state.value = prefs.state.value.copy(
            aiSkipListReview = true,
            aiSkipGramsReview = true,
            aiSkipFinalReview = true
        )
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())

        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.SAVED, vm.uiState.value.stage)
        assertEquals(1, meals.addCalls.size)
    }

    @Test
    fun `the skip-final switch is honoured from the grams screen`() = runTest {
        // This switch used to exist in settings and do nothing at this point.
        prefs.state.value = prefs.state.value.copy(aiSkipFinalReview = true)
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())

        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)

        vm.proceedToFinal()
        advanceUntilIdle()

        assertEquals(ScannerStage.SAVED, vm.uiState.value.stage)
    }

    @Test
    fun `hybrid mode keeps AI weights but replaces every macro with local catalogue values`() = runTest {
        prefs.state.value = prefs.state.value.copy(nutritionSourceMode = NutritionSourceMode.HYBRID)
        products.catalogue.value = listOf(
            Product(1, "\u0413\u0440\u0435\u0447\u043a\u0430", 113f, 3.6f, 1.1f, 21.3f),
            Product(2, "\u041a\u0443\u0440\u0438\u0446\u0430", 170f, 30f, 4f, 0f)
        )
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertEquals(113f, vm.uiState.value.estimated[0].caloriesPer100g)
        assertEquals(21.3f, vm.uiState.value.estimated[0].carbsPer100g)
        assertEquals(170f, vm.uiState.value.estimated[1].caloriesPer100g)
        assertEquals(200f, vm.uiState.value.estimated[0].cookedGrams)
        assertEquals(120f, vm.uiState.value.estimated[1].cookedGrams)
    }
    @Test
    fun `the meal is written to the day the diary was showing`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.proceedToFinal()
        advanceUntilIdle()

        vm.saveMeal()
        advanceUntilIdle()

        assertEquals(targetDay, meals.addCalls.single().first)
    }

    @Test
    fun `saved items carry the cooked weight and macros`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.setMealType(MealType.LUNCH)
        vm.proceedToFinal()
        advanceUntilIdle()
        vm.saveMeal()
        advanceUntilIdle()

        val (_, type, items) = meals.addCalls.single()
        assertEquals(MealType.LUNCH, type)
        assertEquals(200f, items.first().grams, 0.01f)
        assertEquals(260f, items.first().calories, 0.01f)
    }

    @Test
    fun `double-tapping save does not log the meal twice`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.proceedToFinal()
        advanceUntilIdle()

        vm.saveMeal()
        vm.saveMeal()
        advanceUntilIdle()

        assertEquals(1, meals.addCalls.size)
    }

    @Test
    fun `editing the cooked weight rescales the raw weight`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        vm.setGrams(0, 100f)

        val item = vm.uiState.value.estimated.first()
        assertEquals(100f, item.cookedGrams, 0.01f)
        assertEquals(40f, item.rawGrams, 0.01f)
        assertEquals(130f, item.totalCalories, 0.01f)
    }

    @Test
    fun `editing the raw weight changes the total too`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        vm.setGramsEditMode(GramsEditMode.RAW)
        vm.setGrams(0, 40f)

        val item = vm.uiState.value.estimated.first()
        assertEquals(100f, item.cookedGrams, 0.01f)
        assertEquals(130f, item.totalCalories, 0.01f)
    }

    @Test
    fun `removing an item drops it from the totals`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(estimated())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        vm.removeEstimated(1)

        assertEquals(1, vm.uiState.value.estimated.size)
        assertEquals(260f, vm.uiState.value.totalCalories, 0.01f)
    }

    @Test
    fun `cancelling an analysis returns to the viewfinder`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())

        vm.cancelAnalysis()
        advanceUntilIdle()

        assertEquals(ScannerStage.CAPTURE, vm.uiState.value.stage)
    }

    @Test
    fun `retry reuses the photo instead of demanding a new one`() = runTest {
        ai.dishResult = Result.failure(IllegalStateException("Сбой провайдера"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)

        ai.dishResult = Result.success(dish())
        vm.retry()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertEquals(1, images.calls)
        assertEquals(2, ai.recognizeCalls)
    }

    @Test
    fun `retake clears the previous photo`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.retake()
        advanceUntilIdle()

        assertEquals(ScannerStage.CAPTURE, vm.uiState.value.stage)
        assertEquals(null, vm.uiState.value.photo)
        assertEquals(null, vm.uiState.value.dish)
    }

    @Test
    fun `a broken image file is reported as a photo problem`() = runTest {
        images.failure = IllegalStateException("Не удалось декодировать изображение")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertEquals(0, ai.recognizeCalls)
    }

    @Test
    fun `a silent camera failure now reaches the user`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onCaptureFailed(null)

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `estimation failure keeps the photo so the user can retry`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.failure(IllegalStateException("Слишком много запросов (429)"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertNotNull(vm.uiState.value.photo)
    }
}
