package com.personal.workhours.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.personal.workhours.WorkHoursApplication
import com.personal.workhours.data.local.toDomain
import com.personal.workhours.domain.model.ReminderEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val eventType = runCatching {
            ReminderEventType.valueOf(intent.getStringExtra(AlarmContracts.EXTRA_EVENT_TYPE) ?: "")
        }.getOrNull() ?: return
        val workDateStr = intent.getStringExtra(AlarmContracts.EXTRA_WORK_DATE) ?: LocalDate.now().toString()
        val isTest = intent.getBooleanExtra(AlarmContracts.EXTRA_IS_TEST, false)

        val pendingResult = goAsync()
        val app = context.applicationContext as WorkHoursApplication
        val container = app.container

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AlarmRingingService.stop(context)
                container.notificationManager.cancelNotificationForEvent(eventType)

                when (action) {
                    AlarmContracts.ACTION_PUNCH_FROM_NOTIFICATION -> {
                        val date = runCatching { LocalDate.parse(workDateStr) }.getOrDefault(LocalDate.now())
                        val changed = when (eventType) {
                            ReminderEventType.TIME_IN -> container.workLogRepository.clockIn(date)
                            ReminderEventType.BREAK_START -> container.workLogRepository.startBreak(date)
                            ReminderEventType.BREAK_END -> container.workLogRepository.resumeWork(date)
                            ReminderEventType.TIME_OUT -> container.workLogRepository.clockOut(date)
                        }
                        container.alarmScheduler.cancelEvent(eventType)
                        val message = when (eventType) {
                            ReminderEventType.TIME_IN -> if (changed) "Clocked in" else "Already clocked in or shift already logged today"
                            ReminderEventType.BREAK_START -> if (changed) "Break started" else "Clock in first or break already active"
                            ReminderEventType.BREAK_END -> if (changed) "Work resumed" else "No active break to resume"
                            ReminderEventType.TIME_OUT -> if (changed) "Clocked out" else "No active shift to clock out"
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    AlarmContracts.ACTION_SNOOZE_FROM_NOTIFICATION -> {
                        val cfg = container.database.scheduleDao().getReminderConfig(eventType.name)?.toDomain()
                        val snoozeMins = cfg?.snoozeMinutes ?: 5
                        container.alarmScheduler.cancelEvent(eventType)
                        container.alarmScheduler.scheduleSnooze(eventType, workDateStr, snoozeMins, isTest)
                    }

                    AlarmContracts.ACTION_DISMISS_ALARM -> {
                        container.alarmScheduler.cancelEvent(eventType)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
