package com.opencalori.app.data.network.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Provider dialects for the answer payload: string, multipart array, reasoning-only. */
class ChatCompletionResponseTest {

    private fun lenientJson() =
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `plain string content is returned as is`() {
        val response = lenientJson().decodeFromString<ChatCompletionResponse>(
            """{"choices":[{"message":{"role":"assistant","content":"{\"dishes\":[]}"}}]}"""
        )
        assertEquals("{\"dishes\":[]}", response.firstText)
    }

    @Test
    fun `array content parts are flattened`() {
        val response = lenientJson().decodeFromString<ChatCompletionResponse>(
            """{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"{\"dishes\":"},{"type":"text","text":"[]}"}]}}]}"""
        )
        assertEquals("{\"dishes\":\n[]}", response.firstText)
    }

    @Test
    fun `blank content falls back to reasoning content`() {
        val response = lenientJson().decodeFromString<ChatCompletionResponse>(
            """{"choices":[{"message":{"role":"assistant","content":"","reasoning_content":"{\"dishes\":[]}"}}]}"""
        )
        assertEquals("{\"dishes\":[]}", response.firstText)
    }

    @Test
    fun `finish_reason length is surfaced as truncated`() {
        val response = lenientJson().decodeFromString<ChatCompletionResponse>(
            """{"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"","reasoning_content":""}}]}"""
        )
        assertNull(response.firstText)
        assertEquals(true, response.truncated)
        val complete = lenientJson().decodeFromString<ChatCompletionResponse>(
            """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok"}}]}"""
        )
        assertEquals(false, complete.truncated)
    }

    @Test
    fun `missing content and reasoning yields null`() {
        val response = lenientJson().decodeFromString<ChatCompletionResponse>(
            """{"choices":[{"message":{"role":"assistant"}}]}"""
        )
        assertNull(response.firstText)
    }
}
