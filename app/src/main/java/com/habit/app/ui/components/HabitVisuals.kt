package com.habit.app.ui.components

private val legacyEmoji = mapOf(
    "book" to "📚",
    "sprout" to "🌱",
    "run" to "🏃",
    "heart" to "💛",
    "water" to "💧",
    "star" to "⭐",
)

fun habitEmoji(iconKey: String): String = legacyEmoji[iconKey]
    ?: iconKey.removePrefix("emoji:")
        .takeIf { iconKey.startsWith("emoji:") && it.isNotBlank() }
    ?: "🌱"
