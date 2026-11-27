package com.personal.workhours.data.repository

import androidx.room.withTransaction
import com.personal.workhours.data.local.DayOffOverrideEntity
import com.personal.workhours.data.local.WorkHoursDatabase
import com.personal.workhours.data.local.toDomain
import com.personal.workhours.data.local.toEntity
import com.personal.workhours.domain.model.DayOffOverride
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ScheduleRepository(
    private val db: WorkHoursDatabase,
    private val alarmReconciler: AlarmReconciler
) {
    private val scheduleDao = db.scheduleDao()

    fun observeDaySchedules(): Flow<Map<Int, DaySchedule>> {
        return scheduleDao.observeDaySchedules().map { list ->
            list.associate { it.dayOfWeek to it.toDomain() }
        }
    }

    fun observeReminderConfigs(): Flow<Map<ReminderEventType, ReminderConfig>> {
        return scheduleDao.observeReminderConfigs().map { list ->
            list.map { it.toDomain() }.associateBy { it.eventType }
        }
    }

    fun observeDayOffOverrides(): Flow<List<DayOffOverride>> {
        return scheduleDao.observeDayOffOverrides().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getDaySchedules(): Map<Int, DaySchedule> {
        return scheduleDao.getDaySchedules().associate { it.dayOfWeek to it.toDomain() }
    }

    suspend fun getReminderConfigs(): Map<ReminderEventType, ReminderConfig> {
        return scheduleDao.getReminderConfigs().map { it.toDomain() }.associateBy { it.eventType }
    }

    suspend fun getDayOffOverrides(): Set<String> {
        return scheduleDao.getDayOffOverrides().map { it.workDate }.toSet()
    }

    suspend fun saveDaySchedule(schedule: DaySchedule) {
        scheduleDao.upsertDaySchedule(schedule.toEntity())
        alarmReconciler.reconcileAll()
    }

    suspend fun applyDefaultShiftToWorkdays(
        workDaysOfWeek: Set<Int>,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        defaultBreakMinutes: Int,
        targetWorkMinutes: Int,
        breakStartMinuteOfDay: Int? = 12 * 60
    ) {
        db.withTransaction {
            val updated = (1..7).map { dow ->
                DaySchedule(
                    dayOfWeek = dow,
                    isWorkDay = dow in workDaysOfWeek,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    defaultBreakMinutes = defaultBreakMinutes,
                    targetWorkMinutes = targetWorkMinutes,
                    breakStartMinuteOfDay = breakStartMinuteOfDay
                ).toEntity()
            }
            scheduleDao.upsertDaySchedules(updated)
        }
        alarmReconciler.reconcileAll()
    }

    suspend fun saveReminderConfig(config: ReminderConfig) {
        scheduleDao.upsertReminderConfig(config.toEntity())
        alarmReconciler.reconcileAll()
    }

    suspend fun addDayOffOverride(date: LocalDate, reason: String) {
        scheduleDao.upsertDayOffOverride(
            DayOffOverrideEntity(
                workDate = date.toString(),
                reason = reason.trim().ifEmpty { "Holiday / Day Off" }
            )
        )
        alarmReconciler.reconcileAll()
    }

    suspend fun removeDayOffOverride(workDate: String) {
        scheduleDao.deleteDayOffOverride(workDate)
        alarmReconciler.reconcileAll()
    }
}
