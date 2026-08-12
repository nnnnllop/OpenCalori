package com.opencalori.app.data.network

import com.opencalori.app.data.network.dto.ChatCompletionRequest
import com.opencalori.app.data.network.dto.ChatCompletionResponse
import com.opencalori.app.data.network.dto.ChatMessage
import com.opencalori.app.domain.model.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiClient @Inject constructor() {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun buildClient(config: ApiConfig): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        engine {
            config {
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }
    }

    sealed class Result {
        data class Success(val text: String) : Result()
        data class HttpError(val code: Int, val body: String) : Result()
        data class NetworkError(val cause: Throwable) : Result()
    }

    suspend fun chatCompletion(
        config: ApiConfig,
        messages: List<ChatMessage>,
        maxTokens: Int = 1024
    ): Result {
        val client = buildClient(config)
        return try {
            val url = config.baseUrl.trimEnd('/') + "/chat/completions"
            val response = client.post(url) {
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
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
                val parsed = runCatching { json.decodeFromString<ChatCompletionResponse>(rawBody) }.getOrNull()
                val text = parsed?.firstText
                    ?: parsed?.choices?.firstOrNull()?.message?.content?.let { el ->
                        runCatching { el.toString() }.getOrNull()
                    }
                if (text != null) {
                    Result.Success(text)
                } else {
                    Result.HttpError(response.status.value, "Empty content in response: $rawBody")
                }
            } else {
                Result.HttpError(response.status.value, rawBody)
            }
        } catch (e: Exception) {
            Result.NetworkError(e)
        } finally {
            client.close()
        }
    }
}
