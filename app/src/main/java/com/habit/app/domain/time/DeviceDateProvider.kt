package com.habit.app.domain.time

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class DeviceDateSnapshot(
    val today: LocalDate,
    val zoneId: ZoneId,
)

interface DeviceDateProvider {
    fun today(): LocalDate

    val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    fun snapshot(): DeviceDateSnapshot = DeviceDateSnapshot(today(), zoneId)
}

class SystemDeviceDateProvider(
    private val clock: Clock = Clock.systemUTC(),
) : DeviceDateProvider {
    override fun snapshot(): DeviceDateSnapshot {
        val currentZone = ZoneId.systemDefault()
        return DeviceDateSnapshot(
            today = clock.instant().atZone(currentZone).toLocalDate(),
            zoneId = currentZone,
        )
    }

    override fun today(): LocalDate = snapshot().today

    override val zoneId: ZoneId
        get() = snapshot().zoneId
}
