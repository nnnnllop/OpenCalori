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
    fun `extra unknown fields from chatty models are ignored`() {
        val dishes = AiResponseParser.parseDishes(
            """{"dishes":[{"name":"Суп","confidence":0.8,"ingredients":[],"extra":true}],"meta":"ok"}"""
        )
        assertEquals(listOf("Суп"), dishes.map { it.dishName })
    }

    @Test
    fun `numeric strings and percent confidence are coerced`() {
        val items = AiResponseParser.parseNutrition(
            """[{"name":"рис","rawGrams":"150","cookedGrams":"0 г","calories":"130 ккал","protein":2,"fat":1,"carbs":28,"notes":"варёный","portion":"1 шт"}]""",
            confirmedNames = listOf("рис"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )
        assertEquals(150f, items.single().rawGrams)
        assertEquals(130f, items.single().caloriesPer100g)
    }

    @Test
    fun `percent confidence is normalised to fraction`() {
        val dishes = AiResponseParser.parseDishes(
            """{"dishes":[{"name":"Каша","confidence":86,"ingredients":[{"name":"овсянка","confidence":"70%","visibleQuantity":"примерно 40 г"}]}]}"""
        )
        assertEquals(0.86f, dishes.single().confidence)
        assertEquals(0.7f, dishes.single().ingredients.single().confidence)
        assertEquals(null, dishes.single().ingredients.single().visibleQuantityGrams)
    }

    @Test
    fun `missing notes does not block an otherwise valid nutrition answer`() {
        val items = AiResponseParser.parseNutrition(
            """[{"name":"рис","rawGrams":0,"cookedGrams":0,"calories":130,"protein":2,"fat":1,"carbs":28}]""",
            confirmedNames = listOf("рис"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
        )
        assertEquals("", items.single().notes)
    }

    @Test
    fun `string number is rejected when it contains no digits`() {
        assertError(AiPipelineError.InvalidNumber) {
            AiResponseParser.parseNutrition(
                """[{"name":"рис","rawGrams":"много","cookedGrams":0,"calories":130,"protein":2,"fat":1,"carbs":28,"notes":"варёный"}]""",
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
    fun `json_object wrapper object is unwrapped to the nutrition array`() {
        val items = AiResponseParser.parseNutrition(
            """{"ingredients":[${nutritionJsonBody("рис")},${nutritionJsonBody("куриное филе")}]}""",
            confirmedNames = listOf("рис", "куриное филе"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )
        assertEquals(listOf("рис", "куриное филе"), items.map { it.name })
    }

    @Test
    fun `improvised wrapper key is unwrapped too`() {
        val items = AiResponseParser.parseNutrition(
            """{"items":[${nutritionJsonBody("рис")}],"meta":"ok"}""",
            confirmedNames = listOf("рис"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
        )
        assertEquals(listOf("рис"), items.map { it.name })
    }

    @Test
    fun `case differences are not renames and confirmed spelling wins`() {
        // модель вернула «Курица», подтверждено «курица»
        val raw = """{"ingredients":[{"name":"Курица","rawGrams":0,"cookedGrams":0,"calories":165,"protein":31,"fat":3.6,"carbs":0,"notes":""}]}"""
        val items = AiResponseParser.parseNutrition(
            raw,
            confirmedNames = listOf("курица"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.USER_INPUT_ONLY
        )
        assertEquals("курица", items.single().name)
    }

    @Test
    fun `shuffled answer is reordered to the confirmed list`() {
        val raw = """{"ingredients":[${nutritionJsonBody("куриное филе")},${nutritionJsonBody("рис")}]}"""
        val items = AiResponseParser.parseNutrition(
            raw,
            confirmedNames = listOf("рис", "куриное филе"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )
        assertEquals(listOf("рис", "куриное филе"), items.map { it.name })
    }

    @Test
    fun `unknown extra positions are dropped silently`() {
        val raw = """{"ingredients":[${nutritionJsonBody("рис")},${nutritionJsonBody("масло оливковое")}]}"""
        val items = AiResponseParser.parseNutrition(
            raw,
            confirmedNames = listOf("рис"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )
        assertEquals(listOf("рис"), items.map { it.name })
    }

    @Test
    fun `bare array dishes response is accepted`() {
        val dishes = AiResponseParser.parseDishes(validDishesArrayJson())
        assertEquals(listOf("Паста карбонара", "Овощной салат"), dishes.map { it.dishName })
    }

    @Test
    fun `real rename is still rejected as wrong item count`() {
        assertError(AiPipelineError.WrongItemCount) {
            AiResponseParser.parseNutrition(
                """{"ingredients":[${nutritionJsonBody("курица")}]}""",
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
    fun `visible grams from the model are ignored because the user confirms all weights`() {
        val dishes = AiResponseParser.parseDishes(
            """{"dishes":[{"name":"Каша","confidence":0.7,"ingredients":[{"name":"овсянка","confidence":0.8,"visibleQuantity":120}]}]}"""
        )
        assertEquals(null, dishes.single().ingredients.single().visibleQuantityGrams)
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

    @Test
    fun `partial answer reports missing positions instead of failing`() {
        val partial = AiResponseParser.parseNutritionAllowPartial(
            """{"ingredients":[${nutritionJsonBody("рис")}]}""",
            confirmedNames = listOf("рис", "куриное филе", "соус"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )
        assertEquals(listOf("рис"), partial.items.map { it.name })
        assertEquals(listOf("куриное филе", "соус"), partial.missing)
    }

    @Test
    fun `complete answer has empty missing list`() {
        val partial = AiResponseParser.parseNutritionAllowPartial(
            """{"ingredients":[${nutritionJsonBody("рис")},${nutritionJsonBody("куриное филе")}]}""",
            confirmedNames = listOf("рис", "куриное филе"),
            weightPolicy = AiResponseParser.NutritionWeightPolicy.PHOTO_ESTIMATE
        )
        assertTrue(partial.missing.isEmpty())
        assertEquals(2, partial.items.size)
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

    private fun nutritionJsonBody(
        name: String,
        notes: String = "варёный",
        rawGrams: Double = 0.0,
        cookedGrams: Double = 0.0
    ): String = """{"name":"$name","rawGrams":$rawGrams,"cookedGrams":$cookedGrams,"calories":130,"protein":2,"fat":1,"carbs":28,"notes":"$notes"}"""

    private fun validDishesArrayJson(): String = """
        [
          {"name":"Паста карбонара","confidence":0.86,"ingredients":[
            {"name":"паста","confidence":0.91,"visibleQuantity":null},
            {"name":"бекон","confidence":0.78,"visibleQuantity":null}
          ]},
          {"name":"Овощной салат","confidence":0.81,"ingredients":[
            {"name":"огурец","confidence":0.93,"visibleQuantity":null}
          ]}
        ]
    """.trimIndent()
}
