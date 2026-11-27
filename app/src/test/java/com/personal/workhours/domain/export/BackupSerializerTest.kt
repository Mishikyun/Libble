package com.personal.workhours.domain.export

import com.personal.workhours.domain.model.BackupPayload
import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.DayOffOverride
import com.personal.workhours.domain.model.DaySchedule
import com.personal.workhours.domain.model.ReminderConfig
import com.personal.workhours.domain.model.ReminderEventType
import com.personal.workhours.domain.model.WorkDayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    @Test
    fun `round trip encode and decode preserves payload`() {
        val payload = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = listOf(
                WorkDayRecord(
                    id = 1,
                    workDate = "2026-11-25",
                    clockInEpochMs = 10_000L,
                    clockOutEpochMs = 50_000L,
                    breaks = listOf(BreakRecord(id = 1, workDayId = 1, startEpochMs = 20_000L, endEpochMs = 25_000L))
                )
            ),
            daySchedules = listOf(DaySchedule(1, true, 480, 1020, 60, 480)),
            reminderConfigs = listOf(ReminderConfig(ReminderEventType.TIME_IN)),
            dayOffOverrides = listOf(DayOffOverride("2026-11-30", "Holiday"))
        )

        val encoded = BackupSerializer.encode(payload)
        val decoded = BackupSerializer.decodeAndValidate(encoded)
        assertTrue(decoded.isSuccess)
        assertEquals(payload, decoded.getOrThrow())
    }

    @Test
    fun `rejects unsupported schema version and invalid clock out`() {
        val badVersion = BackupPayload(
            schemaVersion = 99,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = emptyList(),
            daySchedules = emptyList(),
            reminderConfigs = emptyList(),
            dayOffOverrides = emptyList()
        )
        assertTrue(BackupSerializer.decodeAndValidate(BackupSerializer.encode(badVersion)).isFailure)

        val badShift = BackupPayload(
            schemaVersion = 1,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = listOf(WorkDayRecord(workDate = "2026-11-25", clockInEpochMs = 50_000L, clockOutEpochMs = 10_000L)),
            daySchedules = emptyList(),
            reminderConfigs = emptyList(),
            dayOffOverrides = emptyList()
        )
        assertTrue(BackupSerializer.decodeAndValidate(BackupSerializer.encode(badShift)).isFailure)
    }

    @Test
    fun `rejects unsafe soundUri, out-of-range reminder config, and oversized day off reason`() {
        val badUri = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = emptyList(),
            daySchedules = emptyList(),
            reminderConfigs = listOf(
                ReminderConfig(ReminderEventType.TIME_IN, soundUri = "file:///etc/passwd")
            ),
            dayOffOverrides = emptyList()
        )
        assertTrue(BackupSerializer.decodeAndValidate(BackupSerializer.encode(badUri)).isFailure)

        val badSnooze = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = emptyList(),
            daySchedules = emptyList(),
            reminderConfigs = listOf(
                ReminderConfig(ReminderEventType.TIME_IN, snoozeMinutes = 0)
            ),
            dayOffOverrides = emptyList()
        )
        assertTrue(BackupSerializer.decodeAndValidate(BackupSerializer.encode(badSnooze)).isFailure)

        val badReason = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = emptyList(),
            daySchedules = emptyList(),
            reminderConfigs = emptyList(),
            dayOffOverrides = listOf(DayOffOverride("2026-11-30", "x".repeat(121)))
        )
        assertTrue(BackupSerializer.decodeAndValidate(BackupSerializer.encode(badReason)).isFailure)

        val validContentUri = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = 1_700_000_000_000L,
            workDays = emptyList(),
            daySchedules = emptyList(),
            reminderConfigs = listOf(
                ReminderConfig(ReminderEventType.TIME_IN, soundUri = "content://settings/system/alarm_alert")
            ),
            dayOffOverrides = listOf(DayOffOverride("2026-11-30", "Holiday"))
        )
        assertTrue(BackupSerializer.decodeAndValidate(BackupSerializer.encode(validContentUri)).isSuccess)
    }
}
