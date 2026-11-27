package com.personal.workhours.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.personal.workhours.WorkHoursApplication
import com.personal.workhours.data.local.toDomain
import com.personal.workhours.domain.ScheduleResolver
import com.personal.workhours.domain.model.ReminderEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class AlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmContracts.ACTION_FIRE_REMINDER) return

        val eventType = runCatching {
            ReminderEventType.valueOf(intent.getStringExtra(AlarmContracts.EXTRA_EVENT_TYPE) ?: "")
        }.getOrNull() ?: return
        val stage = intent.getIntExtra(AlarmContracts.EXTRA_STAGE, 1)
        val workDateStr = intent.getStringExtra(AlarmContracts.EXTRA_WORK_DATE) ?: return
        val targetEpochMs = intent.getLongExtra(AlarmContracts.EXTRA_TARGET_EPOCH_MS, System.currentTimeMillis())
        val isTest = intent.getBooleanExtra(AlarmContracts.EXTRA_IS_TEST, false)

        val pendingResult = goAsync()
        val app = context.applicationContext as WorkHoursApplication
        val container = app.container

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = container.database
                val config = db.scheduleDao().getReminderConfig(eventType.name)?.toDomain()

                if (!isTest) {
                    if (config == null || !config.enabled) return@launch
                    if (stage == 2 && !config.stage2Enabled) return@launch

                    val workDate = runCatching { LocalDate.parse(workDateStr) }.getOrNull() ?: return@launch
                    val today = Instant.ofEpochMilli(Clock.systemDefaultZone().millis())
                        .atZone(Clock.systemDefaultZone().zone)
                        .toLocalDate()
                    val record = db.workLogDao().getWorkDay(workDateStr)?.toDomain()
                    val schedule = db.scheduleDao().getScheduleForDay(workDate.dayOfWeek.value)?.toDomain()
                    val isOverride = db.scheduleDao().isDayOffOverride(workDateStr)

                    val stillNeeded = ScheduleResolver(Clock.systemDefaultZone()).isEventStillNeeded(
                        eventType = eventType,
                        workDate = workDate,
                        today = today,
                        record = record,
                        schedule = schedule,
                        isDayOffOverride = isOverride
                    )
                    if (!stillNeeded) {
                        container.alarmScheduler.cancelEvent(eventType)
                        return@launch
                    }
                }

                if (stage == 1) {
                    container.notificationManager.showStage1Reminder(
                        eventType = eventType,
                        workDate = workDateStr,
                        snoozeMinutes = config?.snoozeMinutes ?: 5,
                        isTest = isTest,
                        targetEpochMs = targetEpochMs,
                        leadMinutes = config?.stage1LeadMinutes ?: 0
                    )
                } else {
                    container.notificationManager.cancelNotificationForEvent(eventType)
                    val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                        putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
                        putExtra(AlarmContracts.EXTRA_WORK_DATE, workDateStr)
                        putExtra(AlarmContracts.EXTRA_TARGET_EPOCH_MS, targetEpochMs)
                        putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
                        putExtra(AlarmRingingService.EXTRA_ESCALATION_MODE, config?.stage2Mode?.name)
                        putExtra(AlarmRingingService.EXTRA_VIBRATION_PATTERN, config?.vibrationPattern?.name)
                        putExtra(AlarmRingingService.EXTRA_SOUND_URI, config?.soundUri)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
