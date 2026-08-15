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
import kotlinx.serialization.json.doubleOrNull

/** Strict boundary between an untrusted model answer and the application domain. */
object AiResponseParser {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false; explicitNulls = true }

    private const val MAX_DISHES = 20
    private const val MAX_INGREDIENTS = 40
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_NOTE_LENGTH = 160
    private const val MAX_GRAMS = 5_000.0
    private val NUTRITION_KEYS = setOf(
        "name", "rawGrams", "cookedGrams", "calories", "protein", "fat", "carbs", "notes"
    )

    /** Kept only for source compatibility with older parser callers. */
    class MalformedResponse : AiPipelineException(AiPipelineError.MalformedJson)

    enum class NutritionWeightPolicy { USER_INPUT_ONLY, PHOTO_ESTIMATE }

    fun parseDishes(raw: String): List<RecognizedDish> {
        val root = extractPayload(raw) as? JsonObject ?: fail(AiPipelineError.WrongSchema)
        requireExactlyKeys(root, setOf("dishes"))
        val dishes = requiredArray(root, "dishes")
        if (dishes.size > MAX_DISHES) fail(AiPipelineError.InvalidRange)

        val seenDishes = mutableSetOf<String>()
        return dishes.mapIndexed { dishIndex, value ->
            val dish = value as? JsonObject ?: fail(AiPipelineError.WrongSchema)
            requireExactlyKeys(dish, setOf("name", "confidence", "ingredients"))
            val name = requiredText(dish, "name")
            val normalizedName = name.normalized()
            if (!seenDishes.add(normalizedName)) fail(AiPipelineError.DuplicateItem)
            val confidence = requiredNumber(dish, "confidence", 0.0, 1.0).toFloat()
            val ingredients = requiredArray(dish, "ingredients")
            if (ingredients.size > MAX_INGREDIENTS) fail(AiPipelineError.InvalidRange)

            val seenIngredients = mutableSetOf<String>()
            val parsedIngredients = ingredients.mapIndexed { ingredientIndex, item ->
                val ingredient = item as? JsonObject ?: fail(AiPipelineError.WrongSchema)
                requireExactlyKeys(ingredient, setOf("name", "confidence", "visibleQuantity"))
                val ingredientName = requiredText(ingredient, "name")
                if (!seenIngredients.add(ingredientName.normalized())) fail(AiPipelineError.DuplicateItem)
                val ingredientConfidence = requiredNumber(ingredient, "confidence", 0.0, 1.0).toFloat()
                if (ingredient["visibleQuantity"] !is kotlinx.serialization.json.JsonNull) {
                    fail(AiPipelineError.InvalidRange)
                }
                RecognizedIngredient(
                    name = ingredientName,
                    id = stableId("ingredient", dishIndex * MAX_INGREDIENTS + ingredientIndex, ingredientName),
                    confidence = ingredientConfidence,
                    visibleQuantityGrams = null
                )
            }
            RecognizedDish(name, parsedIngredients, confidence, stableId("dish", dishIndex, name))
        }
    }

    /** Legacy overload. Application flows should provide confirmed names for identity validation. */
    fun parseNutrition(raw: String): List<EstimatedIngredient> =
        parseNutrition(raw, confirmedNames = null, weightPolicy = NutritionWeightPolicy.PHOTO_ESTIMATE)

    fun parseNutrition(
        raw: String,
        confirmedNames: List<String>?,
        weightPolicy: NutritionWeightPolicy
    ): List<EstimatedIngredient> {
        val array = extractPayload(raw) as? JsonArray ?: fail(AiPipelineError.WrongSchema)
        if (array.isEmpty()) fail(AiPipelineError.EmptyResponse)
        if (array.size > MAX_INGREDIENTS) fail(AiPipelineError.InvalidRange)

        val expected = confirmedNames?.map(String::trim)
        if (expected != null) {
            if (expected.any { it.isEmpty() || it.length > MAX_NAME_LENGTH }) fail(AiPipelineError.WrongSchema)
            if (array.size != expected.size) fail(AiPipelineError.WrongItemCount)
        }

        val seenNames = mutableSetOf<String>()
        return array.mapIndexed { index, item ->
            val value = item as? JsonObject ?: fail(AiPipelineError.WrongSchema)
            requireExactlyKeys(value, NUTRITION_KEYS)
            val name = requiredText(value, "name")
            if (!seenNames.add(name.normalized())) fail(AiPipelineError.DuplicateItem)
            if (expected != null && name != expected[index]) fail(AiPipelineError.RenamedConfirmedItem)

            val rawGrams = requiredNumber(value, "rawGrams", 0.0, MAX_GRAMS).toFloat()
            val cookedGrams = requiredNumber(value, "cookedGrams", 0.0, MAX_GRAMS).toFloat()
            if (weightPolicy == NutritionWeightPolicy.USER_INPUT_ONLY && (rawGrams != 0f || cookedGrams != 0f)) {
                fail(AiPipelineError.InvalidRange)
            }
            EstimatedIngredient(
                name = name,
                rawGrams = rawGrams,
                cookedGrams = cookedGrams,
                caloriesPer100g = requiredNumber(value, "calories", 0.0, 900.0).toFloat(),
                proteinPer100g = requiredNumber(value, "protein", 0.0, 100.0).toFloat(),
                fatPer100g = requiredNumber(value, "fat", 0.0, 100.0).toFloat(),
                carbsPer100g = requiredNumber(value, "carbs", 0.0, 100.0).toFloat(),
                notes = requiredNotes(value),
                id = stableId("nutrition", index, name)
            )
        }
    }

    /** Finds the first balanced JSON object/array without being confused by braces in strings. */
    private fun extractPayload(raw: String): JsonElement {
        val text = normaliseOuterFormatting(raw)
        if (text.isBlank()) fail(AiPipelineError.EmptyResponse)
        var sawOpening = false
        var sawIncomplete = false
        var sawMalformed = false
        text.indices.forEach { start ->
            if (text[start] !in charArrayOf('{', '[')) return@forEach
            sawOpening = true
            val candidate = balancedCandidate(text, start)
            if (candidate == null) {
                sawIncomplete = true
            } else {
                val decoded = runCatching { json.parseToJsonElement(candidate) }.getOrNull()
                if (decoded != null) return decoded
                sawMalformed = true
            }
        }
        when {
            !sawOpening -> fail(AiPipelineError.JsonNotFound)
            sawIncomplete && !sawMalformed -> fail(AiPipelineError.TruncatedResponse)
            else -> fail(AiPipelineError.MalformedJson)
        }
    }

    private fun balancedCandidate(text: String, start: Int): String? {
        val closers = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val character = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> closers.addLast('}')
                '[' -> closers.addLast(']')
                '}', ']' -> {
                    if (closers.isEmpty() || closers.removeLast() != character) return null
                    if (closers.isEmpty()) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun normaliseOuterFormatting(raw: String): String {
        var text = raw.removePrefix("\uFEFF").trim()
        if (!text.startsWith("```")) return text
        val firstLineEnd = text.indexOf('\n')
        if (firstLineEnd < 0) fail(AiPipelineError.TruncatedResponse)
        val fence = text.substring(0, firstLineEnd).trim().lowercase()
        if (fence !in setOf("```", "```json", "```jsonc", "```javascript")) return text
        text = text.substring(firstLineEnd + 1)
        val closingFence = text.lastIndexOf("```")
        if (closingFence < 0) fail(AiPipelineError.TruncatedResponse)
        if (text.substring(closingFence + 3).isNotBlank()) return text
        return text.substring(0, closingFence).trim()
    }

    private fun requiredArray(source: JsonObject, key: String): JsonArray =
        source[key] as? JsonArray ?: fail(if (source.containsKey(key)) AiPipelineError.WrongSchema else AiPipelineError.MissingRequiredField)

    private fun requiredText(source: JsonObject, key: String): String {
        val primitive = source[key] as? JsonPrimitive
            ?: fail(if (source.containsKey(key)) AiPipelineError.WrongSchema else AiPipelineError.MissingRequiredField)
        if (!primitive.isString) fail(AiPipelineError.WrongSchema)
        val text = primitive.contentOrNull?.trim() ?: fail(AiPipelineError.WrongSchema)
        if (text.isEmpty()) fail(AiPipelineError.MissingRequiredField)
        if (text.length > MAX_NAME_LENGTH) fail(AiPipelineError.InvalidRange)
        return text
    }

    private fun requiredNotes(source: JsonObject): String {
        val primitive = source["notes"] as? JsonPrimitive
            ?: fail(if (source.containsKey("notes")) AiPipelineError.WrongSchema else AiPipelineError.MissingRequiredField)
        if (!primitive.isString) fail(AiPipelineError.WrongSchema)
        val notes = primitive.contentOrNull ?: fail(AiPipelineError.WrongSchema)
        if (notes.length > MAX_NOTE_LENGTH) fail(AiPipelineError.InvalidRange)
        return notes.trim()
    }

    private fun requiredNumber(source: JsonObject, key: String, min: Double, max: Double): Double {
        val primitive = source[key] as? JsonPrimitive
            ?: fail(if (source.containsKey(key)) AiPipelineError.WrongSchema else AiPipelineError.MissingRequiredField)
        if (primitive.isString) fail(AiPipelineError.InvalidNumber)
        val number = primitive.doubleOrNull ?: fail(AiPipelineError.InvalidNumber)
        if (!number.isFinite()) fail(AiPipelineError.InvalidNumber)
        if (number !in min..max) fail(AiPipelineError.InvalidRange)
        return number
    }

    private fun requireExactlyKeys(source: JsonObject, expected: Set<String>) {
        if (source.keys != expected) fail(AiPipelineError.WrongSchema)
    }

    private fun String.normalized(): String = trim().lowercase()

    private fun stableId(prefix: String, index: Int, value: String): String =
        "$prefix-$index-${value.normalized().hashCode().toUInt().toString(16)}"

    private fun fail(error: AiPipelineError): Nothing = throw AiPipelineException(error)
}
