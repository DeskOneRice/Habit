package com.habit.app.data.repository

import com.habit.app.data.local.CheckInDao
import com.habit.app.data.local.CheckInEntity
import com.habit.app.data.local.HabitDao
import com.habit.app.data.local.HabitEntity
import com.habit.app.data.local.toDomain
import com.habit.app.domain.model.CalendarMark
import com.habit.app.domain.model.DayHabit
import com.habit.app.domain.model.DaySnapshot
import com.habit.app.domain.model.Habit
import com.habit.app.domain.model.HabitHistorySnapshot
import com.habit.app.domain.model.MonthSnapshot
import com.habit.app.domain.repository.CalendarRepository
import com.habit.app.domain.stats.DatePolicy
import com.habit.app.domain.stats.HabitStatistics
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomCalendarRepository(
    private val habitDao: HabitDao,
    private val checkInDao: CheckInDao,
) : CalendarRepository {
    override fun observeMonth(month: YearMonth, today: LocalDate): Flow<MonthSnapshot> {
        val start = month.atDay(1).toEpochDay()
        val end = month.atEndOfMonth().toEpochDay()
        return combine(habitDao.observeAll(), checkInDao.observeRange(start, end)) {
                habitEntities,
                checkIns,
            ->
            val habits = habitEntities.toDomainSorted()
            val habitsById = habits.associateBy(Habit::id)
            val completedByHabit = checkIns
                .groupBy(CheckInEntity::habitId)
                .mapValues { (_, entries) -> entries.mapTo(mutableSetOf(), CheckInEntity::checkInEpochDay) }
            val marksByEpochDay = checkIns
                .mapNotNull { checkIn ->
                    val habit = habitsById[checkIn.habitId] ?: return@mapNotNull null
                    val date = LocalDate.ofEpochDay(checkIn.checkInEpochDay)
                    if (!DatePolicy.isEligible(habit, date, today)) return@mapNotNull null
                    checkIn.checkInEpochDay to CalendarMark(habit.iconKey, habit.id, habit.sortOrder)
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, marks) -> marks.sortedBy(CalendarMark::sortOrder) }

            MonthSnapshot(
                month = month,
                marksByEpochDay = marksByEpochDay,
                stats = HabitStatistics.forMonth(month, habits, completedByHabit, today),
            )
        }
    }

    override fun observeDay(date: LocalDate, today: LocalDate): Flow<DaySnapshot> =
        combine(
            habitDao.observeAll(),
            checkInDao.observeRange(date.toEpochDay(), date.toEpochDay()),
        ) { habitEntities, checkIns ->
            val byHabit = checkIns.associateBy(CheckInEntity::habitId)
            val epochDay = date.toEpochDay()
            val habits = habitEntities
                .toDomainSorted()
                .filter { habit ->
                    epochDay >= habit.startEpochDay &&
                        epochDay < (habit.archivedEpochDay ?: Long.MAX_VALUE)
                }
                .map { habit ->
                    val checkIn = byHabit[habit.id]
                    DayHabit(habit, checkIn != null, checkIn?.createdAt)
                }
            DaySnapshot(date.toEpochDay(), habits)
        }

    override fun observeHabitHistory(
        habitId: Long,
        today: LocalDate,
    ): Flow<HabitHistorySnapshot> = combine(
        habitDao.observeById(habitId),
        checkInDao.observeForHabit(habitId),
    ) { habitEntity, checkIns ->
        val habit = requireNotNull(habitEntity) { "Habit $habitId does not exist." }.toDomain()
        val completedEpochDays = checkIns
            .asSequence()
            .map(CheckInEntity::checkInEpochDay)
            .filter { epochDay -> DatePolicy.isEligible(habit, LocalDate.ofEpochDay(epochDay), today) }
            .toSet()
        HabitHistorySnapshot(
            habit = habit,
            completedEpochDays = completedEpochDays,
            stats = HabitStatistics.forHabit(completedEpochDays, today),
        )
    }

    private fun List<HabitEntity>.toDomainSorted(): List<Habit> =
        map(HabitEntity::toDomain).sortedBy(Habit::sortOrder)
}
