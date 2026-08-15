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
            Ты — очень внимательный эксперт по блюдам и составу порций. Выполни только первый этап:
            точно назови видимое блюдо и перечисли его видимые съедобные компоненты.

            Сначала мысленно осмотри всю тарелку, затем основное блюдо, гарнир, овощи, соус и отдельные
            продукты. Название блюда используй только когда внешний вид уверенно это подтверждает. Если
            на фото не одно узнаваемое блюдо, а набор отдельных продуктов, укажи dish = "Продукты".

            Ответь СТРОГО одним валидным JSON-объектом без Markdown, пояснений и лишних ключей:
            {"dish":"Название блюда","ingredients":["Продукт 1","Продукт 2"]}

            Обязательные правила точности:
            - Все названия — на русском языке, короткие и каноничные. Например: "паста карбонара",
              "куриное филе", "рис варёный", "огурец".
            - Перечисляй только то, что действительно видно. Не достраивай скрытый рецепт, не добавляй
              специи, масло, соус или ингредиенты, которые нельзя уверенно различить на фото.
            - Основное блюдо, гарнир и каждый отдельный компонент перечисляй раздельно. Не дублируй
              компоненты и не объединяй разные продукты в абстрактное "салат", если видны его части.
            - Если компонент неясен, используй честное общее название (например, "белый соус"), а не
              выдумывай конкретный продукт.
            - Название dish — это понятное человеку название всей порции; ingredients — состав для расчёта.
            - Если еды нет, верни {"dish":"","ingredients":[]}.
        """.trimIndent()

        when (val response = client.chatCompletion(
            config,
            // Use the provider default image detail: subtle ingredients and sauces are critical here.
            listOf(ChatMessage.vision("user", prompt, imageBase64)),
            maxTokens = 750
        )) {
            is OpenAiClient.Result.Success -> {
                val dish = AiResponseParser.parseDish(response.text)
                dish.copy(dishName = dish.dishName.ifBlank { "Блюдо" })
            }

            is OpenAiClient.Result.HttpError -> error(response.userMessage)
            is OpenAiClient.Result.NetworkError -> error(response.userMessage)
        }
    }

    override suspend fun recognizeText(description: String): Result<RecognizedDish> = runCatching {
        val config = apiConfigStore.current()
        require(config.isConfigured) { NOT_CONFIGURED }
        val prompt = """
            Ты помощник дневника питания. Пользователь описал, что съел: "$description".
            Верни только JSON без Markdown: {"dish":"название блюда или приёма пищи","ingredients":["продукт 1","продукт 2"]}.
            Раздели несколько блюд в понятный общий состав, не придумывай граммовки и не добавляй продукты, которых пользователь не упомянул.
        """.trimIndent()
        when (val response = client.chatCompletion(config, listOf(ChatMessage.text("user", prompt)), maxTokens = 500)) {
            is OpenAiClient.Result.Success -> AiResponseParser.parseDish(response.text)
            is OpenAiClient.Result.HttpError -> error(response.userMessage)
            is OpenAiClient.Result.NetworkError -> error(response.userMessage)
        }
    }

    override suspend fun estimateTextNutrition(
        dishName: String,
        correctedIngredients: List<String>
    ): Result<List<EstimatedIngredient>> = runCatching {
        val config = apiConfigStore.current()
        require(config.isConfigured) { NOT_CONFIGURED }
        val list = correctedIngredients.joinToString("\n") { "- $it" }
        val prompt = """
            Ты считаешь КБЖУ для дневника питания. Блюдо: "$dishName". Продукты: \n$list
            Верни только JSON-массив без Markdown. Для каждого продукта верни name, rawGrams: 0, cookedGrams: 0, calories, protein, fat, carbs, notes.
            Нельзя придумывать вес: rawGrams и cookedGrams всегда 0, потому что граммовку введёт пользователь. КБЖУ укажи на 100 г съедобного продукта.
        """.trimIndent()
        when (val response = client.chatCompletion(config, listOf(ChatMessage.text("user", prompt)), maxTokens = (600 + correctedIngredients.size * 160).coerceIn(600, 3000))) {
            is OpenAiClient.Result.Success -> AiResponseParser.parseNutrition(response.text)
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
            Ты — эксперт по нутрициологии и оценке порций по фото. На фото блюдо: "$dishName".
            Пользователь уже подтвердил окончательный состав ниже. Этот список является источником истины:
            $itemList

            Для каждого компонента оцени видимую съедобную порцию и КБЖУ. Не добавляй продукты, не
            объединяй позиции, не меняй порядок и не подменяй названия синонимами. Фото используется
            для оценки размера порции и способа приготовления, а подтверждённый список — для состава.

            Верни СТРОГО валидный JSON-массив без Markdown, комментариев и дополнительных ключей:
            [{"name":"Название","rawGrams":80,"cookedGrams":200,"calories":130,"protein":2.5,"fat":0.5,"carbs":28,"notes":"варёный"}]

            Правила точности:
            - Верни ровно \${correctedIngredients.size} элементов в том же порядке, что и во входном списке.
              Поле name должно в точности повторять соответствующее подтверждённое название.
            - Все числа — числа без единиц, неотрицательные и реалистичные для порции на фотографии.
            - cookedGrams — масса продукта в том виде, в котором он виден. rawGrams указывай только
              когда исходный вес можно обоснованно оценить; иначе делай rawGrams = cookedGrams.
            - calories, protein, fat и carbs указывай НА 100 г в указанном готовом состоянии, а не итог
              всей порции. Не округляй всё до одинаковых значений.
            - notes содержит краткий способ приготовления: "сырой", "варёный", "жареный",
              "запечённый", "тушёный" или другой честный вариант.
            - Не учитывай масло или соус, если их нет в подтверждённом списке. При неопределённости
              выбирай консервативную среднюю оценку, а не экстремальное значение.
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
