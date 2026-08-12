package com.opencalori.app.data.network

import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.RecognizedIngredient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * Turns whatever an LLM decided to answer into domain objects.
 *
 * Deliberately forgiving: models wrap JSON in markdown fences, add a sentence before it,
 * return numbers as strings ("200 г"), or nest the array under a key. All of that is a
 * normal Tuesday, and none of it should surface as an error to the user. Pure Kotlin so
 * every one of those cases is covered by unit tests.
 */
object AiResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class MalformedResponse(message: String) : IllegalArgumentException(message)

    fun parseDish(raw: String): RecognizedDish {
        val obj = json.parseToJsonElement(extractJsonObject(raw)) as? JsonObject
            ?: throw MalformedResponse("Ответ не является JSON-объектом")

        val name = (obj["dish"] ?: obj["name"] ?: obj["dishName"]).asText().orEmpty()
        val rawItems = (obj["ingredients"] ?: obj["items"] ?: obj["products"]) as? JsonArray
            ?: JsonArray(emptyList())

        val ingredients = rawItems.mapNotNull { element ->
            val text = when (element) {
                is JsonObject -> (element["name"] ?: element["title"]).asText()
                else -> element.asText()
            }
            text?.trim()?.takeIf { it.isNotBlank() }?.let { RecognizedIngredient(it) }
        }

        return RecognizedDish(
            dishName = name.trim().ifBlank { "Блюдо" },
            ingredients = ingredients
        )
    }

    fun parseNutrition(raw: String): List<EstimatedIngredient> {
        val array = json.parseToJsonElement(extractJsonArray(raw)) as? JsonArray
            ?: throw MalformedResponse("Ответ не является JSON-массивом")

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] ?: obj["title"]).asText()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            val cooked = obj["cookedGrams"].asFloat(0f)
            val rawGrams = obj["rawGrams"].asFloat(0f)
            EstimatedIngredient(
                name = name,
                rawGrams = if (rawGrams > 0f) rawGrams else cooked,
                cookedGrams = if (cooked > 0f) cooked else rawGrams,
                caloriesPer100g = obj["calories"].asFloat(0f).coerceIn(0f, 900f),
                proteinPer100g = obj["protein"].asFloat(0f).coerceIn(0f, 100f),
                fatPer100g = obj["fat"].asFloat(0f).coerceIn(0f, 100f),
                carbsPer100g = obj["carbs"].asFloat(0f).coerceIn(0f, 100f),
                notes = obj["notes"].asText()?.trim().orEmpty()
            )
        }
    }

    // ---- Extraction helpers ----

    fun extractJsonObject(text: String): String = extract(text, '{', '}', "объект")

    fun extractJsonArray(text: String): String {
        val stripped = stripFences(text)
        val start = stripped.indexOf('[')
        val end = stripped.lastIndexOf(']')
        if (start >= 0 && end > start) return stripped.substring(start, end + 1)

        // Some models answer {"items": [...]}: dig the first array out of the object.
        val objStart = stripped.indexOf('{')
        val objEnd = stripped.lastIndexOf('}')
        if (objStart >= 0 && objEnd > objStart) {
            val obj = runCatching {
                json.parseToJsonElement(stripped.substring(objStart, objEnd + 1)) as? JsonObject
            }.getOrNull()
            obj?.values?.firstNotNullOfOrNull { it as? JsonArray }?.let { return it.toString() }
        }
        throw MalformedResponse("В ответе модели нет JSON-массива")
    }

    private fun extract(text: String, open: Char, close: Char, what: String): String {
        val stripped = stripFences(text)
        val start = stripped.indexOf(open)
        val end = stripped.lastIndexOf(close)
        if (start < 0 || end <= start) throw MalformedResponse("В ответе модели нет JSON-" + what)
        return stripped.substring(start, end + 1)
    }

    private fun stripFences(text: String): String {
        var s = text.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```kotlin")
                .removePrefix("```")
                .trim()
            if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        }
        return s
    }

    private fun JsonElement?.asText(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.contentOrNull
    }

    private fun JsonElement?.asFloat(default: Float): Float {
        val primitive = this as? JsonPrimitive ?: return default
        primitive.floatOrNull?.let { return it }
        val content = primitive.contentOrNull ?: return default
        val match = Regex("-?\\d+(?:\\.\\d+)?").find(content.replace(',', '.')) ?: return default
        return match.value.toFloatOrNull() ?: default
    }
}
