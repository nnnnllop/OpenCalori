package com.opencalori.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model is the least reliable component in the app, so every shape it has been seen
 * to emit gets a test here.
 */
class AiResponseParserTest {

    private val delta = 0.001f

    // ---- Dish recognition ----

    @Test
    fun `plain json object is parsed`() {
        val dish = AiResponseParser.parseDish(
            "{\"dish\":\"Плов\",\"ingredients\":[\"Рис\",\"Морковь\",\"Баранина\"]}"
        )
        assertEquals("Плов", dish.dishName)
        assertEquals(listOf("Рис", "Морковь", "Баранина"), dish.ingredients.map { it.name })
    }

    @Test
    fun `markdown fences are stripped`() {
        val dish = AiResponseParser.parseDish(
            "```json\n{\"dish\":\"Салат\",\"ingredients\":[\"Огурец\"]}\n```"
        )
        assertEquals("Салат", dish.dishName)
        assertEquals(1, dish.ingredients.size)
    }

    @Test
    fun `chatter around the json is ignored`() {
        val dish = AiResponseParser.parseDish(
            "Конечно! Вот результат:\n{\"dish\":\"Омлет\",\"ingredients\":[\"Яйцо\"]}\nНадеюсь, помог."
        )
        assertEquals("Омлет", dish.dishName)
    }

    @Test
    fun `ingredients given as objects are flattened`() {
        val dish = AiResponseParser.parseDish(
            "{\"dish\":\"Паста\",\"ingredients\":[{\"name\":\"Спагетти\"},{\"name\":\"Соус\"}]}"
        )
        assertEquals(listOf("Спагетти", "Соус"), dish.ingredients.map { it.name })
    }

    @Test
    fun `alternative key names are accepted`() {
        val dish = AiResponseParser.parseDish(
            "{\"name\":\"Суп\",\"items\":[\"Картофель\"]}"
        )
        assertEquals("Суп", dish.dishName)
        assertEquals(listOf("Картофель"), dish.ingredients.map { it.name })
    }

    @Test
    fun `blank dish name falls back to a placeholder`() {
        val dish = AiResponseParser.parseDish("{\"dish\":\"\",\"ingredients\":[\"Хлеб\"]}")
        assertEquals("Блюдо", dish.dishName)
    }

    @Test
    fun `blank ingredient entries are dropped`() {
        val dish = AiResponseParser.parseDish(
            "{\"dish\":\"Х\",\"ingredients\":[\"Рис\",\"\",\"   \"]}"
        )
        assertEquals(1, dish.ingredients.size)
    }

    @Test
    fun `empty food list is reported as no ingredients`() {
        val dish = AiResponseParser.parseDish("{\"dish\":\"\",\"ingredients\":[]}")
        assertTrue(dish.ingredients.isEmpty())
    }

    @Test
    fun `ingredients get distinct ids so list edits do not collide`() {
        val dish = AiResponseParser.parseDish(
            "{\"dish\":\"Х\",\"ingredients\":[\"Рис\",\"Курица\"]}"
        )
        assertEquals(2, dish.ingredients.map { it.id }.distinct().size)
    }

    @Test
    fun `duplicated ingredients are collapsed`() {
        val dish = AiResponseParser.parseDish(
            "{\"dish\":\"Х\",\"ingredients\":[\"Рис\",\"рис\",\"Курица\"]}"
        )
        assertEquals(listOf("Рис", "Курица"), dish.ingredients.map { it.name })
    }

    // ---- Multi-dish recognition ----

    @Test
    fun `several dishes stay separate with their own ingredients`() {
        val dishes = AiResponseParser.parseDishes(
            """
            {"dishes":[
              {"name":"Паста карбонара","confidence":0.86,"ingredients":[
                {"name":"паста","confidence":0.91,"visibleQuantity":null},
                {"name":"бекон","confidence":0.78,"visibleQuantity":null}
              ]},
              {"name":"Овощной салат","confidence":0.81,"ingredients":[
                {"name":"огурец","confidence":0.93,"visibleQuantity":null},
                {"name":"помидор","confidence":0.9,"visibleQuantity":null}
              ]}
            ]}
            """.trimIndent()
        )
        assertEquals(listOf("Паста карбонара", "Овощной салат"), dishes.map { it.dishName })
        assertEquals(listOf("паста", "бекон"), dishes[0].ingredients.map { it.name })
        assertEquals(listOf("огурец", "помидор"), dishes[1].ingredients.map { it.name })
        assertEquals(0.86f, dishes[0].confidence!!, delta)
        assertEquals(0.91f, dishes[0].ingredients.first().confidence!!, delta)
        assertEquals(2, dishes.map { it.id }.distinct().size)
    }

