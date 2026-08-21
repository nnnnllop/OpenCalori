package com.opencalori.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiErrorMessagesTest {

    // ---- Retry policy ----

    @Test
    fun `rate limits and server errors are worth retrying`() {
        assertTrue(ApiErrorMessages.isTransient(429))
        assertTrue(ApiErrorMessages.isTransient(500))
        assertTrue(ApiErrorMessages.isTransient(503))
        assertTrue(ApiErrorMessages.isTransient(408))
    }

    @Test
    fun `client errors are not retried`() {
        assertFalse(ApiErrorMessages.isTransient(400))
        assertFalse(ApiErrorMessages.isTransient(401))
        assertFalse(ApiErrorMessages.isTransient(404))
    }

    // ---- Human-readable messages ----

    @Test
    fun `auth failure explains the key rather than the status code`() {
        val message = ApiErrorMessages.forHttp(401, "")
        assertTrue(message.contains("ключ"))
    }

    @Test
    fun `404 points at the base url`() {
        assertTrue(ApiErrorMessages.forHttp(404, "").contains("Base URL"))
    }

    @Test
    fun `429 mentions limits`() {
        assertTrue(ApiErrorMessages.forHttp(429, "").contains("лимит"))
    }

    @Test
    fun `raw json body never reaches the user verbatim`() {
        val body = "{\"error\":{\"message\":\"Rate limit reached\",\"type\":\"rate_limit_exceeded\"}}"
        val message = ApiErrorMessages.forHttp(429, body)
        assertFalse(message.contains("rate_limit_exceeded"))
        assertTrue(message.contains("Rate limit reached"))
    }

    @Test
    fun `provider message is extracted from the nested error object`() {
        assertEquals(
            "Model not found",
            ApiErrorMessages.providerMessage("{\"error\":{\"message\":\"Model not found\"}}")
        )
    }

    @Test
    fun `provider message is extracted from a flat body`() {
        assertEquals(
            "Invalid model",
            ApiErrorMessages.providerMessage("{\"message\":\"Invalid model\"}")
        )
    }

    @Test
    fun `non-json bodies yield no provider message`() {
        assertNull(ApiErrorMessages.providerMessage("<html>502 Bad Gateway</html>"))
        assertNull(ApiErrorMessages.providerMessage(""))
    }

    // ---- Network failures ----

    @Test
    fun `timeouts are phrased as a slow provider`() {
        val message = ApiErrorMessages.forNetwork(SocketTimeoutException("timeout"))
        assertTrue(message.contains("вовремя"))
    }

    @Test
    fun `dns failures point at the base url`() {
        val message = ApiErrorMessages.forNetwork(UnknownHostException("Unable to resolve host api.foo"))
        assertTrue(message.contains("Base URL"))
    }

    @Test
    fun `unknown network errors still say something useful`() {
        val message = ApiErrorMessages.forNetwork(IOException("broken pipe"))
        assertTrue(message.contains("соединения"))
    }

    // ---- Vision detection ----

    @Test
    fun `explicit vision rejection is recognised`() {
        assertTrue(
            ApiErrorMessages.looksLikeMissingVision(
                400,
                "{\"error\":{\"message\":\"This model does not support image input\"}}"
            )
        )
    }

    @Test
    fun `a wrong model id is not mistaken for a vision problem`() {
        // The old heuristic matched the bare word "invalid" and blamed vision for typos.
        assertFalse(
            ApiErrorMessages.looksLikeMissingVision(
                404,
                "{\"error\":{\"message\":\"The model gpt-4oo does not exist\"}}"
            )
        )
    }

    @Test
    fun `auth and rate limit errors are never vision problems`() {
        assertFalse(ApiErrorMessages.looksLikeMissingVision(401, "image not supported"))
        assertFalse(ApiErrorMessages.looksLikeMissingVision(429, "image not supported"))
        assertFalse(ApiErrorMessages.looksLikeMissingVision(503, "image not supported"))
    }

    @Test
    fun `openai o-series max_tokens rejection is detected`() {
        assertEquals(
            setOf("max_tokens"),
            ApiErrorMessages.unsupportedParameterHints(
                "Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead."
            )
        )
    }

    @Test
    fun `response_format and temperature rejections are detected`() {
        assertEquals(
            setOf("response_format"),
            ApiErrorMessages.unsupportedParameterHints(
                "{\"error\":{\"message\":\"response_format is not supported for this model\"}}"
            )
        )
        assertEquals(
            setOf("temperature"),
            ApiErrorMessages.unsupportedParameterHints(
                "{\"error\":{\"message\":\"temperature does not support 0.2 with this model\"}}"
            )
        )
    }

    @Test
    fun `groq json validation failure disables json mode`() {
        assertEquals(
            setOf("response_format"),
            ApiErrorMessages.unsupportedParameterHints(
                "{\"error\":{\"message\":\"Failed to validate JSON. Please adjust your prompt. See 'failed_generation' for more details.\",\"failed_generation\":\"...\"}}"
            )
        )
    }

    @Test
    fun `groq max_tokens ceiling is parsed and clamped`() {
        assertEquals(
            16384,
            ApiErrorMessages.maxTokensCeiling(
                "`max_tokens` must be less than or equal to `16384`, the maximum value for `max_tokens` is less than the `context_window` for this model"
            )
        )
    }

    @Test
    fun `field rejection is not mistaken for a ceiling`() {
        assertNull(
            ApiErrorMessages.maxTokensCeiling(
                "Unsupported parameter: 'max_tokens' is not supported with this model."
            )
        )
    }

    @Test
    fun `unrelated 400 bodies produce no hints`() {
        assertTrue(ApiErrorMessages.unsupportedParameterHints("invalid request body").isEmpty())
        assertTrue(ApiErrorMessages.unsupportedParameterHints("").isEmpty())
    }
}
