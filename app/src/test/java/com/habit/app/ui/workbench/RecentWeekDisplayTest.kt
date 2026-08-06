package com.habit.app.ui.workbench

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentWeekDisplayTest {
    @Test
    fun itemSlotsFollowApprovedCounts() {
        assertEquals(
            RecentWeekDisplay(primaryEmoji = null, secondaryEmoji = null, overflowCount = null),
            buildRecentWeekDisplay(emptyList()),
        )
        assertEquals(
            RecentWeekDisplay(primaryEmoji = "a", secondaryEmoji = null, overflowCount = null),
            buildRecentWeekDisplay(listOf("a")),
        )
        assertEquals(
            RecentWeekDisplay(primaryEmoji = "a", secondaryEmoji = "b", overflowCount = null),
            buildRecentWeekDisplay(listOf("a", "b")),
        )
        assertEquals(
            RecentWeekDisplay(primaryEmoji = "a", secondaryEmoji = null, overflowCount = 2),
            buildRecentWeekDisplay(listOf("a", "b", "c")),
        )
        assertEquals(
            RecentWeekDisplay(primaryEmoji = "1", secondaryEmoji = null, overflowCount = 9),
            buildRecentWeekDisplay((1..10).map(Int::toString)),
        )
    }

    @Test
    fun duplicateEmojiKeysRemainSeparateCompletedItems() {
        assertEquals(
            RecentWeekDisplay(primaryEmoji = "same", secondaryEmoji = "same", overflowCount = null),
            buildRecentWeekDisplay(listOf("same", "same")),
        )
    }
}
