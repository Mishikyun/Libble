package com.personal.workhours.alarm

import com.personal.workhours.domain.model.ReminderEventType

object AlarmContracts {
    const val CHANNEL_GENTLE = "channel_reminders_gentle"
    const val CHANNEL_ALARM = "channel_reminders_alarm_v2"
    const val CHANNEL_ALARM_LEGACY = "channel_reminders_alarm"
    const val CHANNEL_ALARM_SILENT = "channel_reminders_alarm_active"

    const val ACTION_FIRE_REMINDER = "com.personal.workhours.ACTION_FIRE_REMINDER"
    const val ACTION_PUNCH_FROM_NOTIFICATION = "com.personal.workhours.ACTION_PUNCH_FROM_NOTIFICATION"
    const val ACTION_SNOOZE_FROM_NOTIFICATION = "com.personal.workhours.ACTION_SNOOZE_FROM_NOTIFICATION"
    const val ACTION_DISMISS_ALARM = "com.personal.workhours.ACTION_DISMISS_ALARM"
    const val ACTION_STOP_SERVICE = "com.personal.workhours.ACTION_STOP_SERVICE"
    const val ACTION_CLOSE_ALARM_UI = "com.personal.workhours.ACTION_CLOSE_ALARM_UI"

    const val EXTRA_EVENT_TYPE = "extra_event_type"
    const val EXTRA_STAGE = "extra_stage"
    const val EXTRA_WORK_DATE = "extra_work_date"
    const val EXTRA_TARGET_EPOCH_MS = "extra_target_epoch_ms"
    const val EXTRA_IS_TEST = "extra_is_test"

    const val REQUEST_CODE_TIME_IN_STAGE1 = 101
    const val REQUEST_CODE_TIME_IN_STAGE2 = 102
    const val REQUEST_CODE_BREAK_START_STAGE1 = 151
    const val REQUEST_CODE_BREAK_START_STAGE2 = 152
    const val REQUEST_CODE_BREAK_END_STAGE1 = 201
    const val REQUEST_CODE_BREAK_END_STAGE2 = 202
    const val REQUEST_CODE_TIME_OUT_STAGE1 = 301
    const val REQUEST_CODE_TIME_OUT_STAGE2 = 302
    const val REQUEST_CODE_SNOOZE = 401
    const val REQUEST_CODE_TEST = 999

    const val NOTIF_ID_GENTLE_BASE = 1000
    const val NOTIF_ID_ALARM_SERVICE = 2001
    const val NOTIF_ID_MISSED_ALARM = 2002

    fun requestCodeFor(eventType: ReminderEventType, stage: Int): Int = when (eventType) {
        ReminderEventType.TIME_IN -> if (stage == 1) REQUEST_CODE_TIME_IN_STAGE1 else REQUEST_CODE_TIME_IN_STAGE2
        ReminderEventType.BREAK_START -> if (stage == 1) REQUEST_CODE_BREAK_START_STAGE1 else REQUEST_CODE_BREAK_START_STAGE2
        ReminderEventType.BREAK_END -> if (stage == 1) REQUEST_CODE_BREAK_END_STAGE1 else REQUEST_CODE_BREAK_END_STAGE2
        ReminderEventType.TIME_OUT -> if (stage == 1) REQUEST_CODE_TIME_OUT_STAGE1 else REQUEST_CODE_TIME_OUT_STAGE2
    }

    fun gentleNotificationId(eventType: ReminderEventType): Int = NOTIF_ID_GENTLE_BASE + eventType.ordinal
}
