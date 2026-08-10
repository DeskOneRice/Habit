package com.habit.app.domain.ai

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AiPoliciesTest {
    @Test
    fun previousCompleteWeekUsesBeijingMondayThroughSunday() {
        val range = previousCompleteWeek(LocalDate.of(2026, 8, 10))

        assertEquals(LocalDate.of(2026, 8, 3), range.start)
        assertEquals(LocalDate.of(2026, 8, 9), range.endInclusive)
    }

    @Test
    fun baseV1UrlAppendsChatCompletionsExactlyOnce() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            normalizedChatCompletionsUrl("https://api.example.com/v1/", false),
        )
    }

    @Test
    fun existingChatCompletionsPathIsNotAppendedTwice() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            normalizedChatCompletionsUrl("https://api.example.com/v1/chat/completions", false),
        )
    }

    @Test
    fun queryBearingUrlIsRejected() {
        try {
            normalizedChatCompletionsUrl("https://api.example.com/v1?version=1", false)
            fail("Expected query-bearing URL to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun httpRequiresExplicitPerModelConsent() {
        try {
            normalizedChatCompletionsUrl("http://192.168.1.2:11434/v1", false)
            fail("Expected insecure HTTP to require consent")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(
            "http://192.168.1.2:11434/v1/chat/completions",
            normalizedChatCompletionsUrl("http://192.168.1.2:11434/v1", true),
        )
    }
}
