package com.opencalori.app.data.network

/**
 * What to do with a provider's tokens-per-minute rejection: how big the next reservation may
 * be, and whether the retry may happen at once or has to wait the window out.
 */
data class TpmPlan(
    /** max_tokens that fits the window this plan targets. */
    val budget: Int,
    /** True only when the current window still has room right now. */
    val retryImmediately: Boolean
)

/**
 * Reads Groq-style rate-limit bodies ("Limit 8000, Used 6302, Requested 5353") and turns them
 * into a retry plan.
 *
 * Providers with a TPM quota reject the whole reservation (prompt + max_tokens), so a generous
 * budget always 429s there. Shrinking max_tokens to what is still free lets the request go
 * through immediately; when nothing is free, the provider's own "try again in 27.4s" is the
 * only sane wait and the next attempt is sized for a clean window.
 */
object TpmPlanner {

    const val MIN_FITTED_TOKENS = 512
    const val MAX_TOKENS_CAP = 32768

    /** Headroom left for the response envelope when planning a fully reset window. */
    private const val FRESH_WINDOW_RESERVE = 256L

    private val TPM_LINE = Regex("Limit ([0-9]+), (?:Used ([0-9]+), )?Requested ([0-9]+)")

    /**
     * @param body provider error body.
     * @param lastReserved max_tokens of the request that was rejected, used to derive the
     *   prompt size out of the reported "Requested" total.
     * @return null when the body carries no quota numbers at all.
     */
    fun plan(body: String, lastReserved: Int): TpmPlan? {
        val match = TPM_LINE.find(body) ?: return null
        val limit = match.groupValues[1].toLongOrNull() ?: return null
        // Groq's 413 variant ("Request too large ... Limit 8000, Requested 16830") has no Used.
        val used = match.groupValues[2].takeIf { it.isNotBlank() }?.toLongOrNull() ?: 0L
        val requested = match.groupValues[3].takeIf { it.isNotBlank() }?.toLongOrNull()
            ?: match.groupValues[2].toLongOrNull() ?: return null

        val promptTokens = (requested - lastReserved).coerceIn(0L, limit)
        val freeNow = limit - used - promptTokens
        if (freeNow >= MIN_FITTED_TOKENS) {
            return TpmPlan(
                budget = freeNow.coerceAtMost(MAX_TOKENS_CAP.toLong()).toInt(),
                retryImmediately = true
            )
        }
        val freshWindow = limit - promptTokens - FRESH_WINDOW_RESERVE
        return TpmPlan(
            budget = freshWindow.coerceIn(MIN_FITTED_TOKENS.toLong(), MAX_TOKENS_CAP.toLong()).toInt(),
            retryImmediately = false
        )
    }
}
