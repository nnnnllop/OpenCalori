package com.opencalori.app.data.network

import com.opencalori.app.data.network.dto.ChatCompletionRequest
import com.opencalori.app.data.network.dto.ChatCompletionResponse
import com.opencalori.app.data.network.dto.ChatMessage
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

/**
 * Thin OpenAI-compatible chat client.
 *
 * One shared [HttpClient] for the whole app: the previous version built and closed an
 * OkHttp engine per request, throwing away the connection pool and paying a fresh TLS
 * handshake every single time.
 */
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
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    sealed class Result {
        data class Success(val text: String) : Result()
        data class HttpError(val code: Int, val body: String) : Result()
        data class NetworkError(val cause: Throwable) : Result()

        /** Message already phrased for a human. */
        val userMessage: String
            get() = when (this) {
                is Success -> ""
                is HttpError -> ApiErrorMessages.forHttp(code, body)
                is NetworkError -> ApiErrorMessages.forNetwork(cause)
            }
    }

    /**
     * Sends a chat completion, retrying transient failures (429 / 5xx / dropped
     * connections) with exponential backoff.
     */
    suspend fun chatCompletion(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024,
        retries: Int = 2
    ): Result {
        var last: Result = Result.NetworkError(IllegalStateException("no attempt"))

        repeat(retries + 1) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS * (1L shl (attempt - 1)))

            last = singleCall(config, messages, maxTokens)
            when (val result = last) {
                is Result.Success -> return result
                is Result.HttpError -> if (!ApiErrorMessages.isTransient(result.code)) return result
                is Result.NetworkError -> Unit // retry
            }
        }
        return last
    }

    private suspend fun singleCall(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int
    ): Result = try {
        val response = client.post(config.chatCompletionsUrl) {
            header(HttpHeaders.Authorization, "Bearer " + config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                ChatCompletionRequest(
                    model = config.modelId,
                    messages = messages,
                    maxTokens = maxTokens
                )
            )
        }
        val rawBody = response.bodyAsText()
        if (response.status.isSuccess()) {
            val text = runCatching { json.decodeFromString<ChatCompletionResponse>(rawBody) }
                .getOrNull()
                ?.firstText
                ?.takeIf { it.isNotBlank() }

            if (text != null) {
                Result.Success(text)
            } else {
                Result.HttpError(response.status.value, rawBody)
            }
        } else {
            Result.HttpError(response.status.value, rawBody)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.NetworkError(e)
    }

    private companion object {
        const val RETRY_DELAY_MS = 1200L
    }
}
