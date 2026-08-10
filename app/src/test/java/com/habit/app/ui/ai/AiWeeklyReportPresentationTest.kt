package com.habit.app.ui.ai

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWeeklyReportPresentationTest {
    @Test
    fun exactlyThreeSuggestionsStripCommonNumberPrefixes() {
        val presentation = presentWeeklySuggestions(
            listOf("1. 早点休息", "2、继续记录", "三、观察变化"),
        ) as WeeklySuggestionPresentation.Valid

        assertEquals(listOf("早点休息", "继续记录", "观察变化"), presentation.items)
    }

    @Test
    fun parenthesisAndChinesePrefixesAreNormalizedWithoutInventingItems() {
        val presentation = presentWeeklySuggestions(
            listOf("1) 第一条", "二、第二条", "3. 第三条"),
        ) as WeeklySuggestionPresentation.Valid

        assertEquals(listOf("第一条", "第二条", "第三条"), presentation.items)
    }

    @Test
    fun zeroTwoOrFourSuggestionsAreIncomplete() {
        assertSame(WeeklySuggestionPresentation.Incomplete, presentWeeklySuggestions(emptyList()))
        assertSame(WeeklySuggestionPresentation.Incomplete, presentWeeklySuggestions(listOf("一", "二")))
        assertSame(WeeklySuggestionPresentation.Incomplete, presentWeeklySuggestions(listOf("一", "二", "三", "四")))
    }

    @Test
    fun exactlyThreeSuggestionsWithABlankNormalizedItemAreIncomplete() {
        assertSame(
            WeeklySuggestionPresentation.Incomplete,
            presentWeeklySuggestions(listOf("1. 第一条", "2、   ", "三、第三条")),
        )
    }

    @Test
    fun bracketFullWidthAndCircledPrefixesMatchTheirItemIndex() {
        val presentation = presentWeeklySuggestions(
            listOf("(1) 第一条", "（2） 第二条", "③ 第三条"),
        ) as WeeklySuggestionPresentation.Valid

        assertEquals(listOf("第一条", "第二条", "第三条"), presentation.items)
    }

    @Test
    fun decimalAndPrefixForAnotherItemArePreserved() {
        val presentation = presentWeeklySuggestions(
            listOf("1.5 公里慢跑", "1. 不应剥离错误序号", "3）第三条"),
        ) as WeeklySuggestionPresentation.Valid

        assertEquals(listOf("1.5 公里慢跑", "1. 不应剥离错误序号", "第三条"), presentation.items)
    }

    @Test
    fun onlyPreviousCompleteWeekCanBeRegenerated() {
        val today = LocalDate.of(2026, 8, 11)

        assertTrue(isGeneratableWeeklyReportWeek(LocalDate.of(2026, 8, 3).toEpochDay(), today))
        assertFalse(isGeneratableWeeklyReportWeek(LocalDate.of(2026, 7, 27).toEpochDay(), today))
    }
}
