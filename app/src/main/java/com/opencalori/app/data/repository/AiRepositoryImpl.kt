package com.opencalori.app.data.repository

import com.opencalori.app.data.network.AiResponseParser
import com.opencalori.app.data.network.ApiErrorMessages
import com.opencalori.app.data.network.OpenAiClient
import com.opencalori.app.data.network.dto.ChatMessage
import com.opencalori.app.domain.model.ApiValidationResult
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.model.ValidationStatus
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.ApiConfigStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val apiConfigStore: ApiConfigStore,
    private val client: OpenAiClient
) : AiRepository {

    /**
     * Two-step validation: a cheap text ping proves the key and URL, a 1x1 PNG proves the
     * model can actually see images before the user wastes a real photo on it.
     */
    override suspend fun validateApi(): ApiValidationResult {
        val config = apiConfigStore.current()
        if (!config.isConfigured) {
            return ApiValidationResult(ValidationStatus.UNKNOWN_ERROR, "Заполните Base URL, ключ и Model ID")
        }

        when (val ping = client.chatCompletion(
            config,
            listOf(ChatMessage.text("user", "Reply with the single word: ok")),
            maxTokens = 8
        )) {
            is OpenAiClient.Result.NetworkError ->
                return ApiValidationResult(ValidationStatus.NETWORK_ERROR, ping.userMessage)

            is OpenAiClient.Result.HttpError -> when {
                ping.code == 401 || ping.code == 403 ->
                    return ApiValidationResult(ValidationStatus.AUTH_ERROR, ping.userMessage)

                ping.code == 404 ->
                    return ApiValidationResult(ValidationStatus.UNKNOWN_ERROR, ping.userMessage)

                else -> Unit // keep going, the vision probe is more informative
            }

            is OpenAiClient.Result.Success -> Unit
        }

        val visionPing = client.chatCompletion(
            config,
            listOf(
                ChatMessage.vision(
                    role = "user",
                    text = "What color is this image? Reply with one word.",
                    imageBase64 = ONE_PIXEL_PNG_BASE64,
                    mimeType = "image/png",
                    detail = "low"
                )
            ),
            maxTokens = 16
        )

        return when (visionPing) {
            is OpenAiClient.Result.Success ->
                ApiValidationResult(ValidationStatus.SUCCESS, "Подключено. Модель поддерживает Vision.")

            is OpenAiClient.Result.NetworkError ->
                ApiValidationResult(ValidationStatus.NETWORK_ERROR, visionPing.userMessage)

            is OpenAiClient.Result.HttpError -> when {
                visionPing.code == 401 || visionPing.code == 403 ->
                    ApiValidationResult(ValidationStatus.AUTH_ERROR, visionPing.userMessage)

                ApiErrorMessages.looksLikeMissingVision(visionPing.code, visionPing.body) ->
                    ApiValidationResult(
                        ValidationStatus.NO_VISION,
                        "Ключ рабочий, но модель не принимает изображения. Выберите мультимодальную модель."
                    )

                else -> ApiValidationResult(ValidationStatus.UNKNOWN_ERROR, visionPing.userMessage)
            }
        }
    }

    override suspend fun recognizeDish(imageBase64: String): Result<RecognizedDish> = runCatching {
        val config = apiConfigStore.current()
        require(config.isConfigured) { NOT_CONFIGURED }

        val prompt = """
            Ты - эксперт по кулинарии и нутрициологии. Посмотри на фото еды.

            Определи:
            1. Название блюда (если это сложное блюдо - назови его, если простой набор продуктов - напиши "Продукты").
            2. Список ингредиентов/продуктов, которые ты видишь.

            Ответь СТРОГО валидным JSON-объектом без markdown-обёртки, без комментариев, в формате:
            {"dish":"Название блюда","ingredients":["Продукт 1","Продукт 2","Продукт 3"]}

            Правила:
            - Названия продуктов - на русском языке, кратко (1-3 слова).
            - Если видишь гарнир и основное блюдо - перечисли их отдельно.
            - Если еды нет - верни {"dish":"","ingredients":[]}.
        """.trimIndent()

        when (val response = client.chatCompletion(
            config,
            // "low" detail is plenty to name a dish and costs a fraction of the tokens.
            listOf(ChatMessage.vision("user", prompt, imageBase64, detail = "low")),
            maxTokens = 500
        )) {
            is OpenAiClient.Result.Success -> {
                val dish = AiResponseParser.parseDish(response.text)
                dish.copy(dishName = dish.dishName.ifBlank { "Блюдо" })
            }

            is OpenAiClient.Result.HttpError -> error(response.userMessage)
            is OpenAiClient.Result.NetworkError -> error(response.userMessage)
        }
    }

    override suspend fun estimateNutrition(
        imageBase64: String,
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>> = runCatching {
        val config = apiConfigStore.current()
        require(config.isConfigured) { NOT_CONFIGURED }

        val itemList = correctedIngredients.joinToString("\n") { "- " + it }
        val prompt = """
            Ты - эксперт по нутрициологии. На фото: "$dishName".
            Пользователь подтвердил следующий список ингредиентов:
            $itemList

            Для КАЖДОГО ингредиента оцени по фото:
            1. Массу в СЫРОМ/СУХОМ виде (граммы) - если применимо (для круп, мяса и т.п.).
            2. Массу в ГОТОВОМ виде (граммы) - как она выглядит на тарелке.
            3. Пищевую ценность НА 100 Г ГОТОВОГО продукта: калории (ккал), белки (г), жиры (г), углеводы (г).
            4. Способ приготовления (notes): "сырой", "варёный", "жареный", "запечённый", "сухой" и т.д.

            Ответь СТРОГО валидным JSON-массивом без markdown-обёртки, без комментариев, в формате:
            [{"name":"Название","rawGrams":80,"cookedGrams":200,"calories":130,"protein":2.5,"fat":0.5,"carbs":28,"notes":"варёный"}]

            Правила:
            - Числа - без единиц измерения.
            - Если сырой вес неприменим (например, для овощного салата) - ставь rawGrams = cookedGrams.
            - КБЖУ указывай для ГОТОВОГО продукта (как принято в стандартных таблицах).
            - Верни ровно ${correctedIngredients.size} элементов, по одному на каждый ингредиент.
            - Если не уверен - дай разумную оценку.
        """.trimIndent()

        // Budget scales with the list: a fixed 1800 truncated the JSON on large plates,
        // and a truncated array is unparseable.
        val budget = (600 + correctedIngredients.size * 180).coerceIn(600, 4000)

        when (val response = client.chatCompletion(
            config,
            listOf(ChatMessage.vision("user", prompt, imageBase64)),
            maxTokens = budget
        )) {
            is OpenAiClient.Result.Success -> {
                val parsed = AiResponseParser.parseNutrition(response.text)
                if (parsed.isEmpty()) error("Модель не вернула ни одного продукта. Попробуйте ещё раз.")
                parsed
            }

            is OpenAiClient.Result.HttpError -> error(response.userMessage)
            is OpenAiClient.Result.NetworkError -> error(response.userMessage)
        }
    }

    private companion object {
        const val NOT_CONFIGURED = "ИИ не настроен. Добавьте API-ключ в настройках."

        // 1x1 white PNG
        const val ONE_PIXEL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    }
}
