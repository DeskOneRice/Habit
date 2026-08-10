package com.habit.app.data.repository

import androidx.room.withTransaction
import com.habit.app.data.local.HabitDatabase
import com.habit.app.data.local.toDomain
import com.habit.app.data.local.toEntity
import com.habit.app.domain.model.AiWeeklyReport
import com.habit.app.domain.repository.AiWeeklyReportRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAiWeeklyReportRepository(
    private val database: HabitDatabase,
    private val clock: Clock,
) : AiWeeklyReportRepository {
    private val dao = database.aiDao()

    override fun observeAll(): Flow<List<AiWeeklyReport>> = dao.observeReports().map { rows ->
        rows.map { it.toDomain() }
    }

    override fun observeWeek(startEpochDay: Long): Flow<AiWeeklyReport?> =
        dao.observeReport(startEpochDay).map { it?.toDomain() }

    override suspend fun save(report: AiWeeklyReport): Long = database.withTransaction {
        val now = clock.millis()
        val existing = dao.getReport(report.startEpochDay)
        val entity = report.toEntity(
            id = existing?.id ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (existing == null) dao.insertReport(entity) else {
            dao.updateReport(entity)
            existing.id
        }
    }

    override suspend fun delete(id: Long) {
        dao.deleteReport(id)
    }
}
