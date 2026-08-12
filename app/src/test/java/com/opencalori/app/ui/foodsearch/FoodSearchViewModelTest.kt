package com.opencalori.app.ui.foodsearch

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.opencalori.app.domain.model.FoodItem
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.Product
import com.opencalori.app.domain.model.ProductSource
import com.opencalori.app.testing.FakeMealRepository
import com.opencalori.app.testing.FakeProductRepository
import com.opencalori.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val targetDay = 20_311L

    private val products = FakeProductRepository()
    private val meals = FakeMealRepository()

    private fun viewModel() = FoodSearchViewModel(
        productRepository = products,
        mealRepository = meals,
        savedStateHandle = SavedStateHandle(mapOf("date" to targetDay))
    )

    private fun product(name: String, kcal: Float) = Product(
        id = name.hashCode().toLong(),
        name = name,
        caloriesPer100g = kcal,
        proteinPer100g = 10f,
        fatPer100g = 2f,
        carbsPer100g = 20f
    )

    @Test
    fun `search results arrive after the debounce`() = runTest {
        products.catalogue.value = listOf(product("Гречка варёная", 130f), product("Рис белый", 116f))
        val vm = viewModel()
        advanceUntilIdle()

        vm.results.test {
            assertTrue(awaitItem().isEmpty())
            vm.setQuery("греч")
            advanceTimeBy(300)
            assertEquals(listOf("Гречка варёная"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing fast does not fire a query per keystroke`() = runTest {
        products.catalogue.value = listOf(product("Творог 5%", 121f))
        val vm = viewModel()
        advanceUntilIdle()

        vm.results.test {
            awaitItem()
            vm.setQuery("т")
            advanceTimeBy(50)
            vm.setQuery("тв")
            advanceTimeBy(50)
            vm.setQuery("твор")
            advanceTimeBy(300)

            assertEquals(1, awaitItem().size)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the query is mirrored into ui state for the text field`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setQuery("банан")

        assertEquals("банан", vm.uiState.value.query)
    }

    @Test
    fun `recently eaten food is offered before anything is typed`() = runTest {
        meals.seedMeal(
            targetDay - 1,
            MealType.BREAKFAST,
            FoodItem(name = "Овсянка", grams = 250f, caloriesPer100g = 88f, proteinPer100g = 3f, fatPer100g = 1.7f, carbsPer100g = 15f)
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Овсянка"), vm.uiState.value.recents.map { it.name })
        assertEquals(ProductSource.RECENT, vm.uiState.value.recents.first().source)
    }

    @Test
    fun `adding a product writes to the day the diary was showing`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addProductToMeal(product("Яблоко", 52f), grams = 150f, mealType = MealType.SNACK)
        advanceUntilIdle()

        val (day, type, items) = meals.addCalls.single()
        assertEquals(targetDay, day)
        assertEquals(MealType.SNACK, type)
        assertEquals(150f, items.single().grams, 0.01f)
        assertEquals(78f, items.single().calories, 0.01f)
    }

    @Test
    fun `saving closes the screen exactly once`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addProductToMeal(product("Яблоко", 52f), 100f, MealType.SNACK)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)

        vm.resetSaved()
        assertTrue(!vm.uiState.value.saved)
    }

    @Test
    fun `zero grams is not logged`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addProductToMeal(product("Яблоко", 52f), 0f, MealType.SNACK)
        advanceUntilIdle()

        assertTrue(meals.addCalls.isEmpty())
    }

    @Test
    fun `a custom product is stored and logged in one step`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addCustomProduct(
            product("Мой батончик", 350f).copy(source = ProductSource.CUSTOM),
            grams = 60f,
            mealType = MealType.SNACK
        )
        advanceUntilIdle()

        assertEquals(1, products.custom.value.size)
        assertEquals(1, meals.addCalls.size)
        assertEquals(210f, meals.addCalls.single().third.single().calories, 0.01f)
    }

    @Test
    fun `two products from different sources can coexist in one list`() = runTest {
        // Ids collide across the bundled table and the user's own products, so the list
        // key has to include the source or Compose crashes on duplicate keys.
        val builtIn = product("Хлеб", 250f).copy(id = 1, source = ProductSource.BUILT_IN)
        val custom = product("Хлеб", 260f).copy(id = 1, source = ProductSource.CUSTOM)

        assertTrue(builtIn.key != custom.key)
    }
}
