package com.opencalori.app.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// ---------- Request ----------

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    // OpenAI o-series and some other newer models only accept max_completion_tokens.
    @SerialName("max_tokens") val maxTokens: Int? = 1024,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    // Null is omitted from the payload (explicitNulls = false), which some reasoning models require.
    val temperature: Float? = 0.2f,
    @SerialName("response_format") val responseFormat: JsonResponseFormat? = null,
    // Groq-only switch that keeps reasoning models from flooding the answer with <think>.
    @SerialName("reasoning_format") val reasoningFormat: String? = null
)

/** OpenAI-compatible JSON mode, sent only to known compatible providers. */
@Serializable
data class JsonResponseFormat(val type: String = "json_object")

/**
 * `content` is a raw JsonElement on purpose.
 *
 * Plenty of OpenAI-compatible servers (llama.cpp, LM Studio, older proxies) reject the
 * multipart array form for text-only messages and expect a plain string, so we send a
 * string when there is no image and the array form only when there is.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement
) {
    companion object {
        fun text(role: String, text: String) = ChatMessage(role, JsonPrimitive(text))

        fun vision(
            role: String,
            text: String,
            imageBase64: String,
            mimeType: String = "image/jpeg",
            detail: String = "auto"
        ) = ChatMessage(
            role,
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    }
                )
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put(
                            "image_url",
                            buildJsonObject {
                                put("url", "data:" + mimeType + ";base64," + imageBase64)
                                put("detail", detail)
                            }
                        )
                    }
                )
            }
        )
    }
}

// ---------- Response ----------

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null
) {
    /**
     * Text of the first choice, tolerating both the string and the multipart array form.
     * Reasoning models (Nemotron, DeepSeek-R1, Qwen-thinking) sometimes put the payload only
     * into reasoning_content or wrap it in <think> tags, so a blank content must not win.
     */
    val firstText: String?
        get() = choices.firstOrNull()?.message?.let { message ->
            val contentText = message.content?.let(::flatten)
            when {
                !contentText.isNullOrBlank() -> contentText
                else -> message.reasoningContent?.takeIf { it.isNotBlank() }
                    ?: message.reasoning?.takeIf { it.isNotBlank() }
            }
        }

    /** True when the model hit the token limit before producing any payload text. */
    val truncated: Boolean
        get() = choices.firstOrNull()?.finishReason == "length"

    private fun flatten(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element
            .mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.contentOrNull
                    is JsonObject -> (part["text"] as? JsonPrimitive)?.contentOrNull
                    else -> null
                }
            }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

        is JsonObject -> (element["text"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }
}

@Serializable
data class Choice(
    val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val content: JsonElement? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    // Groq's dialect of the reasoning field, alongside the DeepSeek-style reasoning_content.
    val reasoning: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/** Shape used by providers that wrap the error object: {"error": {"message": ...}}. */
@Serializable
data class ApiErrorEnvelope(
    val error: ApiError? = null,
    val message: String? = null,
    val detail: String? = null
)
