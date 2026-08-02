package com.habit.app.ui.emoji

import com.habit.app.ui.components.habitEmoji
import com.habit.app.data.preferences.updatedRecentEmojiKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiCatalogTest {
    @Test
    fun legacyKeysAndUnicodeValuesRenderWithoutRewriting() {
        assertEquals("📚", habitEmoji("book"))
        assertEquals("🧪", habitEmoji("emoji:🧪"))
        assertEquals("🌱", habitEmoji("unknown-old-value"))
    }

    @Test
    fun chineseSearchAndCategoryFilteringAreStable() {
        assertTrue(
            EmojiCatalog.search("实验", EmojiCategory.STUDY)
                .any { it.emoji == "🧪" },
        )
        assertTrue(EmojiCatalog.options.size in 60..80)
        assertTrue(EmojiCatalog.search("羽毛球").any { it.emoji == "🏸" })
    }

    @Test
    fun customInputAcceptsOneEmojiAndRejectsTextOrMultipleEmoji() {
        assertTrue(isSingleEmoji("🏸"))
        assertTrue(isSingleEmoji("❤️"))
        assertFalse(isSingleEmoji("习惯"))
        assertFalse(isSingleEmoji("7"))
        assertFalse(isSingleEmoji("🏸📚"))
        assertEquals("emoji:🏸", normalizeEmojiKey("  🏸  "))
        assertEquals(null, normalizeEmojiKey("日常"))
    }

    @Test
    fun recentHistoryIsDeduplicatedAndBounded() {
        val old = (1..12).map { "emoji:$it" }
        val updated = updatedRecentEmojiKeys("emoji:5", old)

        assertEquals("emoji:5", updated.first())
        assertEquals(12, updated.size)
        assertEquals(1, updated.count { it == "emoji:5" })
    }

    @Test
    fun everyPickerCategoryHasUniqueStableKeys() {
        EmojiCategory.entries.filterNot { it == EmojiCategory.RECENT }.forEach { category ->
            val keys = EmojiCatalog.search("", category).map(EmojiOption::key)
            assertEquals("duplicate keys in $category", keys.distinct(), keys)
        }
    }

    @Test
    fun hobbyCategoryCanBuildEveryPickerItemKey() {
        val keys = EmojiCatalog.search("", EmojiCategory.HOBBY)
            .map { pickerItemKey(EmojiCategory.HOBBY, it.key) }

        assertEquals(12, keys.size)
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun recentKeysAreNormalizedAndDeduplicatedBeforeRendering() {
        val rows = EmojiCatalog.pickerOptions(
            query = "",
            category = EmojiCategory.RECENT,
            recentKeys = listOf("emoji:⭐", "emoji:⭐"),
        )

        assertEquals(listOf("emoji:⭐"), rows.map(EmojiOption::key))
    }
}
