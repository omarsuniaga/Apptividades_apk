package com.example.data

import com.example.data.remote.GroqChatRequestDto
import com.example.data.remote.GroqMessageDto
import com.example.domain.model.GroqModelOption
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqModelsAndRequestTest {

    private val adapter = Moshi.Builder().build().adapter(GroqChatRequestDto::class.java)

    @Test
    fun retiredLlamaModelIdsFallBackToDefaultGptOss() {
        assertEquals(GroqModelOption.GPT_OSS_120B, GroqModelOption.fromId("llama-3.3-70b-versatile"))
        assertEquals(GroqModelOption.GPT_OSS_120B, GroqModelOption.fromId("llama-3.1-8b-instant"))
        assertEquals(GroqModelOption.GPT_OSS_120B, GroqModelOption.fromId(null))
        assertEquals(GroqModelOption.GPT_OSS_20B, GroqModelOption.fromId("openai/gpt-oss-20b"))
    }

    @Test
    fun reasoningEffortIsSerializedOnlyWhenSet() {
        val msgs = listOf(GroqMessageDto("user", "hola"))
        val withEffort = adapter.toJson(
            GroqChatRequestDto(model = "openai/gpt-oss-120b", messages = msgs, reasoningEffort = "low")
        )
        val without = adapter.toJson(GroqChatRequestDto(model = "x", messages = msgs))
        assertTrue(withEffort.contains("\"reasoning_effort\":\"low\""))
        assertFalse(without.contains("reasoning_effort"))
        assertTrue(without.contains("\"max_completion_tokens\":3000"))
    }

    @Test
    fun allSelectableModelsSupportReasoningEffort() {
        assertTrue(GroqModelOption.entries.all { it.supportsReasoningEffort })
    }
}
