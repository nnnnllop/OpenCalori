package com.opencalori.app.ui.scanner

import androidx.lifecycle.SavedStateHandle
import com.opencalori.app.data.repository.LocalNutritionResolver
import com.opencalori.app.data.repository.PhotoNutritionResolver
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import com.opencalori.app.domain.model.UserProfile
import com.opencalori.app.testing.FakeAiRepository
import com.opencalori.app.testing.FakeApiConfigStore
import com.opencalori.app.testing.FakeDishRepository
import com.opencalori.app.testing.FakeImageProcessor
import com.opencalori.app.testing.FakeMealRepository
import com.opencalori.app.testing.FakeProductRepository
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
    private val dishes = FakeDishRepository()
    private val localNutrition = LocalNutritionResolver(products)
    private val photoNutrition = PhotoNutritionResolver(dishes, localNutrition)

    private fun viewModel() = ScannerViewModel(
        aiRepository = ai,
        mealRepository = meals,
        imageProcessor = images,
        userPrefs = prefs,
        apiConfigStore = apiConfig,
        localNutritionResolver = localNutrition,
        photoNutritionResolver = photoNutrition,
        savedStateHandle = SavedStateHandle(mapOf("date" to targetDay))
    )

    private fun dish() = RecognizedDish(
        dishName = "\u0413\u0440\u0435\u0447\u043a\u0430 \u0441 \u043a\u0443\u0440\u0438\u0446\u0435\u0439",
        ingredients = listOf(RecognizedIngredient("\u0413\u0440\u0435\u0447\u043a\u0430"), RecognizedIngredient("\u041a\u0443\u0440\u0438\u0446\u0430"))
    )

    private fun seedProducts() {
        products.catalogue.value = listOf(
            Product(1, "\u0413\u0440\u0435\u0447\u043a\u0430", 113f, 3.6f, 1.1f, 21.3f),
            Product(2, "\u041a\u0443\u0440\u0438\u0446\u0430", 170f, 30f, 4f, 0f)
        )
    }

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
    fun `a photo always opens the confirmation step`() = runTest {
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
    fun `list confirmation remains mandatory even when old skip preference is enabled`() = runTest {
        prefs.state.value = prefs.state.value.copy(aiSkipListReview = true)
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertEquals(0, ai.estimateCalls)
    }

    @Test
    fun `known local dish uses its local serving and never requests AI macros`() = runTest {
        dishes.catalogue.value = listOf(
            Dish(10, "\u041f\u0430\u0441\u0442\u0430 \u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0430", listOf("carbonara"), emptyList(), 280f, 210f, 11f, 12f, 22f)
        )
        ai.dishResult = Result.success(RecognizedDish("carbonara", listOf(RecognizedIngredient("\u043f\u0430\u0441\u0442\u0430"))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertEquals("\u041f\u0430\u0441\u0442\u0430 \u043a\u0430\u0440\u0431\u043e\u043d\u0430\u0440\u0430", vm.uiState.value.localDish?.name)
        assertEquals(280f, vm.uiState.value.estimated.single().cookedGrams, 0.01f)
        assertEquals(210f, vm.uiState.value.estimated.single().caloriesPer100g, 0.01f)
        assertEquals(0, ai.estimateCalls)
    }

    @Test
    fun `unknown dish with locally matched components remains a local draft`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.isLocalDraft)
        assertTrue(vm.uiState.value.unmatchedIngredients.isEmpty())
        assertEquals(2, vm.uiState.value.estimated.size)
        assertEquals(113f, vm.uiState.value.estimated.first().caloriesPer100g, 0.01f)
        assertTrue(dishes.catalogue.value.isEmpty())
        assertTrue(products.custom.value.isEmpty())
        assertEquals(0, ai.estimateCalls)
    }

    @Test
    fun `unmatched local component returns to the draft instead of saving invented macros`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(
            RecognizedDish("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435", listOf(RecognizedIngredient("\u043a\u0443\u0440\u0438\u0446\u0430"), RecognizedIngredient("\u043e\u0431\u043b\u0430\u0447\u043d\u044b\u0439 \u0441\u043e\u0443\u0441")))
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.isLocalDraft)
        assertEquals(listOf("\u043e\u0431\u043b\u0430\u0447\u043d\u044b\u0439 \u0441\u043e\u0443\u0441"), vm.uiState.value.unmatchedIngredients)
        assertEquals(0, meals.addCalls.size)
    }

    @Test
    fun `editing a confirmed draft re-resolves local ingredient names`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(RecognizedDish("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0435", listOf(RecognizedIngredient("\u043e\u0431\u043b\u0430\u0447\u043d\u044b\u0439 \u0441\u043e\u0443\u0441"))))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)

        vm.updateIngredient(0, "\u043a\u0443\u0440\u0438\u0446\u0430")
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertEquals("\u041a\u0443\u0440\u0438\u0446\u0430", vm.uiState.value.estimated.single().name)
    }

    @Test
    fun `local macro values stay fixed while grams remain editable`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.setGrams(0, 180f)

        val item = vm.uiState.value.estimated.first()
        assertEquals(180f, item.cookedGrams, 0.01f)
        assertEquals(113f, item.caloriesPer100g, 0.01f)
        assertEquals(203.4f, item.totalCalories, 0.01f)
        assertEquals(0, ai.estimateCalls)
    }

    @Test
    fun `the meal is written to the day the diary was showing`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(dish())
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
    fun `double-tapping save does not log a confirmed local draft twice`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(dish())
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
    fun `unrecognised plate gives actionable advice`() = runTest {
        ai.dishResult = Result.success(RecognizedDish("", emptyList()))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.error.orEmpty().contains("\u043d\u0435 \u0440\u0430\u0441\u043f\u043e\u0437\u043d\u0430\u043d\u0430"))
    }

    @Test
    fun `recognition failure surfaces the repository message and keeps retryable photo`() = runTest {
        ai.dishResult = Result.failure(IllegalStateException("\u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 API-\u043a\u043b\u044e\u0447 (401)"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertEquals("\u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 API-\u043a\u043b\u044e\u0447 (401)", vm.uiState.value.error)
        assertNotNull(vm.uiState.value.photo)
    }

    @Test
    fun `retake clears the previous local draft and keeps the target diary date`() = runTest {
        seedProducts()
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        vm.retake()
        advanceUntilIdle()

        assertEquals(ScannerStage.CAPTURE, vm.uiState.value.stage)
        assertEquals(null, vm.uiState.value.photo)
        assertEquals(targetDay, vm.uiState.value.targetDate.toEpochDay())
        assertFalse(vm.uiState.value.isLocalDraft)
    }
}
