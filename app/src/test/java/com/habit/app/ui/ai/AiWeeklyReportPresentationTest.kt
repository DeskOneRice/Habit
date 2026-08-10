package com.habit.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
}
