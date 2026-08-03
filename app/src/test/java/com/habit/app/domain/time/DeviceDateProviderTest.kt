package com.habit.app.domain.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDateProviderTest {
    @Test
    fun systemProviderAlwaysUsesBeijingTime() {
        val original = TimeZone.getDefault()
        try {
            val instant = Instant.parse("2031-02-03T16:30:00Z")
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val provider = SystemDeviceDateProvider(Clock.fixed(instant, ZoneOffset.UTC))
            assertEquals(LocalDate.of(2031, 2, 4), provider.today())
            assertEquals(ZoneId.of("Asia/Shanghai"), provider.zoneId)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

            assertEquals(LocalDate.of(2031, 2, 4), provider.today())
            assertEquals(ZoneId.of("Asia/Shanghai"), provider.zoneId)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
