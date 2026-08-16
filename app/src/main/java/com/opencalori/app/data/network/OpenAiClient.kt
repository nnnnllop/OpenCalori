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
                    // Reasoning models (Nemotron, DeepSeek-R1) emit nothing for minutes
                    // before the first token; the socket would idle out at 120s.
                    readTimeout(300, TimeUnit.SECONDS)
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
     * one-off repair request, plus a single compatibility fallback when the provider rejects an
     * optional request parameter (json mode, max_tokens, temperature) that other providers accept.
     */
    suspend fun chatCompletion(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
        preferJsonMode: Boolean = false
    ): Result {
        val first = singleCall(config, messages, maxTokens, preferJsonMode, allowCompatFallback = true)
        if (first is Result.HttpError && first.code == 400) {
            val fallback = compatFallbackFor(config, first.body) ?: return first
            applyFallback(config, fallback)
            return singleCall(config, messages, maxTokens, preferJsonMode, allowCompatFallback = false)
        }
        return first
    }

    /**
     * Parameter dialects differ across OpenAI-compatible servers: o-series wants
     * max_completion_tokens instead of max_tokens, some reject response_format or temperature.
     * The provider's own 400 message is the most reliable signal of what to change.
     */
    private fun compatFallbackFor(config: ApiConfig, body: String): Set<String>? {
        val hints = ApiErrorMessages.unsupportedParameterHints(body)
        if (hints.isEmpty()) return null
        var changed = false
        if (CompatHints.MAX_TOKENS in hints && !maxTokensRejected.contains(config.baseUrl)) changed = true
        if (CompatHints.RESPONSE_FORMAT in hints && !jsonModeRejected.contains(config.baseUrl)) changed = true
        if (CompatHints.TEMPERATURE in hints && !temperatureRejected.contains(config.baseUrl)) changed = true
        return if (changed) hints else null
    }

    private fun applyFallback(config: ApiConfig, hints: Set<String>) {
        if (CompatHints.MAX_TOKENS in hints) maxTokensRejected.add(config.baseUrl)
        if (CompatHints.RESPONSE_FORMAT in hints) jsonModeRejected.add(config.baseUrl)
        if (CompatHints.TEMPERATURE in hints) temperatureRejected.add(config.baseUrl)
    }

    private val maxTokensRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val jsonModeRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val temperatureRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private suspend fun singleCall(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int,
        preferJsonMode: Boolean,
        allowCompatFallback: Boolean
    ): Result = try {
        val response = client.post(config.chatCompletionsUrl) {
            header(HttpHeaders.Authorization, "Bearer " + config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildRequest(config, messages, maxTokens, preferJsonMode))
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

    private fun buildRequest(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int,
        preferJsonMode: Boolean
    ): ChatCompletionRequest = ChatCompletionRequest(
        model = config.modelId,
        messages = messages,
        maxTokens = if (maxTokensRejected.contains(config.baseUrl)) null else maxTokens,
        maxCompletionTokens = if (maxTokensRejected.contains(config.baseUrl)) maxTokens else null,
        temperature = if (temperatureRejected.contains(config.baseUrl)) null else 0.2f,
        responseFormat = if (preferJsonMode && supportsJsonMode(config)) JsonResponseFormat() else null
    )

    /**
     * JSON mode is sent to any provider unless this exact provider already rejected it once;
     * the 400-compat fallback above learns and remembers per base URL.
     */
    private fun supportsJsonMode(config: ApiConfig): Boolean =
        !jsonModeRejected.contains(config.baseUrl)

    private object CompatHints {
        const val MAX_TOKENS = "max_tokens"
        const val RESPONSE_FORMAT = "response_format"
        const val TEMPERATURE = "temperature"
    }
}
