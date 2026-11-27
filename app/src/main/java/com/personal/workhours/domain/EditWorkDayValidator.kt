package com.personal.workhours.domain

import com.personal.workhours.domain.model.BreakRecord
import com.personal.workhours.domain.model.EditValidationResult

object EditWorkDayValidator {

    private const val MAX_NOTE_LENGTH = 200

    fun validate(
        isDayOff: Boolean,
        clockInEpochMs: Long?,
        clockOutEpochMs: Long?,
        breaks: List<BreakRecord>,
        note: String
    ): EditValidationResult {
        if (note.length > MAX_NOTE_LENGTH) {
            return EditValidationResult.Invalid("Note cannot exceed $MAX_NOTE_LENGTH characters.")
        }

        if (isDayOff) {
            return EditValidationResult.Valid
        }

        if (clockInEpochMs == null) {
            return if (clockOutEpochMs != null || breaks.isNotEmpty()) {
                EditValidationResult.Invalid("Clock In time is required when Clock Out or breaks are set.")
            } else {
                EditValidationResult.Valid
            }
        }

        if (clockOutEpochMs != null && clockOutEpochMs <= clockInEpochMs) {
            return EditValidationResult.Invalid("Clock Out must be after Clock In.")
        }

        val sortedBreaks = breaks.sortedBy { it.startEpochMs }
        for (i in sortedBreaks.indices) {
            val current = sortedBreaks[i]
            if (current.startEpochMs < clockInEpochMs) {
                return EditValidationResult.Invalid("Break start cannot be earlier than Clock In.")
            }
            val end = current.endEpochMs
            if (end != null) {
                if (end <= current.startEpochMs) {
                    return EditValidationResult.Invalid("Break end must be after break start.")
                }
                if (clockOutEpochMs != null && end > clockOutEpochMs) {
                    return EditValidationResult.Invalid("Break end cannot be after Clock Out.")
                }
            } else if (clockOutEpochMs != null) {
                return EditValidationResult.Invalid("Completed shifts cannot contain an open break.")
            }

            if (i > 0) {
                val prev = sortedBreaks[i - 1]
                val prevEnd = prev.endEpochMs ?: Long.MAX_VALUE
                if (current.startEpochMs < prevEnd) {
                    return EditValidationResult.Invalid("Break intervals cannot overlap.")
                }
            }
        }

        return EditValidationResult.Valid
    }
}
