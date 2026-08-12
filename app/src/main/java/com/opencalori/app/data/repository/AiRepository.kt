package com.opencalori.app.data.repository

import com.opencalori.app.data.network.OpenAiClient
import com.opencalori.app.data.network.dto.ChatMessage
import com.opencalori.app.data.preferences.ApiKeyStore
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.EstimatedFoodItem
import com.opencalori.app.domain.model.RecognizedFood
import com.opencalori.app.domain.model.ValidationStatus
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val client: OpenAiClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Validates the API config in two steps:
     * 1. Simple text ping to check key/URL validity.
     * 2. Vision ping with a 1x1 pixel PNG to verify multimodal support.
     */
    suspend fun validateApi(): ApiValidationResult {
        val config = apiKeyStore.config.first()
        if (!config.isConfigured) {
            return ApiValidationResult(ValidationStatus.UNKNOWN_ERROR, "API не настроен")
        }

        // Step 1: text ping
        val ping = client.chatCompletion(
            config,
            listOf(ChatMessage.text("user", "Reply with the single word: ok")),
            maxTokens = 8
        )
        when (ping) {
            is OpenAiClient.Result.NetworkError ->
                return ApiValidationResult(ValidationStatus.NETWORK_ERROR, ping.cause.message ?: "Сетевая ошибка")
            is OpenAiClient.Result.HttpError -> {
                if (ping.code == 401 || ping.code == 403)
                    return ApiValidationResult(ValidationStatus.AUTH_ERROR, "Неверный API-ключ (HTTP ${ping.code})")
                if (ping.code == 404)
                    return ApiValidationResult(ValidationStatus.UNKNOWN_ERROR, "Эндпоинт не найден (HTTP 404). Проверьте Base URL.")
                // other codes — continue to vision check anyway
            }
            is OpenAiClient.Result.Success -> Unit
        }

        // Step 2: vision ping with 1x1 transparent PNG
        val visionPing = client.chatCompletion(
            config,
            listOf(
                ChatMessage.vision(
                    role = "user",
                    text = "What color is this image? Reply with one word.",
                    imageBase64 = ONE_PIXEL_PNG_BASE64,
                    mimeType = "image/png"
                )
            ),
            maxTokens = 16
        )
        return when (visionPing) {
            is OpenAiClient.Result.Success ->
                ApiValidationResult(ValidationStatus.SUCCESS, "Модель поддерживает Vision")
            is OpenAiClient.Result.HttpError -> {
                val body = visionPing.body.lowercase()
                if (visionPing.code == 401 || visionPing.code == 403)
                    ApiValidationResult(ValidationStatus.AUTH_ERROR, "Неверный API-ключ (HTTP ${visionPing.code})")
                else if (body.contains("image") || body.contains("vision") || body.contains("multimodal")
                    || body.contains("invalid") || body.contains("unsupported") || body.contains("does not support")
                )
                    ApiValidationResult(ValidationStatus.NO_VISION, "Модель не поддерживает обработку изображений")
                else
                    ApiValidationResult(ValidationStatus.UNKNOWN_ERROR, "HTTP ${visionPing.code}: ${visionPing.body.take(300)}")
            }
            is OpenAiClient.Result.NetworkError ->
                ApiValidationResult(ValidationStatus.NETWORK_ERROR, visionPing.cause.message ?: "Сетевая ошибка")
        }
    }

    /** Stage 1: recognize the list of food items from the photo. */
    suspend fun recognizeFood(imageBase64: String): Result<List<RecognizedFood>> = runCatching {
        val config = apiKeyStore.config.first()
        require(config.isConfigured) { "API не настроен" }

        val prompt = """
            Ты — ассистент для подсчёта калорий. Посмотри на фото еды.
            Перечисли ТОЛЬКО названия продуктов/блюд, которые ты видишь, по одному на строку.
            Без описаний, без массы, без калорий, без нумерации, без маркеров списка.
            Отвечай на русском языке. Если еды нет — напиши "Еда не распознана".
        """.trimIndent()

        when (val r = client.chatCompletion(
            config,
            listOf(ChatMessage.vision("user", prompt, imageBase64)),
            maxTokens = 300
        )) {
            is OpenAiClient.Result.Success -> {
                r.text.lines()
                    .map { it.trim().trimStart('-', '•', '*', '·').trim() }
                    .filter { it.isNotBlank() && !it.contains("не распознана", ignoreCase = true) }
                    .map { RecognizedFood(it) }
            }
            is OpenAiClient.Result.HttpError -> error("HTTP ${r.code}: ${r.body.take(300)}")
            is OpenAiClient.Result.NetworkError -> error(r.cause)
        }
    }

    /** Stage 2: given user-corrected list + photo, estimate grams/macros. */
    suspend fun estimateNutrition(
        imageBase64: String,
        correctedItems: List<String>
    ): Result<List<EstimatedFoodItem>> = runCatching {
        val config = apiKeyStore.config.first()
        require(config.isConfigured) { "API не настроен" }

        val itemList = correctedItems.joinToString("\n") { "- $it" }
        val prompt = """
            Ты — эксперт по нутрициологии. На фото еда. Пользователь подтвердил следующий список продуктов:
            $itemList

            Для КАЖДОГО продукта из списка оцени по фото его массу в граммах (учитывай агрегатное состояние: сырой/варёный/жареный),
            и укажи пищевую ценность НА 100 Г: калории (ккал), белки (г), жиры (г), углеводы (г).

            Ответь СТРОГО валидным JSON-массивом без markdown-обёртки, без комментариев, в формате:
            [{"name":"Название","grams":150,"calories":130,"protein":2.5,"fat":0.5,"carbs":28,"notes":"варёный"}]

            Числа — без единиц измерения. Если не уверен — дай разумную оценку.
        """.trimIndent()

        when (val r = client.chatCompletion(
            config,
            listOf(ChatMessage.vision("user", prompt, imageBase64)),
            maxTokens = 1500
        )) {
            is OpenAiClient.Result.Success -> {
                val cleaned = extractJsonArray(r.text)
                val parsed = json.decodeFromString<List<NutritionJson>>(cleaned)
                parsed.map {
                    EstimatedFoodItem(
                        name = it.name,
                        grams = it.grams,
                        caloriesPer100g = it.calories,
                        proteinPer100g = it.protein,
                        fatPer100g = it.fat,
                        carbsPer100g = it.carbs,
                        notes = it.notes
                    )
                }
            }
            is OpenAiClient.Result.HttpError -> error("HTTP ${r.code}: ${r.body.take(300)}")
            is OpenAiClient.Result.NetworkError -> error(r.cause)
        }
    }

    private fun extractJsonArray(text: String): String {
        var s = text.trim()
        // strip markdown code fence if present
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        }
        val start = s.indexOf('[')
        val end = s.lastIndexOf(']')
        require(start >= 0 && end > start) { "JSON-массив не найден в ответе: ${s.take(200)}" }
        return s.substring(start, end + 1)
    }

    @kotlinx.serialization.Serializable
    private data class NutritionJson(
        val name: String,
        val grams: Float,
        val calories: Float,
        val protein: Float,
        val fat: Float,
        val carbs: Float,
        val notes: String = ""
    )

    companion object {
        // 1x1 white PNG
        private const val ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    }
}
