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
import kotlinx.coroutines.delay
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
        data class HttpError(
            val code: Int,
            val body: String,
            /** Provider-advised wait in seconds, from Retry-After or the error body. */
            val retryAfterSeconds: Long? = null
        ) : Result()
        data class NetworkError(val cause: Throwable) : Result()
        data class EmptyResponse(val truncated: Boolean = false) : Result()

        /** Message safe to show only after repository-level classification. */
        val userMessage: String
            get() = when (this) {
                is Success -> ""
                is HttpError -> ApiErrorMessages.forHttp(code, body)
                is NetworkError -> ApiErrorMessages.forNetwork(cause)
                is EmptyResponse -> AiPipelineError.EmptyResponse.userMessage
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
        // Rate limits (429) and transient provider hiccups (408/503/504) are waited out
        // seamlessly: the provider names the exact pause ("Please try again in 27.4s",
        // Retry-After) and the request repeats on its own; the user only sees a slightly
        // longer "analysing" state instead of an error.
        var budget = maxTokens
        var attempt = 0
        var result = callOnce(config, messages, budget, preferJsonMode)
        while (attempt < MAX_TRANSIENT_ATTEMPTS && result.isTransientRetryable()) {
            val error = result as Result.HttpError
            val advised = error.retryAfterSeconds
            val backoff = TRANSIENT_BACKOFF_SECONDS.elementAtOrNull(attempt) ?: TRANSIENT_BACKOFF_SECONDS.last()
            // Providers with a tokens-per-minute quota (Groq free tier) reject the whole
            // reservation (prompt + max_tokens), so a generous budget always 429s there.
            // The "Limit 8000, Used 4699, Requested 5353" line tells us how much is still
            // free: if something fits we shrink max_tokens and go now, otherwise the window
            // is spent and the provider's own advice ("try again in 27.4s") is honoured.
            val plan = TpmPlanner.plan(error.body, effectiveBudget(config, budget))
            if (plan != null) budget = plan.budget
            val waitSeconds = if (plan?.retryImmediately == true) {
                1L
            } else {
                (advised ?: backoff).coerceIn(1, MAX_TRANSIENT_WAIT_SECONDS)
            }
            delay(waitSeconds * 1000)
            attempt++
            result = callOnce(config, messages, budget, preferJsonMode)
        }
        return result
    }

    private fun Result.isTransientRetryable(): Boolean =
        this is Result.HttpError && (code == 429 || code == 413 || code == 408 || code == 503 || code == 504)

    /** One full attempt including the compatibility and truncation self-healing. */
    private suspend fun callOnce(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int,
        preferJsonMode: Boolean
    ): Result {
        val first = singleCall(config, messages, maxTokens, preferJsonMode, allowCompatFallback = true)
        // Reasoning models can burn the whole budget on thinking and return no payload at all;
        // one immediate retry with a doubled budget usually recovers the answer.
        if (first is Result.EmptyResponse && first.truncated) {
            return singleCall(config, messages, (maxTokens * 2).coerceAtMost(MAX_TOKENS_CAP),
                preferJsonMode, allowCompatFallback = false)
        }
        if (first is Result.HttpError && first.code == 400) {
            // "max_tokens must be <= 16384" means a smaller number in the same field,
            // not the o-series field switch handled by the parameter hints below.
            ApiErrorMessages.maxTokensCeiling(first.body)?.let { ceiling ->
                maxTokensCeilings.merge(config.baseUrl, ceiling, ::minOf)
                return singleCall(config, messages, effectiveBudget(config, maxTokens),
                    preferJsonMode, allowCompatFallback = false)
            }
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
        if (CompatHints.REASONING_FORMAT in hints && !reasoningFormatRejected.contains(config.baseUrl)) changed = true
        return if (changed) hints else null
    }

    private fun applyFallback(config: ApiConfig, hints: Set<String>) {
        if (CompatHints.MAX_TOKENS in hints) maxTokensRejected.add(config.baseUrl)
        if (CompatHints.RESPONSE_FORMAT in hints) jsonModeRejected.add(config.baseUrl)
        if (CompatHints.TEMPERATURE in hints) temperatureRejected.add(config.baseUrl)
        if (CompatHints.REASONING_FORMAT in hints) reasoningFormatRejected.add(config.baseUrl)
    }

    private val maxTokensRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val jsonModeRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val temperatureRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val reasoningFormatRejected = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val maxTokensCeilings = java.util.concurrent.ConcurrentHashMap<String, Int>()

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
            Result.HttpError(
                code = response.status.value,
                body = rawBody,
                retryAfterSeconds = retryAfterSeconds(response.headers[HttpHeaders.RetryAfter], rawBody)
            )
        } else {
            val decoded = runCatching { json.decodeFromString<ChatCompletionResponse>(rawBody) }.getOrNull()
            val text = decoded?.firstText?.takeIf { it.isNotBlank() }
            when {
                text != null -> Result.Success(text)
                else -> Result.EmptyResponse(truncated = decoded?.truncated == true)
            }
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
        // Some providers cap max_tokens far below our generous budgets (Groq qwen3.6: 16384).
        maxTokens = if (maxTokensRejected.contains(config.baseUrl)) null else effectiveBudget(config, maxTokens),
        maxCompletionTokens = if (maxTokensRejected.contains(config.baseUrl)) maxTokens else null,
        temperature = if (temperatureRejected.contains(config.baseUrl)) null else 0.2f,
        responseFormat = if (preferJsonMode && supportsJsonMode(config)) JsonResponseFormat() else null,
        reasoningFormat = if (config.baseUrl.lowercase().contains("groq.com") &&
            !reasoningFormatRejected.contains(config.baseUrl)) "hidden" else null
    )

    private fun effectiveBudget(config: ApiConfig, requested: Int): Int {
        val ceiling = maxTokensCeilings[config.baseUrl] ?: return requested
        return requested.coerceAtMost(ceiling)
    }

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
        const val REASONING_FORMAT = "reasoning_format"
    }

    /** Provider-advised wait: the Retry-After header (seconds) or Groq's "try again in 27.4s". */
    private fun retryAfterSeconds(header: String?, body: String): Long? {
        header?.trim()?.toLongOrNull()?.let { return it }
        return RETRY_AFTER_IN_BODY.find(body)?.value?.toDoubleOrNull()?.toLong()
    }

    private companion object {
        const val MAX_TOKENS_CAP = TpmPlanner.MAX_TOKENS_CAP
        const val MAX_TRANSIENT_ATTEMPTS = 3
        const val MAX_TRANSIENT_WAIT_SECONDS = 45L
        val TRANSIENT_BACKOFF_SECONDS = longArrayOf(8, 16, 30)
        val RETRY_AFTER_IN_BODY = Regex("try again in ([0-9]+(?:\\.[0-9]+)?)\\s*s", RegexOption.IGNORE_CASE)
    }
}
