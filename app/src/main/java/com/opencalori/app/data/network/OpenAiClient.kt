package com.opencalori.app.data.network

import com.opencalori.app.data.network.dto.ChatCompletionRequest
import com.opencalori.app.data.network.dto.ChatCompletionResponse
import com.opencalori.app.data.network.dto.ChatMessage
import com.opencalori.app.data.network.dto.JsonResponseFormat
import com.opencalori.app.domain.model.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One shared OpenAI-compatible client with no implicit user-visible retries. */
@Singleton
class OpenAiClient @Inject constructor() {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                    writeTimeout(120, TimeUnit.SECONDS)
                    // A user action must map to a single provider request. Repair/retry is explicit above this layer.
                    retryOnConnectionFailure(false)
                }
            }
        }
    }

    sealed class Result {
        data class Success(val text: String) : Result()
        data class HttpError(val code: Int, val body: String) : Result()
        data class NetworkError(val cause: Throwable) : Result()
        data object EmptyResponse : Result()

        /** Message safe to show only after repository-level classification. */
        val userMessage: String
            get() = when (this) {
                is Success -> ""
                is HttpError -> ApiErrorMessages.forHttp(code, body)
                is NetworkError -> ApiErrorMessages.forNetwork(cause)
                EmptyResponse -> AiPipelineError.EmptyResponse.userMessage
            }
    }

    /**
     * Sends exactly one provider request. The only automatic content recovery is the repository's
     * one-off repair request, so transport behaviour cannot silently repeat a food-entry step.
     */
    suspend fun chatCompletion(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
        preferJsonMode: Boolean = false
    ): Result = singleCall(config, messages, maxTokens, preferJsonMode)

    private suspend fun singleCall(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int,
        preferJsonMode: Boolean
    ): Result = try {
        val response = client.post(config.chatCompletionsUrl) {
            header(HttpHeaders.Authorization, "Bearer " + config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequest(
                    model = config.modelId,
                    messages = messages,
                    maxTokens = maxTokens,
                    responseFormat = if (preferJsonMode && supportsJsonMode(config)) JsonResponseFormat() else null
                )
            )
        }
        val rawBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Result.HttpError(response.status.value, rawBody)
        } else {
            val text = runCatching { json.decodeFromString<ChatCompletionResponse>(rawBody) }
                .getOrNull()
                ?.firstText
                ?.takeIf { it.isNotBlank() }
            if (text == null) Result.EmptyResponse else Result.Success(text)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.NetworkError(e)
    }

    /** Do not send optional OpenAI-only fields to arbitrary compatibility servers. */
    private fun supportsJsonMode(config: ApiConfig): Boolean =
        config.baseUrl.lowercase().contains("api.openai.com")
}
