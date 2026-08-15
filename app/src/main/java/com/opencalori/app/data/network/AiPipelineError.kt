package com.opencalori.app.data.network

/**
 * Stable, user-safe reasons for an unsuccessful AI step.
 *
 * The UI must consume [userMessage] instead of a provider body, raw model answer, or exception
 * text. [AiPipelineException] retains the type for a targeted repair/retry decision.
 */
sealed class AiPipelineError(val userMessage: String) {
    data object EmptyResponse : AiPipelineError("ИИ не вернул ответ. Повторите текущий шаг.")
    data object JsonNotFound : AiPipelineError("ИИ вернул ответ в неподходящем формате. Повторите текущий шаг.")
    data object MalformedJson : AiPipelineError("ИИ вернул ответ в неподходящем формате. Повторите текущий шаг.")
    data object WrongSchema : AiPipelineError("ИИ вернул неполные данные. Проверьте состав и повторите текущий шаг.")
    data object MissingRequiredField : AiPipelineError("ИИ не указал все обязательные данные. Повторите текущий шаг.")
    data object InvalidNumber : AiPipelineError("ИИ вернул некорректные значения. Повторите текущий шаг.")
    data object InvalidRange : AiPipelineError("ИИ вернул значения вне допустимого диапазона. Повторите текущий шаг.")
    data object TruncatedResponse : AiPipelineError("ИИ не закончил ответ. Повторите текущий шаг.")
    data object WrongItemCount : AiPipelineError("ИИ вернул не все подтверждённые продукты. Проверьте состав и повторите текущий шаг.")
    data object RenamedConfirmedItem : AiPipelineError("ИИ изменил название подтверждённого продукта. Проверьте состав и повторите текущий шаг.")
    data object DuplicateItem : AiPipelineError("ИИ продублировал продукт. Проверьте состав и повторите текущий шаг.")
    data class ProviderError(val message: String) : AiPipelineError(message)
    data class NetworkError(val message: String) : AiPipelineError(message)
    data object TimeoutError : AiPipelineError("ИИ не ответил вовремя. Проверьте интернет и повторите текущий шаг.")
    data object VisionUnsupported : AiPipelineError("Выбранная модель не поддерживает анализ изображений. Выберите мультимодальную модель.")
}

open class AiPipelineException(val pipelineError: AiPipelineError) : IllegalStateException(pipelineError.userMessage)

/** Converts every throwable received by a ViewModel to a message suitable for ordinary users. */
fun Throwable.aiUserMessage(fallback: String): String =
    (this as? AiPipelineException)?.pipelineError?.userMessage ?: fallback

fun Throwable.isRepairableAiContentError(): Boolean =
    (this as? AiPipelineException)?.pipelineError in setOf(
        AiPipelineError.EmptyResponse,
        AiPipelineError.JsonNotFound,
        AiPipelineError.MalformedJson,
        AiPipelineError.WrongSchema,
        AiPipelineError.MissingRequiredField,
        AiPipelineError.InvalidNumber,
        AiPipelineError.InvalidRange,
        AiPipelineError.TruncatedResponse,
        AiPipelineError.WrongItemCount,
        AiPipelineError.RenamedConfirmedItem,
        AiPipelineError.DuplicateItem
    )
