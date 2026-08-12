package com.opencalori.app.data.network

import com.opencalori.app.data.network.dto.ApiErrorEnvelope
import kotlinx.serialization.json.Json

/**
 * Converts provider errors into something a human can act on.
 *
 * Users should never see `HTTP 429: {"error":{"type":"rate_limit_exceeded"...` - they see
 * "Слишком много запросов" plus the provider's own message when it is informative.
 */
object ApiErrorMessages {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** True for errors that are worth retrying after a short pause. */
    fun isTransient(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    fun forHttp(code: Int, body: String): String {
        val detail = providerMessage(body)
        val base = when (code) {
            400 -> "Провайдер отклонил запрос (400). Обычно дело в Model ID или в том, что модель не принимает изображения."
            401, 403 -> "Неверный или истёкший API-ключ (" + code + ")."
            404 -> "Эндпоинт не найден (404). Проверьте Base URL - он должен заканчиваться на /v1."
            408 -> "Провайдер не ответил вовремя (408). Попробуйте ещё раз."
            413 -> "Фото слишком большое для провайдера (413)."
            422 -> "Провайдер не понял запрос (422). Проверьте Model ID."
            429 -> "Слишком много запросов или закончился лимит (429). Подождите немного."
            in 500..599 -> "Сбой на стороне провайдера (" + code + "). Обычно помогает повтор."
            else -> "Ошибка провайдера (HTTP " + code + ")."
        }
        return if (detail.isNullOrBlank()) base else base + "\n\n" + detail.take(300)
    }

    fun forNetwork(cause: Throwable): String {
        val message = cause.message.orEmpty()
        return when {
            message.contains("timeout", ignoreCase = true) ->
                "Провайдер не ответил вовремя. Проверьте связь и попробуйте снова."

            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("UnknownHost", ignoreCase = true) ->
                "Не удалось найти сервер. Проверьте Base URL и интернет-соединение."

            message.contains("CertPath", ignoreCase = true) ||
                message.contains("SSL", ignoreCase = true) ->
                "Проблема с TLS-сертификатом сервера."

            else -> "Нет соединения с провайдером. Проверьте интернет."
        }
    }

    /** Pulls the human-readable part out of an error body, if the provider sent one. */
    fun providerMessage(body: String): String? {
        if (body.isBlank()) return null
        val parsed = runCatching { json.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
        val message = parsed?.error?.message ?: parsed?.message ?: parsed?.detail
        return message?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Heuristic for "this model simply cannot see images". */
    fun looksLikeMissingVision(code: Int, body: String): Boolean {
        if (code == 401 || code == 403 || code == 429 || code in 500..599) return false
        val text = (providerMessage(body) ?: body).lowercase()
        return listOf(
            "does not support image",
            "do not support image",
            "not support vision",
            "image input",
            "multimodal",
            "image_url",
            "unsupported content type",
            "не поддерживает изображ"
        ).any { text.contains(it) }
    }
}
