package com.personal.workhours.domain.model

import kotlinx.serialization.Serializable

enum class WorkDayStatus {
    NOT_STARTED,
    CLOCKED_IN,
    ON_BREAK,
    COMPLETED,
    DAY_OFF,
    MISSED
}

enum class ReminderEventType {
    TIME_IN,
    BREAK_START,
    BREAK_END,
    TIME_OUT
}

enum class EscalationMode {
    VIBRATE_ONLY,
    SOUND_AND_VIBRATE,
    FULL_ALARM
}

enum class VibrationPatternType {
    PULSE,
    HEARTBEAT,
    URGENT
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
data class BreakRecord(
    val id: Long = 0L,
    val workDayId: Long = 0L,
    val startEpochMs: Long,
    val endEpochMs: Long? = null
)

@Serializable
data class WorkDayRecord(
    val id: Long = 0L,
    val workDate: String, // ISO-8601 YYYY-MM-DD
    val clockInEpochMs: Long? = null,
    val clockOutEpochMs: Long? = null,
    val isDayOff: Boolean = false,
    val autoDeductBreakMinutes: Int = 0,
    val note: String = "",
    val updatedAtEpochMs: Long = 0L,
    val breaks: List<BreakRecord> = emptyList()
)

@Serializable
data class DaySchedule(
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val isWorkDay: Boolean,
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int, // 0..1439
    val defaultBreakMinutes: Int, // 0..480
    val targetWorkMinutes: Int, // 0..1440
    val breakStartMinuteOfDay: Int? = 12 * 60 // 0..1439
)

@Serializable
data class ReminderConfig(
    val eventType: ReminderEventType,
    val enabled: Boolean = true,
    val stage1LeadMinutes: Int = 0,
    val stage2Enabled: Boolean = true,
    val stage2DelayMinutes: Int = 10,
    val stage2Mode: EscalationMode = EscalationMode.FULL_ALARM,
    val vibrationPattern: VibrationPatternType = VibrationPatternType.PULSE,
    val soundUri: String? = null,
    val snoozeMinutes: Int = 5
)

@Serializable
data class DayOffOverride(
    val workDate: String, // ISO-8601 YYYY-MM-DD
    val reason: String = "Day Off"
)

data class WorkDurationSummary(
    val grossMs: Long,
    val manualBreakMs: Long,
    val effectiveBreakMs: Long,
    val activeBreakMs: Long,
    val netWorkMs: Long,
    val targetMs: Long,
    val deltaMs: Long
)

data class DailyChartPoint(
    val workDate: String,
    val shortLabel: String,
    val netHours: Float,
    val targetHours: Float,
    val breakMinutes: Long,
    val status: WorkDayStatus
)

data class PeriodStats(
    val totalNetMs: Long,
    val totalTargetMs: Long,
    val totalBreakMs: Long,
    val workedDaysCount: Int,
    val averageNetMsPerWorkedDay: Long,
    val deltaMs: Long
)

sealed interface EditValidationResult {
    data object Valid : EditValidationResult
    data class Invalid(val message: String) : EditValidationResult
}

data class AlarmTriggerSpec(
    val eventType: ReminderEventType,
    val stage: Int, // 1 = Gentle notification, 2 = Escalated alarm/vibration
    val workDate: String,
    val scheduledTargetEpochMs: Long,
    val triggerEpochMs: Long
)

@Serializable
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val workDays: List<WorkDayRecord>,
    val daySchedules: List<DaySchedule>,
    val reminderConfigs: List<ReminderConfig>,
    val dayOffOverrides: List<DayOffOverride>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
