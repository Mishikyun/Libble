package com.personal.workhours.data.repository

import com.personal.workhours.domain.model.ReminderEventType

interface AlarmReconciler {
    suspend fun reconcileAll()
    suspend fun scheduleBreakEnd(breakStartEpochMs: Long)
    fun cancelBreakEnd()
    fun cancelEvent(eventType: ReminderEventType)
}
