package com.habit.app.ui.components

fun habitEmoji(iconKey: String): String = when (iconKey) {
    "book" -> "📚"
    "sprout" -> "🌱"
    "run" -> "🏃"
    "heart" -> "💛"
    "water" -> "💧"
    "star" -> "⭐"
    else -> "🌱"
}
