package com.personal.workhours.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DayOffOverride
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.EscalationMode
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.VibrationPatternType
import com.personal.workhours.domain.model.WorkDayRecord

@Entity(
    tableName = "work_days",
    indices = [Index(value = ["workDate"], unique = true)]
)
data class WorkDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDate: String,
    val clockInEpochMs: Long? = null,
    val clockOutEpochMs: Long? = null,
    val isDayOff: Boolean = false,
    val autoDeductBreakMinutes: Int = 0,
    val note: String = "",
    val updatedAtEpochMs: Long = 0L
)

@Entity(
    tableName = "break_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workDayId"])]
)
data class BreakSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workDayId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long? = null
)

data class WorkDayWithBreaks(
    @Embedded val workDay: WorkDayEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workDayId"
    )
    val breaks: List<BreakSessionEntity>
)

@Entity(tableName = "day_schedules")
data class DayScheduleEntity(
    @PrimaryKey val dayOfWeek: Int,
    val isWorkDay: Boolean,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val defaultBreakMinutes: Int,
    val targetWorkMinutes: Int,
    val breakStartMinuteOfDay: Int? = 12 * 60
)

@Entity(tableName = "reminder_configs")
data class ReminderConfigEntity(
    @PrimaryKey val eventType: String,
    val enabled: Boolean,
    val stage1LeadMinutes: Int,
    val stage2Enabled: Boolean,
    val stage2DelayMinutes: Int,
    val stage2Mode: String,
    val vibrationPattern: String,
    val soundUri: String?,
    val snoozeMinutes: Int
)

@Entity(tableName = "day_off_overrides")
data class DayOffOverrideEntity(
    @PrimaryKey val workDate: String,
    val reason: String
)

fun BreakSessionEntity.toDomain(): BreakRecord = BreakRecord(
    id = id,
    workDayId = workDayId,
    startEpochMs = startEpochMs,
    endEpochMs = endEpochMs
)

fun WorkDayWithBreaks.toDomain(): WorkDayRecord = WorkDayRecord(
    id = workDay.id,
    workDate = workDay.workDate,
    clockInEpochMs = workDay.clockInEpochMs,
    clockOutEpochMs = workDay.clockOutEpochMs,
    isDayOff = workDay.isDayOff,
    autoDeductBreakMinutes = workDay.autoDeductBreakMinutes,
    note = workDay.note,
    updatedAtEpochMs = workDay.updatedAtEpochMs,
    breaks = breaks.sortedBy { it.startEpochMs }.map { it.toDomain() }
)

fun DayScheduleEntity.toDomain(): DaySchedule = DaySchedule(
    dayOfWeek = dayOfWeek,
    isWorkDay = isWorkDay,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    defaultBreakMinutes = defaultBreakMinutes,
    targetWorkMinutes = targetWorkMinutes,
    breakStartMinuteOfDay = breakStartMinuteOfDay
)

fun DaySchedule.toEntity(): DayScheduleEntity = DayScheduleEntity(
    dayOfWeek = dayOfWeek,
    isWorkDay = isWorkDay,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    defaultBreakMinutes = defaultBreakMinutes,
    targetWorkMinutes = targetWorkMinutes,
    breakStartMinuteOfDay = breakStartMinuteOfDay
)

fun ReminderConfigEntity.toDomain(): ReminderConfig = ReminderConfig(
    eventType = runCatching { ReminderEventType.valueOf(eventType) }.getOrDefault(ReminderEventType.TIME_IN),
    enabled = enabled,
    stage1LeadMinutes = stage1LeadMinutes,
    stage2Enabled = stage2Enabled,
    stage2DelayMinutes = stage2DelayMinutes,
    stage2Mode = runCatching { EscalationMode.valueOf(stage2Mode) }.getOrDefault(EscalationMode.FULL_ALARM),
    vibrationPattern = runCatching { VibrationPatternType.valueOf(vibrationPattern) }.getOrDefault(VibrationPatternType.PULSE),
    soundUri = soundUri,
    snoozeMinutes = snoozeMinutes
)

fun ReminderConfig.toEntity(): ReminderConfigEntity = ReminderConfigEntity(
    eventType = eventType.name,
    enabled = enabled,
    stage1LeadMinutes = stage1LeadMinutes,
    stage2Enabled = stage2Enabled,
    stage2DelayMinutes = stage2DelayMinutes,
    stage2Mode = stage2Mode.name,
    vibrationPattern = vibrationPattern.name,
    soundUri = soundUri,
    snoozeMinutes = snoozeMinutes
)

fun DayOffOverrideEntity.toDomain(): DayOffOverride = DayOffOverride(
    workDate = workDate,
    reason = reason
)

fun DayOffOverride.toEntity(): DayOffOverrideEntity = DayOffOverrideEntity(
    workDate = workDate,
    reason = reason
)
