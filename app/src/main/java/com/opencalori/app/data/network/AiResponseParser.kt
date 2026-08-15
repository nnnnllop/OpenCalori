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
 * return numbers as strings ("200 \u0433"), nest the array under a key, or answer with the old
 * single-dish object instead of the multi-dish contract. All of that is a normal Tuesday, and
 * none of it should surface as an error to the user. Pure Kotlin so every one of those cases
 * is covered by unit tests.
 */
object AiResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val DISH_LIST_KEYS = listOf("dishes", "blyuda", "meals", "plates", "foods", "results")
    private val DISH_NAME_KEYS = listOf("name", "dish", "dishName", "title", "label")
    private val INGREDIENT_LIST_KEYS = listOf("ingredients", "items", "products", "components")
    private val INGREDIENT_NAME_KEYS = listOf("name", "title", "product", "ingredient")

    class MalformedResponse(message: String) : IllegalArgumentException(message)

    /**
     * Multi-dish recognition: one photo can hold several visually separate dishes.
     *
     * Accepts the current contract `{"dishes":[{name, confidence, ingredients:[{name, confidence,
     * visibleQuantity}]}]}`, a bare array of those objects, and the legacy
     * `{"dish":"...","ingredients":["..."]}` object, which becomes a single-element list.
     * Empty dishes and empty ingredients are dropped; ids are always unique.
     */
    fun parseDishes(raw: String): List<RecognizedDish> {
        val element = parsePayload(raw)

        val array: JsonArray? = when (element) {
            is JsonArray -> element
            is JsonObject -> DISH_LIST_KEYS.firstNotNullOfOrNull { key -> element[key] as? JsonArray }
            else -> null
        }

        if (array != null) {
            // A bare array of strings is a dish list without ingredients, not an error.
            val dishes = array.mapNotNull { entry -> entry.toDishOrNull() }
            if (dishes.isNotEmpty()) return dishes
            // {"dishes":[]} plus a legacy dish field is possible; fall through to the single object.
        }

        val obj = element as? JsonObject ?: return emptyList()
        val single = parseSingleDish(obj)
        return if (single.dishName.isBlank() && single.ingredients.isEmpty()) emptyList() else listOf(single)
    }

    /**
     * Legacy single-dish entry point, kept for the old contract and for callers that only
     * care about the first dish. Never throws on an empty result: a blank name becomes a
     * placeholder so the user can rename it.
     */
    fun parseDish(raw: String): RecognizedDish {
        val element = parsePayload(raw)
        val obj = when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstNotNullOfOrNull { it as? JsonObject }
                ?: throw MalformedResponse("\u041e\u0442\u0432\u0435\u0442 \u043d\u0435 \u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f JSON-\u043e\u0431\u044a\u0435\u043a\u0442\u043e\u043c"
            )
            else -> throw MalformedResponse("\u041e\u0442\u0432\u0435\u0442 \u043d\u0435 \u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f JSON-\u043e\u0431\u044a\u0435\u043a\u0442\u043e\u043c")
        }
        val nested = DISH_LIST_KEYS.firstNotNullOfOrNull { key -> obj[key] as? JsonArray }
            ?.firstNotNullOfOrNull { it.toDishOrNull() }
        if (nested != null) return nested
        val dish = parseSingleDish(obj)
        return dish.copy(dishName = dish.dishName.ifBlank { "\u0411\u043b\u044e\u0434\u043e" })
    }

    fun parseNutrition(raw: String): List<EstimatedIngredient> {
        val array = json.parseToJsonElement(extractJsonArray(raw)) as? JsonArray
            ?: throw MalformedResponse("\u041e\u0442\u0432\u0435\u0442 \u043d\u0435 \u044f\u0432\u043b\u044f\u0435\u0442\u0441\u044f JSON-\u043c\u0430\u0441\u0441\u0438\u0432\u043e\u043c")

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

    // ---- Dish helpers ----

    private fun parseSingleDish(obj: JsonObject): RecognizedDish {
        val name = DISH_NAME_KEYS.firstNotNullOfOrNull { key -> obj[key].asText() }.orEmpty()
        val rawItems = INGREDIENT_LIST_KEYS.firstNotNullOfOrNull { key -> obj[key] as? JsonArray }
            ?: JsonArray(emptyList())
        return RecognizedDish(
            dishName = name.trim(),
            ingredients = rawItems.toIngredients(),
            confidence = obj.confidence()
        )
    }

    /** One entry of a dishes array: either an object, or just the dish name as a string. */
    private fun JsonElement.toDishOrNull(): RecognizedDish? {
        val dish = when (this) {
            is JsonObject -> parseSingleDish(this)
            is JsonPrimitive -> RecognizedDish(contentOrNull?.trim().orEmpty(), emptyList())
            else -> return null
        }
        if (dish.dishName.isBlank() && dish.ingredients.isEmpty()) return null
        return dish.copy(
            dishName = dish.dishName.ifBlank { RecognizedDish.UNKNOWN_LABEL }
        )
    }

    private fun JsonArray.toIngredients(): List<RecognizedIngredient> {
        val seen = mutableSetOf<String>()
        return mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val name = INGREDIENT_NAME_KEYS.firstNotNullOfOrNull { key -> element[key].asText() }
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    RecognizedIngredient(
                        name = name,
                        confidence = element.confidence(),
                        visibleQuantityGrams = (element["visibleQuantity"] ?: element["grams"])
                            .asFloat(0f)
                            .takeIf { it > 0f }
                    )
                }

                else -> element.asText()?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { RecognizedIngredient(it) }
            }
        }.filter { ingredient -> seen.add(ingredient.name.lowercase()) }
    }

    /** Models report confidence as 0..1 or as a percentage; both are normalised to 0..1. */
    private fun JsonObject.confidence(): Float? {
        val raw = (this["confidence"] ?: this["probability"] ?: this["certainty"]) ?: return null
        val value = raw.asFloat(-1f)
        if (value < 0f) return null
        val normalized = if (value > 1f) value / 100f else value
        return normalized.coerceIn(0f, 1f)
    }

    // ---- Extraction helpers ----

    /** Parses the first JSON object or array found in the answer, ignoring prose and fences. */
    private fun parsePayload(raw: String): JsonElement {
        val stripped = stripFences(raw)
        val objStart = stripped.indexOf('{')
        val arrStart = stripped.indexOf('[')
        val candidates = buildList {
            if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) add(extractJsonArray(stripped))
            if (objStart >= 0) add(extract(stripped, '{', '}', "\u043e\u0431\u044a\u0435\u043a\u0442"))
            if (arrStart >= 0 && objStart >= 0 && arrStart > objStart) add(extractJsonArray(stripped))
        }
        if (candidates.isEmpty()) {
            throw MalformedResponse("\u0412 \u043e\u0442\u0432\u0435\u0442\u0435 \u043c\u043e\u0434\u0435\u043b\u0438 \u043d\u0435\u0442 JSON")
        }
        candidates.forEach { candidate ->
            runCatching { json.parseToJsonElement(candidate) }.getOrNull()?.let { return it }
        }
        throw MalformedResponse("\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0440\u0430\u0437\u043e\u0431\u0440\u0430\u0442\u044c JSON \u0438\u0437 \u043e\u0442\u0432\u0435\u0442\u0430 \u043c\u043e\u0434\u0435\u043b\u0438")
    }

    fun extractJsonObject(text: String): String = extract(text, '{', '}', "\u043e\u0431\u044a\u0435\u043a\u0442")

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
        throw MalformedResponse("\u0412 \u043e\u0442\u0432\u0435\u0442\u0435 \u043c\u043e\u0434\u0435\u043b\u0438 \u043d\u0435\u0442 JSON-\u043c\u0430\u0441\u0441\u0438\u0432\u0430")
    }

    private fun extract(text: String, open: Char, close: Char, what: String): String {
        val stripped = stripFences(text)
        val start = stripped.indexOf(open)
        val end = stripped.lastIndexOf(close)
        if (start < 0 || end <= start) throw MalformedResponse("\u0412 \u043e\u0442\u0432\u0435\u0442\u0435 \u043c\u043e\u0434\u0435\u043b\u0438 \u043d\u0435\u0442 JSON-" + what)
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
