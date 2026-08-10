package com.habit.app.domain.ai

import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun previousCompleteWeek(today: LocalDate): ClosedRange<LocalDate> {
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = currentMonday.minusDays(1)
    return end.minusDays(6)..end
}

fun normalizedChatCompletionsUrl(baseUrl: String, allowInsecureHttp: Boolean): String {
    val uri = URI(baseUrl.trim())
    require(uri.scheme == "https" || uri.scheme == "http") { "仅支持 HTTP 或 HTTPS 地址" }
    require(uri.host != null && uri.userInfo == null && uri.fragment == null) { "API 地址格式不正确" }
    require(uri.rawQuery == null) { "API 地址不能包含查询参数" }
    require(uri.scheme != "http" || allowInsecureHttp) { "HTTP 地址需要明确授权" }
    val normalized = baseUrl.trim().trimEnd('/')
    return if (normalized.endsWith("/chat/completions")) normalized else "$normalized/chat/completions"
}
