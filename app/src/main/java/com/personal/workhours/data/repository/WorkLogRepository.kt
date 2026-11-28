package com.personal.workhours.data.repository

import androidx.room.withTransaction
import com.personal.workhours.alarm.AlarmScheduler
import com.personal.workhours.data.local.BreakSessionEntity
import com.personal.workhours.data.local.DayOffOverrideEntity
import com.personal.workhours.data.local.WorkDayEntity
import com.personal.workhours.data.local.WorkHoursDatabase
import com.personal.workhours.data.local.toDomain
import com.personal.workhours.data.local.toEntity
import com.personal.workhours.domain.EditWorkDayValidator
import com.personal.workhours.domain.model.BackupPayload
import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.EditValidationResult
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.WorkDayRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorkLogRepository(
    private val db: WorkHoursDatabase,
    private val alarmReconciler: AlarmScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val autoDeductProvider: suspend () -> Boolean = { false }
) {
    private val workLogDao = db.workLogDao()
    private val scheduleDao = db.scheduleDao()

    fun todayDate(): LocalDate = Instant.ofEpochMilli(clock.millis()).atZone(clock.zone).toLocalDate()

    fun observeWorkDay(workDate: LocalDate): Flow<WorkDayRecord?> {
        return workLogDao.observeWorkDay(workDate.toString()).map { it?.toDomain() }
    }

    fun observeOpenShifts(): Flow<List<WorkDayRecord>> {
        return workLogDao.observeOpenShifts().map { list -> list.map { it.toDomain() } }
    }

    fun observeWorkDaysInRange(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkDayRecord>> {
        return workLogDao.observeWorkDaysInRange(startDate.toString(), endDate.toString())
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun getWorkDay(workDate: LocalDate): WorkDayRecord? {
        return workLogDao.getWorkDay(workDate.toString())?.toDomain()
    }

    suspend fun getAllWorkDays(): List<WorkDayRecord> {
        return workLogDao.getAllWorkDays().map { it.toDomain() }
    }

    suspend fun clockIn(targetDate: LocalDate = todayDate()): Boolean {
        val now = clock.millis()
        val dateStr = targetDate.toString()
        val changed = db.withTransaction {
            val openShifts = workLogDao.getOpenShifts()
            if (openShifts.isNotEmpty()) {
                return@withTransaction false
            }
            val existing = workLogDao.getWorkDay(dateStr)
            if (existing == null) {
                workLogDao.insertWorkDay(
                    WorkDayEntity(
                        workDate = dateStr,
                        clockInEpochMs = now,
                        clockOutEpochMs = null,
                        isDayOff = false,
                        autoDeductBreakMinutes = 0,
                        note = "",
                        updatedAtEpochMs = now
                    )
                )
                scheduleDao.deleteDayOffOverride(dateStr)
                true
            } else if (existing.workDay.clockInEpochMs == null) {
                workLogDao.updateWorkDay(
                    existing.workDay.copy(
                        clockInEpochMs = now,
                        clockOutEpochMs = null,
                        isDayOff = false,
                        updatedAtEpochMs = now
                    )
                )
                scheduleDao.deleteDayOffOverride(dateStr)
                true
            } else {
                false
            }
        }
        if (changed) {
            alarmReconciler.cancelEvent(ReminderEventType.TIME_IN)
            alarmReconciler.reconcileAll()
        }
        return changed
    }

    suspend fun startBreak(targetDate: LocalDate = todayDate()): Boolean {
        val now = clock.millis()
        val dateStr = targetDate.toString()
        val changed = db.withTransaction {
            val day = workLogDao.getWorkDay(dateStr) ?: return@withTransaction false
            if (day.workDay.clockInEpochMs == null || day.workDay.clockOutEpochMs != null) {
                return@withTransaction false
            }
            if (workLogDao.getOpenBreak(day.workDay.id) != null) {
                return@withTransaction false
            }
            workLogDao.insertBreak(
                BreakSessionEntity(
                    workDayId = day.workDay.id,
                    startEpochMs = now,
                    endEpochMs = null
                )
            )
            workLogDao.updateWorkDay(day.workDay.copy(updatedAtEpochMs = now))
            true
        }
        if (changed) {
            alarmReconciler.cancelEvent(ReminderEventType.BREAK_START)
            alarmReconciler.scheduleBreakEnd(now)
        }
        return changed
    }

    suspend fun resumeWork(targetDate: LocalDate = todayDate()): Boolean {
        val now = clock.millis()
        val dateStr = targetDate.toString()
        val changed = db.withTransaction {
            val day = workLogDao.getWorkDay(dateStr) ?: return@withTransaction false
            val openBreak = workLogDao.getOpenBreak(day.workDay.id) ?: return@withTransaction false
            workLogDao.updateBreak(openBreak.copy(endEpochMs = now))
            workLogDao.updateWorkDay(day.workDay.copy(updatedAtEpochMs = now))
            true
        }
        if (changed) {
            alarmReconciler.cancelBreakEnd()
            alarmReconciler.reconcileAll()
        }
        return changed
    }

    suspend fun clockOut(targetDate: LocalDate = todayDate()): Boolean {
        val now = clock.millis()
        val dateStr = targetDate.toString()
        val shouldAutoDeduct = autoDeductProvider()
        val changed = db.withTransaction {
            val day = workLogDao.getWorkDay(dateStr) ?: return@withTransaction false
            if (day.workDay.clockInEpochMs == null || day.workDay.clockOutEpochMs != null) {
                return@withTransaction false
            }
            workLogDao.closeOpenBreaks(day.workDay.id, now)
            val refreshed = workLogDao.getWorkDay(dateStr) ?: day
            val autoDeductMinutes = if (refreshed.breaks.isEmpty() && shouldAutoDeduct) {
                scheduleDao.getScheduleForDay(targetDate.dayOfWeek.value)?.defaultBreakMinutes ?: 60
            } else {
                0
            }
            workLogDao.updateWorkDay(
                refreshed.workDay.copy(
                    clockOutEpochMs = now,
                    autoDeductBreakMinutes = autoDeductMinutes,
                    updatedAtEpochMs = now
                )
            )
            true
        }
        if (changed) {
            alarmReconciler.cancelBreakEnd()
            alarmReconciler.cancelEvent(ReminderEventType.TIME_OUT)
            alarmReconciler.reconcileAll()
        }
        return changed
    }

    suspend fun skipToday(targetDate: LocalDate = todayDate(), reason: String = "Skipped"): Boolean {
        val now = clock.millis()
        val dateStr = targetDate.toString()
        db.withTransaction {
            scheduleDao.upsertDayOffOverride(DayOffOverrideEntity(workDate = dateStr, reason = reason))
            val existing = workLogDao.getWorkDay(dateStr)
            if (existing == null) {
                workLogDao.insertWorkDay(
                    WorkDayEntity(
                        workDate = dateStr,
                        clockInEpochMs = null,
                        clockOutEpochMs = null,
                        isDayOff = true,
                        note = reason,
                        updatedAtEpochMs = now
                    )
                )
            } else if (existing.workDay.clockInEpochMs == null) {
                workLogDao.updateWorkDay(
                    existing.workDay.copy(
                        isDayOff = true,
                        updatedAtEpochMs = now
                    )
                )
            }
        }
        alarmReconciler.cancelBreakEnd()
        alarmReconciler.cancelEvent(ReminderEventType.BREAK_START)
        alarmReconciler.cancelEvent(ReminderEventType.TIME_IN)
        alarmReconciler.cancelEvent(ReminderEventType.TIME_OUT)
        alarmReconciler.reconcileAll()
        return true
    }

    suspend fun unskipToday(targetDate: LocalDate = todayDate()) {
        val now = clock.millis()
        val dateStr = targetDate.toString()
        db.withTransaction {
            scheduleDao.deleteDayOffOverride(dateStr)
            val existing = workLogDao.getWorkDay(dateStr)
            if (existing != null && existing.workDay.isDayOff) {
                if (existing.workDay.clockInEpochMs == null && existing.workDay.note == "Skipped") {
                    workLogDao.deleteWorkDayByDate(dateStr)
                } else {
                    workLogDao.updateWorkDay(
                        existing.workDay.copy(
                            isDayOff = false,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }
        }
        alarmReconciler.reconcileAll()
    }

    suspend fun saveManualEdit(
        workDate: LocalDate,
        isDayOff: Boolean,
        clockInEpochMs: Long?,
        clockOutEpochMs: Long?,
        breaks: List<BreakRecord>,
        autoDeductBreakMinutes: Int,
        note: String
    ): EditValidationResult {
        val validation = EditWorkDayValidator.validate(
            isDayOff = isDayOff,
            clockInEpochMs = clockInEpochMs,
            clockOutEpochMs = clockOutEpochMs,
            breaks = breaks,
            note = note
        )
        if (validation is EditValidationResult.Invalid) {
            return validation
        }

        val now = clock.millis()
        val dateStr = workDate.toString()
        db.withTransaction {
            if (isDayOff) {
                scheduleDao.upsertDayOffOverride(DayOffOverrideEntity(workDate = dateStr, reason = note.ifBlank { "Day Off" }))
            } else {
                scheduleDao.deleteDayOffOverride(dateStr)
            }

            val existing = workLogDao.getWorkDay(dateStr)
            val dayId = if (existing == null) {
                workLogDao.insertWorkDay(
                    WorkDayEntity(
                        workDate = dateStr,
                        clockInEpochMs = if (isDayOff) null else clockInEpochMs,
                        clockOutEpochMs = if (isDayOff) null else clockOutEpochMs,
                        isDayOff = isDayOff,
                        autoDeductBreakMinutes = if (isDayOff || breaks.isNotEmpty()) 0 else autoDeductBreakMinutes,
                        note = note.trim(),
                        updatedAtEpochMs = now
                    )
                )
            } else {
                workLogDao.updateWorkDay(
                    existing.workDay.copy(
                        clockInEpochMs = if (isDayOff) null else clockInEpochMs,
                        clockOutEpochMs = if (isDayOff) null else clockOutEpochMs,
                        isDayOff = isDayOff,
                        autoDeductBreakMinutes = if (isDayOff || breaks.isNotEmpty()) 0 else autoDeductBreakMinutes,
                        note = note.trim(),
                        updatedAtEpochMs = now
                    )
                )
                workLogDao.deleteBreaksForWorkDay(existing.workDay.id)
                existing.workDay.id
            }

            if (!isDayOff) {
                for (brk in breaks) {
                    workLogDao.insertBreak(
                        BreakSessionEntity(
                            workDayId = dayId,
                            startEpochMs = brk.startEpochMs,
                            endEpochMs = brk.endEpochMs
                        )
                    )
                }
            }
        }
        alarmReconciler.reconcileAll()
        return EditValidationResult.Valid
    }

    suspend fun deleteDay(workDate: LocalDate) {
        db.withTransaction {
            workLogDao.deleteWorkDayByDate(workDate.toString())
            scheduleDao.deleteDayOffOverride(workDate.toString())
        }
        alarmReconciler.reconcileAll()
    }

    suspend fun restoreWorkDay(record: WorkDayRecord) {
        val now = clock.millis()
        db.withTransaction {
            if (record.isDayOff) {
                scheduleDao.upsertDayOffOverride(
                    DayOffOverrideEntity(
                        workDate = record.workDate,
                        reason = record.note.ifBlank { "Day Off" }
                    )
                )
            } else {
                scheduleDao.deleteDayOffOverride(record.workDate)
            }
            workLogDao.deleteWorkDayByDate(record.workDate)
            val insertedId = workLogDao.insertWorkDay(
                WorkDayEntity(
                    workDate = record.workDate,
                    clockInEpochMs = record.clockInEpochMs,
                    clockOutEpochMs = record.clockOutEpochMs,
                    isDayOff = record.isDayOff,
                    autoDeductBreakMinutes = record.autoDeductBreakMinutes,
                    note = record.note,
                    updatedAtEpochMs = now
                )
            )
            for (brk in record.breaks) {
                workLogDao.insertBreak(
                    BreakSessionEntity(
                        workDayId = insertedId,
                        startEpochMs = brk.startEpochMs,
                        endEpochMs = brk.endEpochMs
                    )
                )
            }
        }
        alarmReconciler.reconcileAll()
    }

    suspend fun exportAllSnapshot(): BackupPayload {
        return db.withTransaction {
            BackupPayload(
                schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
                exportedAtEpochMs = clock.millis(),
                workDays = workLogDao.getAllWorkDays().map { it.toDomain() },
                daySchedules = scheduleDao.getDaySchedules().map { it.toDomain() },
                reminderConfigs = scheduleDao.getReminderConfigs().map { it.toDomain() },
                dayOffOverrides = scheduleDao.getDayOffOverrides().map { it.toDomain() }
            )
        }
    }

    suspend fun restoreSnapshot(payload: BackupPayload) {
        db.withTransaction {
            workLogDao.clearAllWorkDays()
            scheduleDao.clearDaySchedules()
            scheduleDao.clearReminderConfigs()
            scheduleDao.clearDayOffOverrides()

            if (payload.daySchedules.isNotEmpty()) {
                scheduleDao.upsertDaySchedules(payload.daySchedules.map { it.toEntity() })
            }
            if (payload.reminderConfigs.isNotEmpty()) {
                scheduleDao.upsertReminderConfigs(payload.reminderConfigs.map { it.toEntity() })
            }
            if (payload.dayOffOverrides.isNotEmpty()) {
                scheduleDao.upsertDayOffOverrides(payload.dayOffOverrides.map { it.toEntity() })
            }
            for (day in payload.workDays) {
                val insertedId = workLogDao.insertWorkDay(
                    WorkDayEntity(
                        workDate = day.workDate,
                        clockInEpochMs = day.clockInEpochMs,
                        clockOutEpochMs = day.clockOutEpochMs,
                        isDayOff = day.isDayOff,
                        autoDeductBreakMinutes = day.autoDeductBreakMinutes,
                        note = day.note,
                        updatedAtEpochMs = day.updatedAtEpochMs
                    )
                )
                for (brk in day.breaks) {
                    workLogDao.insertBreak(
                        BreakSessionEntity(
                            workDayId = insertedId,
                            startEpochMs = brk.startEpochMs,
                            endEpochMs = brk.endEpochMs
                        )
                    )
                }
            }
        }
        db.ensureDefaultSeedData()
        alarmReconciler.reconcileAll()
    }
}
