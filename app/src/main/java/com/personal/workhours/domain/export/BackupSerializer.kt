package com.personal.workhours.domain.export

import com.personal.workhours.domain.EditWorkDayValidator
import com.personal.workhours.domain.model.BackupPayload
import com.personal.workhours.domain.model.EditValidationResult
import kotlinx.serialization.json.Json
import java.time.LocalDate

object BackupSerializer {

    const val MAX_BACKUP_BYTES = 5 * 1024 * 1024 // 5 MB
    const val MAX_WORK_DAYS = 5_000
    const val MAX_REASON_LENGTH = 120

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encode(payload: BackupPayload): String {
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    fun decodeAndValidate(rawJson: String): Result<BackupPayload> {
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            return Result.failure(IllegalArgumentException("Backup file exceeds maximum allowed size (5 MB)."))
        }

        val payload = runCatching {
            json.decodeFromString(BackupPayload.serializer(), rawJson)
        }.getOrElse { err ->
            return Result.failure(IllegalArgumentException("Invalid backup format: ${err.message}"))
        }

        if (payload.schemaVersion != BackupPayload.CURRENT_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException("Unsupported backup schema version: ${payload.schemaVersion}.")
            )
        }

        if (payload.workDays.size > MAX_WORK_DAYS) {
            return Result.failure(
                IllegalArgumentException("Backup exceeds maximum supported work day entries ($MAX_WORK_DAYS).")
            )
        }

        for (day in payload.workDays) {
            if (runCatching { LocalDate.parse(day.workDate) }.isFailure) {
                return Result.failure(IllegalArgumentException("Invalid workDate in backup: ${day.workDate}"))
            }
            val validation = EditWorkDayValidator.validate(
                isDayOff = day.isDayOff,
                clockInEpochMs = day.clockInEpochMs,
                clockOutEpochMs = day.clockOutEpochMs,
                breaks = day.breaks,
                note = day.note
            )
            if (validation is EditValidationResult.Invalid) {
                return Result.failure(IllegalArgumentException("Invalid shift on ${day.workDate}: ${validation.message}"))
            }
        }

        for (schedule in payload.daySchedules) {
            if (schedule.dayOfWeek !in 1..7 ||
                schedule.startMinuteOfDay !in 0..1439 ||
                schedule.endMinuteOfDay !in 0..1439 ||
                schedule.defaultBreakMinutes !in 0..480 ||
                schedule.targetWorkMinutes !in 0..1440 ||
                (schedule.breakStartMinuteOfDay != null && schedule.breakStartMinuteOfDay !in 0..1439)
            ) {
                return Result.failure(IllegalArgumentException("Invalid schedule entry for dayOfWeek=${schedule.dayOfWeek}."))
            }
        }

        for (cfg in payload.reminderConfigs) {
            if (cfg.stage1LeadMinutes !in 0..120 ||
                cfg.stage2DelayMinutes !in 0..120 ||
                cfg.snoozeMinutes !in 1..60
            ) {
                return Result.failure(
                    IllegalArgumentException("Invalid reminder timing config for ${cfg.eventType}.")
                )
            }
            val uri = cfg.soundUri
            if (uri != null && !uri.startsWith("content://") && !uri.startsWith("android.resource://")) {
                return Result.failure(
                    IllegalArgumentException("Unsupported reminder sound URI scheme for ${cfg.eventType}.")
                )
            }
        }

        for (override in payload.dayOffOverrides) {
            if (runCatching { LocalDate.parse(override.workDate) }.isFailure) {
                return Result.failure(IllegalArgumentException("Invalid Day Off date in backup: ${override.workDate}"))
            }
            if (override.reason.length > MAX_REASON_LENGTH) {
                return Result.failure(
                    IllegalArgumentException("Day Off reason exceeds $MAX_REASON_LENGTH characters on ${override.workDate}.")
                )
            }
        }

        return Result.success(payload)
    }
}