    @Test
    fun `legacy single dish object becomes a one element list`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dish\":\"Плов\",\"ingredients\":[\"Рис\",\"Морковь\"]}"
        )
        assertEquals(1, dishes.size)
        assertEquals("Плов", dishes.single().dishName)
        assertEquals(2, dishes.single().ingredients.size)
    }

    @Test
    fun `bare array of dishes is accepted`() {
        val dishes = AiResponseParser.parseDishes(
            "[{\"name\":\"Суп\",\"ingredients\":[\"Картошка\"]}," +
                "{\"name\":\"Хлеб\",\"ingredients\":[\"Хлеб\"]}]"
        )
        assertEquals(listOf("Суп", "Хлеб"), dishes.map { it.dishName })
    }

    @Test
    fun `markdown fences and chatter around a dish array are ignored`() {
        val dishes = AiResponseParser.parseDishes(
            "Конечно! \n```json\n{\"dishes\":[{\"name\":\"Омлет\",\"ingredients\":[\"Яйцо\"]}]}\n```\nГотово."
        )
        assertEquals("Омлет", dishes.single().dishName)
    }

    @Test
    fun `empty and ingredientless dishes are dropped`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dishes\":[{\"name\":\"\",\"ingredients\":[]}," +
                "{\"name\":\"Салат\",\"ingredients\":[\"Огурец\",\"\",\"   \"]}]}"
        )
        assertEquals(1, dishes.size)
        assertEquals(listOf("Огурец"), dishes.single().ingredients.map { it.name })
    }

    @Test
    fun `no food at all yields an empty dish list instead of an error`() {
        assertTrue(AiResponseParser.parseDishes("{\"dishes\":[]}").isEmpty())
    }

    @Test
    fun `nameless dish with ingredients is labelled as unknown`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dishes\":[{\"name\":\"\",\"ingredients\":[\"Каша\"]}]}"
        )
        assertEquals("Неизвестное блюдо", dishes.single().dishName)
        assertTrue(dishes.single().isUnknown)
    }

    @Test
    fun `dishes given as plain strings still parse`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dishes\":[\"Паста\",\"Салат\"]}"
        )
        assertEquals(listOf("Паста", "Салат"), dishes.map { it.dishName })
        assertTrue(dishes.all { it.ingredients.isEmpty() })
    }

    @Test
    fun `confidence given as a percentage is normalised`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dishes\":[{\"name\":\"Суп\",\"confidence\":86,\"ingredients\":[\"Вода\"]}]}"
        )
        assertEquals(0.86f, dishes.single().confidence!!, delta)
    }

    @Test
    fun `low confidence is flagged for the ui`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dishes\":[{\"name\":\"Соус\",\"confidence\":0.3,\"ingredients\":[" +
                "{\"name\":\"сметана\",\"confidence\":0.2}]}]}"
        )
        assertTrue(dishes.single().isLowConfidence)
        assertTrue(dishes.single().ingredients.single().isLowConfidence)
    }

    @Test
    fun `visible quantity is kept only when the model reports one`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"dishes\":[{\"name\":\"Завтрак\",\"ingredients\":[" +
                "{\"name\":\"йогурт\",\"visibleQuantity\":125}," +
                "{\"name\":\"банан\",\"visibleQuantity\":null}]}]}"
        )
        val ingredients = dishes.single().ingredients
        assertEquals(125f, ingredients[0].visibleQuantityGrams!!, delta)
        assertEquals(null, ingredients[1].visibleQuantityGrams)
    }

    @Test
    fun `unknown extra keys around dishes do not break parsing`() {
        val dishes = AiResponseParser.parseDishes(
            "{\"model\":\"gpt\",\"dishes\":[{\"name\":\"Каша\",\"portion\":\"большая\"," +
                "\"ingredients\":[{\"name\":\"Овсянка\",\"source\":\"USDA\"}]}],\"note\":\"ok\"}"
        )
        assertEquals("Каша", dishes.single().dishName)
    }

    @Test
    fun `garbage multi-dish response is rejected loudly`() {
        assertThrows(AiResponseParser.MalformedResponse::class.java) {
            AiResponseParser.parseDishes("Не могу определить блюда на этом фото.")
        }
    }

    @Test
    fun `garbage response is rejected loudly`() {
        assertThrows(AiResponseParser.MalformedResponse::class.java) {
            AiResponseParser.parseDish("Извините, я не могу определить это блюдо.")
        }
    }

    // ---- Nutrition estimation ----

    @Test
    fun `plain json array is parsed`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"Гречка\",\"rawGrams\":80,\"cookedGrams\":200,\"calories\":130," +
                "\"protein\":4.5,\"fat\":1.2,\"carbs\":25,\"notes\":\"варёный\"}]"
        )
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Гречка", item.name)
        assertEquals(80f, item.rawGrams, delta)
        assertEquals(200f, item.cookedGrams, delta)
        assertEquals(130f, item.caloriesPer100g, delta)
        assertEquals("варёный", item.notes)
    }

    @Test
    fun `numbers sent as strings with units are recovered`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"Рис\",\"cookedGrams\":\"150 г\",\"calories\":\"116 ккал\"," +
                "\"protein\":\"2,2\",\"fat\":\"0.3\",\"carbs\":\"25\"}]"
        )
        val item = items.first()
        assertEquals(150f, item.cookedGrams, delta)
        assertEquals(116f, item.caloriesPer100g, delta)
        assertEquals(2.2f, item.proteinPer100g, delta)
    }

    @Test
    fun `array nested under a key is found`() {
        val items = AiResponseParser.parseNutrition(
            "{\"items\":[{\"name\":\"Яйцо\",\"cookedGrams\":50,\"calories\":155," +
                "\"protein\":12,\"fat\":10,\"carbs\":1}]}"
        )
        assertEquals("Яйцо", items.single().name)
    }

    @Test
    fun `missing raw weight mirrors the cooked weight`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"Салат\",\"cookedGrams\":120,\"calories\":45,\"protein\":1,\"fat\":3,\"carbs\":4}]"
        )
        assertEquals(120f, items.single().rawGrams, delta)
    }

    @Test
    fun `missing cooked weight falls back to the raw weight`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"Овсянка\",\"rawGrams\":60,\"calories\":88,\"protein\":3,\"fat\":1.7,\"carbs\":15}]"
        )
        assertEquals(60f, items.single().cookedGrams, delta)
    }

    @Test
    fun `implausible macros are clamped instead of poisoning the diary`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"Ошибка\",\"cookedGrams\":100,\"calories\":99999,\"protein\":500," +
                "\"fat\":-3,\"carbs\":10}]"
        )
        val item = items.single()
        assertEquals(900f, item.caloriesPer100g, delta)
        assertEquals(100f, item.proteinPer100g, delta)
        assertEquals(0f, item.fatPer100g, delta)
    }

    @Test
    fun `entries without a name are skipped`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"\",\"cookedGrams\":100,\"calories\":50,\"protein\":1,\"fat\":1,\"carbs\":1}," +
                "{\"name\":\"Хлеб\",\"cookedGrams\":30,\"calories\":250,\"protein\":8,\"fat\":1,\"carbs\":48}]"
        )
        assertEquals(listOf("Хлеб"), items.map { it.name })
    }

    @Test
    fun `unknown extra fields do not break parsing`() {
        val items = AiResponseParser.parseNutrition(
            "[{\"name\":\"Суп\",\"cookedGrams\":250,\"calories\":45,\"protein\":2,\"fat\":2," +
                "\"carbs\":4,\"confidence\":0.8,\"source\":\"USDA\"}]"
        )
        assertEquals(1, items.size)
    }

    @Test
    fun `truncated json is rejected rather than silently halved`() {
        assertThrows(Exception::class.java) {
            AiResponseParser.parseNutrition("[{\"name\":\"Гречка\",\"cookedGrams\":200,\"calories\":13")
        }
    }

    @Test
    fun `response with no array at all is rejected`() {
        assertThrows(AiResponseParser.MalformedResponse::class.java) {
            AiResponseParser.parseNutrition("Не могу оценить.")
        }
    }
}
