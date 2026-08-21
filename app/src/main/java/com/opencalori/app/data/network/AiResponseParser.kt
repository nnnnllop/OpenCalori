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
    private val NUMBER_IN_STRING = Regex("-?\\d+(?:[.,]\\d+)?")

    /** Kept only for source compatibility with older parser callers. */
    class MalformedResponse : AiPipelineException(AiPipelineError.MalformedJson)

    enum class NutritionWeightPolicy { USER_INPUT_ONLY, PHOTO_ESTIMATE }

    fun parseDishes(raw: String): List<RecognizedDish> {
        val payload = extractPayload(raw)
        // json_object mode cannot produce a top-level array, but a bare array still arrives
        // from providers without JSON mode, and wrappers arrive from chatty models.
        val dishArray = when (payload) {
            is JsonObject -> payload["dishes"] as? JsonArray ?: singleArrayValue(payload)
            is JsonArray -> payload
            else -> null
        } ?: fail(AiPipelineError.WrongSchema)
        if (dishArray.size > MAX_DISHES) fail(AiPipelineError.InvalidRange)

        val seenDishes = mutableSetOf<String>()
        return dishArray.mapIndexed { dishIndex, value ->
            val dish = value as? JsonObject ?: fail(AiPipelineError.WrongSchema)
            val name = requiredText(dish, "name")
            val normalizedName = name.normalized()
            if (!seenDishes.add(normalizedName)) fail(AiPipelineError.DuplicateItem)
            val confidence = requiredConfidence(dish, "confidence")
            val ingredients = requiredArray(dish, "ingredients")
            if (ingredients.size > MAX_INGREDIENTS) fail(AiPipelineError.InvalidRange)

            val seenIngredients = mutableSetOf<String>()
            val parsedIngredients = ingredients.mapIndexed { ingredientIndex, item ->
                val ingredient = item as? JsonObject ?: fail(AiPipelineError.WrongSchema)
                val ingredientName = requiredText(ingredient, "name")
                if (!seenIngredients.add(ingredientName.normalized())) fail(AiPipelineError.DuplicateItem)
                val ingredientConfidence = requiredConfidence(ingredient, "confidence")
                RecognizedIngredient(
                    name = ingredientName,
                    id = stableId("ingredient", dishIndex * MAX_INGREDIENTS + ingredientIndex, ingredientName),
                    confidence = ingredientConfidence,
                    // The app never trusts AI weights; whatever the model put here is ignored.
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
        val partial = parseNutritionAllowPartial(raw, confirmedNames, weightPolicy)
        if (partial.missing.isNotEmpty()) fail(AiPipelineError.WrongItemCount)
        return partial.items
    }

    /** A nutrition answer with some confirmed positions absent; the caller can fetch the rest. */
    class PartialNutrition(val items: List<EstimatedIngredient>, val missing: List<String>)

    /**
     * Same validation as [parseNutrition] except a missing confirmed position is reported in
     * [PartialNutrition.missing] instead of failing: the repository asks the model once more
     * for just those products, which is far more reliable than failing the whole step.
     */
    fun parseNutritionAllowPartial(
        raw: String,
        confirmedNames: List<String>?,
        weightPolicy: NutritionWeightPolicy
    ): PartialNutrition {
        val payload = extractPayload(raw)
        // The contract is {"ingredients":[...]}; a bare array arrives from providers without
        // JSON mode, and single-array wrappers ({"items":[...]}) from models that improvise.
        val array = when (payload) {
            is JsonArray -> payload
            is JsonObject -> payload["ingredients"] as? JsonArray ?: singleArrayValue(payload)
            else -> null
        } ?: fail(AiPipelineError.WrongSchema)
        if (array.isEmpty()) fail(AiPipelineError.EmptyResponse)
        if (array.size > MAX_INGREDIENTS) fail(AiPipelineError.InvalidRange)

        val expected = confirmedNames?.map(String::trim)
        if (expected != null) {
            if (expected.any { it.isEmpty() || it.length > MAX_NAME_LENGTH }) fail(AiPipelineError.WrongSchema)
        }

        // Items are validated positionally and kept in confirmed order. Names are matched
        // case-insensitively so "Курица" vs "курица" is not a rename; unknown extras are
        // dropped; two answers for one item is a duplicate.
        val slotByName = expected?.mapIndexed { index, name -> name.normalized() to index }?.toMap()
        val bySlot = mutableMapOf<Int, JsonObject>()
        val parsed = mutableListOf<EstimatedIngredient>()
        array.forEach { item ->
            val value = item as? JsonObject ?: fail(AiPipelineError.WrongSchema)
            val name = requiredText(value, "name")
            val slot = if (expected == null) parsed.size else slotByName!![name.normalized()]
            if (slot == null) return@forEach // unknown extra position — dropped
            if (bySlot.put(slot, value) != null) fail(AiPipelineError.DuplicateItem)
            val confirmedName = if (expected == null) name else expected[slot]
            parsed.add(
                EstimatedIngredient(
                    name = confirmedName,
                    rawGrams = requiredNumber(value, "rawGrams", 0.0, MAX_GRAMS).toFloat(),
                    cookedGrams = requiredNumber(value, "cookedGrams", 0.0, MAX_GRAMS).toFloat(),
                    caloriesPer100g = requiredNumber(value, "calories", 0.0, 900.0).toFloat(),
                    proteinPer100g = requiredNumber(value, "protein", 0.0, 100.0).toFloat(),
                    fatPer100g = requiredNumber(value, "fat", 0.0, 100.0).toFloat(),
                    carbsPer100g = requiredNumber(value, "carbs", 0.0, 100.0).toFloat(),
                    notes = optionalNotes(value),
                    id = stableId("nutrition", slot, confirmedName)
                )
            )
        }
        parsed.forEach { item ->
            if (weightPolicy == NutritionWeightPolicy.USER_INPUT_ONLY && (item.rawGrams != 0f || item.cookedGrams != 0f)) {
                fail(AiPipelineError.InvalidRange)
            }
        }
        val ordered = parsed.sortedBy { item ->
            expected?.indexOfFirst { it.normalized() == item.name.normalized() } ?: 0
        }
        val missing = expected
            ?.filter { confirmed -> ordered.none { it.name.normalized() == confirmed.normalized() } }
            .orEmpty()
        return PartialNutrition(ordered, missing)
    }

    /** First array-valued property of a wrapper object, whatever the model named it. */
    private fun singleArrayValue(source: JsonObject): JsonArray? =
        source.values.filterIsInstance<JsonArray>().firstOrNull()

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

    /** Confidence is accepted as a 0..1 fraction or as a percent (86 -> 0.86). */
    private fun requiredConfidence(source: JsonObject, key: String): Float {
        val value = requiredNumber(source, key, 0.0, 100.0)
        return (if (value > 1.0) value / 100.0 else value).toFloat()
    }

    /** Cosmetic field: a missing or null notes never blocks an otherwise valid answer. */
    private fun optionalNotes(source: JsonObject): String {
        val primitive = source["notes"] as? JsonPrimitive ?: return ""
        if (!primitive.isString) return ""
        return primitive.contentOrNull.orEmpty().take(MAX_NOTE_LENGTH).trim()
    }

    /**
     * Numbers are accepted as JSON numbers or as strings ("120", "120 г", "86%"):
     * many models quote numbers despite the contract. Anything without a digit fails.
     */
    private fun requiredNumber(source: JsonObject, key: String, min: Double, max: Double): Double {
        val primitive = source[key] as? JsonPrimitive
            ?: fail(if (source.containsKey(key)) AiPipelineError.WrongSchema else AiPipelineError.MissingRequiredField)
        val number = when {
            !primitive.isString -> primitive.doubleOrNull
            else -> NUMBER_IN_STRING.find(primitive.contentOrNull.orEmpty())?.value?.replace(',', '.')?.toDouble()
        } ?: fail(AiPipelineError.InvalidNumber)
        if (!number.isFinite()) fail(AiPipelineError.InvalidNumber)
        if (number !in min..max) fail(AiPipelineError.InvalidRange)
        return number
    }

    private fun String.normalized(): String = trim().lowercase()

    private fun stableId(prefix: String, index: Int, value: String): String =
        "$prefix-$index-${value.normalized().hashCode().toUInt().toString(16)}"

    private fun fail(error: AiPipelineError): Nothing = throw AiPipelineException(error)
}
