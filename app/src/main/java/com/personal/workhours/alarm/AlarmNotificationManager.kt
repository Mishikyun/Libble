package com.personal.workhours.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.personal.workhours.MainActivity
import com.personal.workhours.R
import com.personal.workhours.domain.model.ReminderEventType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun ensureChannels() {
        val gentleChannel = NotificationChannel(
            AlarmContracts.CHANNEL_GENTLE,
            context.getString(R.string.notif_channel_gentle_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_gentle_desc)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alarmChannel = NotificationChannel(
            AlarmContracts.CHANNEL_ALARM,
            context.getString(R.string.notif_channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_alarm_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 1L) // Ensure heads-up delivery; VibrationController handles looping alarm vibration
            setSound(null, null) // Handled by MediaPlayer with USAGE_ALARM
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val activeAlarmChannel = NotificationChannel(
            AlarmContracts.CHANNEL_ALARM_SILENT,
            context.getString(R.string.notif_channel_alarm_active_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_alarm_active_desc)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.deleteNotificationChannel(AlarmContracts.CHANNEL_ALARM_LEGACY)
        notificationManager.createNotificationChannel(gentleChannel)
        notificationManager.createNotificationChannel(alarmChannel)
        notificationManager.createNotificationChannel(activeAlarmChannel)
    }

    fun showStage1Reminder(
        eventType: ReminderEventType,
        workDate: String,
        snoozeMinutes: Int,
        isTest: Boolean,
        targetEpochMs: Long = System.currentTimeMillis(),
        leadMinutes: Int = 0
    ) {
        ensureChannels()

        val scheduledTime = Instant.ofEpochMilli(targetEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)

        val (title, body, punchLabel) = if (leadMinutes > 0) {
            when (eventType) {
                ReminderEventType.TIME_IN -> Triple(
                    "Shift starts in ${leadMinutes}m",
                    "Scheduled for $scheduledTime · Tap Clock In when ready.",
                    "Clock In"
                )
                ReminderEventType.BREAK_START -> Triple(
                    "Break starts in ${leadMinutes}m",
                    "Scheduled for $scheduledTime · Tap Start Break when ready.",
                    "Start Break"
                )
                ReminderEventType.BREAK_END -> Triple(
                    "Break ends in ${leadMinutes}m",
                    "Break ends at $scheduledTime · Tap Resume when you're back.",
                    "Resume"
                )
                ReminderEventType.TIME_OUT -> Triple(
                    "Shift ends in ${leadMinutes}m",
                    "Scheduled for $scheduledTime · Wrap up soon.",
                    "Clock Out"
                )
            }
        } else {
            when (eventType) {
                ReminderEventType.TIME_IN -> Triple(
                    "Time to Clock In",
                    "Tap Clock In to start today's timer.",
                    "Clock In"
                )
                ReminderEventType.BREAK_START -> Triple(
                    "Time for Break",
                    "Tap Start Break to pause today's timer.",
                    "Start Break"
                )
                ReminderEventType.BREAK_END -> Triple(
                    "Break ending soon",
                    "Tap Resume when you're back at work.",
                    "Resume"
                )
                ReminderEventType.TIME_OUT -> Triple(
                    "Time to Clock Out",
                    "Your scheduled work hours are wrapping up.",
                    "Clock Out"
                )
            }
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            500 + eventType.ordinal,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val punchPendingIntent = PendingIntent.getBroadcast(
            context,
            600 + eventType.ordinal,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = AlarmContracts.ACTION_PUNCH_FROM_NOTIFICATION
                putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
                putExtra(AlarmContracts.EXTRA_WORK_DATE, workDate)
                putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            700 + eventType.ordinal,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = AlarmContracts.ACTION_SNOOZE_FROM_NOTIFICATION
                putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
                putExtra(AlarmContracts.EXTRA_WORK_DATE, workDate)
                putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, AlarmContracts.CHANNEL_GENTLE)
            .setSmallIcon(R.drawable.ic_stat_workhours)
            .setColor(BRAND_TEAL_ARGB)
            .setSubText(if (isTest) "Test Reminder" else "Reminder")
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(0, punchLabel, punchPendingIntent)
            .addAction(0, "Snooze ${snoozeMinutes}m", snoozePendingIntent)
            .build()

        notificationManager.notify(AlarmContracts.gentleNotificationId(eventType), notification)
    }

    fun buildStage2ForegroundNotification(
        eventType: ReminderEventType,
        workDate: String,
        targetEpochMs: Long,
        fullScreen: Boolean,
        isTest: Boolean,
        suppressHeadsUp: Boolean = false
    ): Notification {
        ensureChannels()

        val scheduledTime = Instant.ofEpochMilli(targetEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(timeFormatter)

        val (title, punchLabel) = when (eventType) {
            ReminderEventType.TIME_IN -> "Time to Clock In" to "Clock In"
            ReminderEventType.BREAK_START -> "Time for Break" to "Start Break"
            ReminderEventType.BREAK_END -> "Break is over" to "Resume"
            ReminderEventType.TIME_OUT -> "Time to Clock Out" to "Clock Out"
        }

        val body = if (isTest) {
            "Test alarm triggered · Tap to open or dismiss."
        } else {
            "Scheduled for $scheduledTime · Tap to punch, snooze, or dismiss."
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            800 + eventType.ordinal,
            buildAlarmActivityIntent(eventType, workDate, targetEpochMs, isTest),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val punchPendingIntent = PendingIntent.getBroadcast(
            context,
            610 + eventType.ordinal,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = AlarmContracts.ACTION_PUNCH_FROM_NOTIFICATION
                putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
                putExtra(AlarmContracts.EXTRA_WORK_DATE, workDate)
                putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            710 + eventType.ordinal,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = AlarmContracts.ACTION_SNOOZE_FROM_NOTIFICATION
                putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
                putExtra(AlarmContracts.EXTRA_WORK_DATE, workDate)
                putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            850 + eventType.ordinal,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = AlarmContracts.ACTION_DISMISS_ALARM
                putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = if (suppressHeadsUp) {
            AlarmContracts.CHANNEL_ALARM_SILENT
        } else {
            AlarmContracts.CHANNEL_ALARM
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_workhours)
            .setColor(BRAND_TEAL_ARGB)
            .setSubText(if (isTest) "Test Alarm" else "Alarm")
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (suppressHeadsUp) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, punchLabel, punchPendingIntent)
            .addAction(0, "Snooze", snoozePendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)

        if (fullScreen && !suppressHeadsUp) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
        return builder.build()
    }

    fun buildAlarmActivityIntent(
        eventType: ReminderEventType,
        workDate: String,
        targetEpochMs: Long,
        isTest: Boolean
    ): Intent {
        return Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmContracts.EXTRA_EVENT_TYPE, eventType.name)
            putExtra(AlarmContracts.EXTRA_WORK_DATE, workDate)
            putExtra(AlarmContracts.EXTRA_TARGET_EPOCH_MS, targetEpochMs)
            putExtra(AlarmContracts.EXTRA_IS_TEST, isTest)
        }
    }

    fun showMissedAlarmNotification(eventType: ReminderEventType) {
        ensureChannels()
        val label = when (eventType) {
            ReminderEventType.TIME_IN -> "Clock In"
            ReminderEventType.BREAK_START -> "Start Break"
            ReminderEventType.BREAK_END -> "Resume Work"
            ReminderEventType.TIME_OUT -> "Clock Out"
        }
        val openAppIntent = PendingIntent.getActivity(
            context,
            901,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = "Stopped automatically after 5 minutes. Open Libble to log."
        val notification = NotificationCompat.Builder(context, AlarmContracts.CHANNEL_GENTLE)
            .setSmallIcon(R.drawable.ic_stat_workhours)
            .setColor(BRAND_TEAL_ARGB)
            .setSubText("Missed")
            .setContentTitle("Missed $label")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()
        notificationManager.notify(AlarmContracts.NOTIF_ID_MISSED_ALARM, notification)
    }

    fun cancelNotificationForEvent(eventType: ReminderEventType) {
        notificationManager.cancel(AlarmContracts.gentleNotificationId(eventType))
    }

    companion object {
        private const val BRAND_TEAL_ARGB = 0xFF0D9488.toInt()
    }
}
