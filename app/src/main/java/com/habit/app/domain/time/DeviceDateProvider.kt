package com.habit.app.domain.time

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

interface DeviceDateProvider {
    fun today(): LocalDate

    val zoneId: ZoneId
        get() = ZoneId.systemDefault()
}

class SystemDeviceDateProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
) : DeviceDateProvider {
    override fun today(): LocalDate = LocalDate.now(clock)

    override val zoneId: ZoneId = clock.zone
}
