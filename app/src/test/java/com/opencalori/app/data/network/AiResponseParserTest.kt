package com.opencalori.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class AiResponseParserTest {

    @Test
    fun `balanced JSON after BOM prose and markdown fence is parsed`() {
        val dishes = AiResponseParser.parseDishes(
            "\uFEFFОтвет модели:\n```json\n" + validDishesJson() + "\n```"
        )

        assertEquals(listOf("Паста карбонара", "Овощной салат"), dishes.map { it.dishName })
        assertEquals(listOf("паста", "бекон"), dishes.first().ingredients.map { it.name })
        assertTrue(dishes.map { it.id }.distinct().size == dishes.size)
    }

    @Test
    fun `braces and escaped quotes inside notes do not break balanced JSON extraction`() {
        val items = AiResponseParser.parseNutrition(
            "Преамбула " + nutritionJson("рис", "варёный {без \\\"соуса\\\"}") + " хвост",
            confirmedNames = listOf("рис"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )

        assertEquals("варёный {без \"соуса\"}", items.single().notes)
    }

    @Test
    fun `truncated content is classified separately`() {
        assertError(AiPipelineError.TruncatedResponse) {
            AiResponseParser.parseNutrition("[{\"name\":\"рис\",\"rawGrams\":20")
        }
    }

    @Test
    fun `missing JSON is classified separately`() {
        assertError(AiPipelineError.JsonNotFound) {
            AiResponseParser.parseDishes("Не могу распознать это изображение.")
        }
    }

    @Test
    fun `unknown fields and legacy aliases are rejected as wrong schema`() {
        assertError(AiPipelineError.WrongSchema) {
            AiResponseParser.parseDishes(
                """{"dishes":[{"name":"Суп","confidence":0.8,"ingredients":[],"extra":true}]}"""
            )
        }
    }

    @Test
    fun `string number is not silently coerced`() {
        assertError(AiPipelineError.InvalidNumber) {
            AiResponseParser.parseNutrition(
                """[{"name":"рис","rawGrams":"120 г","cookedGrams":0,"calories":130,"protein":2,"fat":1,"carbs":28,"notes":"варёный"}]""",
                confirmedNames = listOf("рис"),
                weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
            )
        }
    }

    @Test
    fun `duplicate confirmed product is rejected instead of dropped`() {
        assertError(AiPipelineError.DuplicateItem) {
            AiResponseParser.parseNutrition(
                """[{"name":"рис","rawGrams":0,"cookedGrams":0,"calories":130,"protein":2,"fat":1,"carbs":28,"notes":"варёный"},{"name":"Рис","rawGrams":0,"cookedGrams":0,"calories":130,"protein":2,"fat":1,"carbs":28,"notes":"варёный"}]""",
                confirmedNames = listOf("рис", "Рис"),
                weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
            )
        }
    }

    @Test
    fun `changed confirmed product name is rejected`() {
        assertError(AiPipelineError.RenamedConfirmedItem) {
            AiResponseParser.parseNutrition(
                nutritionJson("курица"),
                confirmedNames = listOf("куриное филе"),
                weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
            )
        }
    }

    @Test
    fun `partial nutrition response is rejected by exact item count`() {
        assertError(AiPipelineError.WrongItemCount) {
            AiResponseParser.parseNutrition(
                nutritionJson("рис"),
                confirmedNames = listOf("рис", "куриное филе"),
                weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
            )
        }
    }

    @Test
    fun `text nutrition rejects AI-invented weights`() {
        assertError(AiPipelineError.InvalidRange) {
            AiResponseParser.parseNutrition(
                nutritionJson("рис", rawGrams = 50.0, cookedGrams = 0.0),
                confirmedNames = listOf("рис"),
                weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
            )
        }
    }

    @Test
    fun `recognition rejects visible grams because user confirms all weights`() {
        assertError(AiPipelineError.InvalidRange) {
            AiResponseParser.parseDishes(
                """{"dishes":[{"name":"Каша","confidence":0.7,"ingredients":[{"name":"овсянка","confidence":0.8,"visibleQuantity":120}]}]}"""
            )
        }
    }

    @Test
    fun `valid zero-weight text nutrition keeps stable generated ids`() {
        val raw = nutritionJson("салат цезарь", rawGrams = 0.0, cookedGrams = 0.0)
        val first = AiResponseParser.parseNutrition(
            raw,
            confirmedNames = listOf("салат цезарь"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
        ).single()
        val second = AiResponseParser.parseNutrition(
            raw,
            confirmedNames = listOf("салат цезарь"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
        ).single()

        assertEquals(0f, first.effectiveGrams)
        assertEquals(first.id, second.id)
    }

    @Test
    fun `json wrapped in think block of a reasoning model is parsed`() {
        val dishes = AiResponseParser.parseDishes(
            "<think>Смотрю на фото: там паста и салат. Отвечу строго JSON без лишнего текста.</think>\n" + validDishesJson()
        )
        assertEquals(listOf("Паста карбонара", "Овощной салат"), dishes.map { it.dishName })
    }

    @Test
    fun `json after reasoning prose without think tags is parsed`() {
        val dishes = AiResponseParser.parseDishes(
            "Разбираю изображение... вижу два блюда... итоговый ответ:\n" + validDishesJson() + "\nГотово."
        )
        assertEquals(2, dishes.size)
    }

    private fun assertError(expected: AiPipelineError, block: () -> Unit) {
        val error = assertThrows(AiPipelineException::class.java, block)
        assertEquals(expected, error.pipelineError)
    }

    private fun validDishesJson(): String = """
        {"dishes":[
          {"name":"Паста карбонара","confidence":0.86,"ingredients":[
            {"name":"паста","confidence":0.91,"visibleQuantity":null},
            {"name":"бекон","confidence":0.78,"visibleQuantity":null}
          ]},
          {"name":"Овощной салат","confidence":0.81,"ingredients":[
            {"name":"огурец","confidence":0.93,"visibleQuantity":null}
          ]}
        ]}
    """.trimIndent()

    private fun nutritionJson(
        name: String,
        notes: String = "варёный",
        rawGrams: Double = 0.0,
        cookedGrams: Double = 0.0
    ): String = """[{"name":"$name","rawGrams":$rawGrams,"cookedGrams":$cookedGrams,"calories":130,"protein":2,"fat":1,"carbs":28,"notes":"$notes"}]"""
}
