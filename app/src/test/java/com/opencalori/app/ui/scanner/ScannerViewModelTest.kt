package com.opencalori.app.ui.scanner

import androidx.lifecycle.SavedStateHandle
import com.opencalori.app.data.repository.LocalNutritionResolver
import com.opencalori.app.data.repository.PhotoNutritionResolver
import com.opencalori.app.domain.model.ApiConfig
import com.opencalori.app.domain.model.Dish
import com.opencalori.app.domain.model.EstimatedIngredient
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
    private val ai = FakeAiRepository().apply {
        nutritionResult = Result.success(
            listOf(
                EstimatedIngredient(
                    name = "\u0413\u0440\u0435\u0447\u043a\u0430 \u0441 \u043a\u0443\u0440\u0438\u0446\u0435\u0439",
                    rawGrams = 100f,
                    cookedGrams = 100f,
                    caloriesPer100g = 180f,
                    proteinPer100g = 15f,
                    fatPer100g = 6f,
                    carbsPer100g = 18f
                )
            )
        )
    }
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

    /**
     * P1-6: the "skip list review" switch used to be dead - nobody read it. Enabling it now
     * flows from stage 1 straight into the grams estimation, like the other two skip switches.
     */
    @Test
    fun `the skip list review switch goes straight to the grams estimation`() = runTest {
        prefs.state.value = prefs.state.value.copy(aiSkipListReview = true)
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertEquals(1, ai.estimateCalls)
    }

    @Test
    fun `list confirmation is mandatory while the skip switch is off`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertEquals(0, ai.estimateCalls)
    }

    /** P0-1: a failing write must not crash the app and must not lock the save button. */
    @Test
    fun `a failing diary write shows an error and unlocks saving`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.proceedToFinal()
        advanceUntilIdle()
        val stageBefore = vm.uiState.value.stage
        meals.writeFailure = IllegalStateException("disk full")

        vm.saveMeal()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.saving)
        assertEquals(stageBefore, vm.uiState.value.stage)
        assertEquals("Не удалось сохранить запись. Попробуйте ещё раз.", vm.uiState.value.error)
        assertTrue(vm.uiState.value.canSave)

        // The retry after a transient failure goes through.
        meals.writeFailure = null
        vm.saveMeal()
        advanceUntilIdle()
        assertEquals(ScannerStage.SAVED, vm.uiState.value.stage)
    }

    /** P1-5: the retry button must not be driven by a stale state snapshot. */
    @Test
    fun `retry fields take part in state equality`() {
        val base = ScannerUiState()
        assertFalse(base == base.copy(manualRetryAvailable = true))
        assertFalse(base == base.copy(failedAiStage = ScannerStage.ANALYZING_2))
        assertFalse(base == base.copy(photo = ByteArray(2) { 7 }))
        assertEquals(base, base.copy())
    }

    @Test
    fun `configured provider estimates known dishes through AI before local catalogue`() = runTest {
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
        assertEquals(1, ai.estimateCalls)
        assertEquals("\u0413\u0440\u0435\u0447\u043a\u0430 \u0441 \u043a\u0443\u0440\u0438\u0446\u0435\u0439", vm.uiState.value.estimated.single().name)
        assertEquals(180f, vm.uiState.value.estimated.single().caloriesPer100g, 0.01f)
        assertEquals(null, vm.uiState.value.localDish)
    }

    @Test
    fun `AI estimation failure becomes recoverable error instead of throwing from confirmation`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.failure(IllegalStateException("\u0441\u0435\u0442\u044c \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.error.orEmpty().contains("Не удалось рассчитать КБЖУ"))
        assertTrue(vm.uiState.value.manualRetryAvailable)
        assertNotNull(vm.uiState.value.photo)
    }

    @Test
    fun `empty AI estimate returns to editable ingredient review`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.error.orEmpty().contains("\u0418\u0418"))
    }

    @Test
    fun `confirmed ingredients are passed to AI without local draft transition`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.updateIngredient(0, "\u0440\u0438\u0441")
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        assertEquals(listOf("\u0440\u0438\u0441", "\u041a\u0443\u0440\u0438\u0446\u0430"), ai.lastIngredients)
        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertFalse(vm.uiState.value.isLocalDraft)
    }

    @Test
    fun `AI macro values stay fixed while grams remain editable`() = runTest {
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
        assertEquals(180f, item.caloriesPer100g, 0.01f)
        assertEquals(324f, item.totalCalories, 0.01f)
        assertEquals(1, ai.estimateCalls)
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
    fun `recognition failure hides raw repository message and keeps retryable photo`() = runTest {
        ai.dishResult = Result.failure(IllegalStateException("\u041d\u0435\u0432\u0435\u0440\u043d\u044b\u0439 API-\u043a\u043b\u044e\u0447 (401)"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.ERROR, vm.uiState.value.stage)
        assertEquals("Не удалось распознать еду на фото. Повторите текущий шаг.", vm.uiState.value.error)
        assertTrue(vm.uiState.value.manualRetryAvailable)
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

    // ---- Several dishes on one photo ----

    private fun pasta() = EstimatedIngredient(
        name = "Паста",
        rawGrams = 180f,
        cookedGrams = 180f,
        caloriesPer100g = 160f,
        proteinPer100g = 6f,
        fatPer100g = 5f,
        carbsPer100g = 24f
    )

    private fun cucumber() = EstimatedIngredient(
        name = "Огурец",
        rawGrams = 100f,
        cookedGrams = 100f,
        caloriesPer100g = 15f,
        proteinPer100g = 0.8f,
        fatPer100g = 0.1f,
        carbsPer100g = 2.8f
    )

    private fun recognizedDishes() = listOf(
        RecognizedDish(
            dishName = "Паста карбонара",
            ingredients = listOf(RecognizedIngredient("паста"), RecognizedIngredient("бекон")),
            confidence = 0.86f
        ),
        RecognizedDish(
            dishName = "Овощной салат",
            ingredients = listOf(RecognizedIngredient("огурец")),
            confidence = 0.81f
        )
    )

    private fun seedMultiDishNutrition() {
        ai.dishesResult = Result.success(recognizedDishes())
        ai.nutritionByDish["Паста карбонара"] = Result.success(listOf(pasta()))
        ai.nutritionByDish["Овощной салат"] = Result.success(listOf(cucumber()))
    }

    @Test
    fun `several dishes open the dish list before any estimation`() = runTest {
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISHES, vm.uiState.value.stage)
        assertEquals(2, vm.uiState.value.dishes.size)
        assertTrue(vm.uiState.value.hasMultipleDishes)
        assertEquals(0, ai.estimateCalls)
    }

    @Test
    fun `a single dish skips the list step`() = runTest {
        ai.dishResult = Result.success(dish())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertFalse(vm.uiState.value.hasMultipleDishes)
    }

    @Test
    fun `every dish is reviewed and estimated on its own`() = runTest {
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.confirmDishList()
        assertEquals(ScannerStage.REVIEW_DISH, vm.uiState.value.stage)
        assertEquals("Паста карбонара", vm.uiState.value.dish?.dishName)

        // First "next" only moves to the second dish - nothing is estimated yet.
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.currentDishIndex)
        assertEquals(0, ai.estimateCalls)

        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertEquals(2, ai.estimateCalls)
        assertEquals(listOf("Паста карбонара", "Овощной салат"), ai.estimatedDishNames)
        assertEquals(listOf("Паста", "Огурец"), vm.uiState.value.estimated.map { it.name })
    }

    @Test
    fun `each dish becomes its own diary entry inside the same meal`() = runTest {
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmDishList()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.proceedToFinal()
        advanceUntilIdle()
        vm.saveMeal()
        advanceUntilIdle()

        assertEquals(2, meals.addCalls.size)
        assertEquals(listOf("Паста карбонара", "Овощной салат"), meals.addedDishNames)
        assertEquals(2, meals.meals.value.size)
        assertTrue(meals.meals.value.all { it.mealType == vm.uiState.value.mealType })
        assertTrue(meals.addCalls.all { it.first == targetDay })
    }

    @Test
    fun `a deleted dish never reaches the diary`() = runTest {
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.removeDish(1)
        vm.confirmDishList()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.proceedToFinal()
        advanceUntilIdle()
        vm.saveMeal()
        advanceUntilIdle()

        assertEquals(1, ai.estimateCalls)
        assertEquals(listOf("Паста карбонара"), meals.addedDishNames)
    }

    @Test
    fun `a manually added dish is kept and can be renamed`() = runTest {
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()

        vm.addDish("Компот")
        vm.renameDish(0, "Карбонара")
        vm.markDishUnknown(1)

        val dishes = vm.uiState.value.dishes
        assertEquals(3, dishes.size)
        assertEquals("Карбонара", dishes[0].name)
        assertTrue(dishes[1].isUnknown)
        assertEquals("Компот", dishes[2].name)
    }

    @Test
    fun `grams edits address the right dish`() = runTest {
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmDishList()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        // Flat index 1 is the first product of the second dish.
        vm.setGrams(1, 250f)

        assertEquals(180f, vm.uiState.value.dishes[0].estimated.single().cookedGrams, 0.01f)
        assertEquals(250f, vm.uiState.value.dishes[1].estimated.single().cookedGrams, 0.01f)
    }

    @Test
    fun `a product without weight blocks saving`() = runTest {
        ai.dishResult = Result.success(dish())
        ai.nutritionResult = Result.success(listOf(pasta().copy(rawGrams = 0f, cookedGrams = 0f)))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canSave)
        vm.setGrams(0, 120f)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `AI-only mode never produces a local draft or catalogue match`() = runTest {
        seedProducts()
        dishes.catalogue.value = listOf(
            Dish(10, "Паста карбонара", listOf("carbonara"), emptyList(), 280f, 210f, 11f, 12f, 22f)
        )
        seedMultiDishNutrition()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPhotoTaken(photo())
        advanceUntilIdle()
        vm.confirmDishList()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()
        vm.confirmIngredientsAndEstimate()
        advanceUntilIdle()

        assertEquals(ScannerStage.REVIEW_GRAMS, vm.uiState.value.stage)
        assertFalse(vm.uiState.value.isLocalDraft)
        assertEquals(null, vm.uiState.value.localDish)
        assertTrue(vm.uiState.value.unmatchedIngredients.isEmpty())
    }
}
