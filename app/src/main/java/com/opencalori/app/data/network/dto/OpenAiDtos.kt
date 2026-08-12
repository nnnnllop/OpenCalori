package com.opencalori.app.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------- Request ----------

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Float = 0.2f
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ContentPart>
) {
    companion object {
        fun text(role: String, text: String) =
            ChatMessage(role, listOf(ContentPart(type = "text", text = text)))

        fun vision(role: String, text: String, imageBase64: String, mimeType: String = "image/jpeg") =
            ChatMessage(
                role,
                listOf(
                    ContentPart(type = "text", text = text),
                    ContentPart(
                        type = "image_url",
                        imageUrl = ImageUrl(url = "data:$mimeType;base64,$imageBase64")
                    )
                )
            )
    }
}

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String,
    val detail: String = "auto"
)

// ---------- Response ----------

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null
) {
    val firstText: String?
        get() = choices.firstOrNull()?.message?.content?.let { content ->
            // content may be plain string or list of parts depending on provider
            when (content) {
                is JsonElement -> extractText(content)
                else -> null
            }
        }

    private fun extractText(element: JsonElement): String? {
        return runCatching {
            when {
                element is JsonObject -> element["text"]?.jsonPrimitive?.content
                element.jsonPrimitive.isString -> element.jsonPrimitive.content
                else -> {
                    // try as array of parts
                    element.jsonArray.joinToString("\n") { part ->
                        part.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                    }
                }
            }
        }.getOrNull()
    }
}

@Serializable
data class Choice(
    val message: ResponseMessage? = null
)

@Serializable
data class ResponseMessage(
    val content: JsonElement? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
