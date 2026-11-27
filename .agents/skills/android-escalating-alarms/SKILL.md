---
name: android-escalating-alarms
description: Use when implementing, modifying, or debugging Android AlarmManager scheduling, lock-screen full-screen alarms, custom vibration patterns, notification quick actions, or boot and timezone alarm rescheduling.
---

# Android Escalating Alarms

## Overview
Two-stage reminders (gentle Stage 1 notification -> Stage 2 vibration/ringing lock-screen alarm) fail silently on modern Android unless permissions, `PendingIntent` codes, Doze exemptions, and database guards stay tightly aligned.

## Core Patterns

### 1. Choose the Right AlarmManager API
- **Stage 1 (Gentle Notification):** Use `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, triggerMs, pi)` when `canScheduleExactAlarms()` is true; fall back to `setAndAllowWhileIdle(...)` instead of crashing if revoked.
- **Stage 2 (Escalated Vibration / Full Alarm):** Use `AlarmManager.setAlarmClock(AlarmClockInfo(triggerMs, showIntent), pi)` so Android exempts it from Doze and treats it as a real alarm clock.

### 2. Mandatory DB-Guard Inside `goAsync()`
Never show a notification or ring unconditionally when an alarm fires. Inside `AlarmBroadcastReceiver.onReceive()`:
1. Call `val pendingResult = goAsync()`.
2. Query Room on `Dispatchers.IO` inside a `try { ... } finally { pendingResult.finish() }` block.
3. Verify the target event is still needed (e.g., `TIME_IN` requires `NOT_STARTED` and not `DAY_OFF`; `BREAK_START` requires `CLOCKED_IN` with `breaks.isEmpty()`; `BREAK_END` requires `ON_BREAK`; `TIME_OUT` requires `CLOCKED_IN` or `ON_BREAK`).
4. Abort silently if the user already punched in early or skipped today.
5. When `stage2DelayMinutes == 0` ("On time"), skip scheduling redundant Stage 1 if `stage1TriggerMs == stage2TriggerMs`.

### 3. Deterministic `PendingIntent` Request Codes
Always pass `PendingIntent.FLAG_IMMUTABLE` and fixed request codes so updates and cancellations match:
- `101` / `102`: `TIME_IN` Stage 1 / Stage 2
- `251` / `252`: `BREAK_START` Stage 1 / Stage 2
- `201` / `202`: `BREAK_END` Stage 1 / Stage 2
- `301` / `302`: `TIME_OUT` Stage 1 / Stage 2
- `401`: Snooze · `999`: Settings Test Alarm

### 4. `AlarmRingingService` Audio, Vibration & WakeLock Bounds
- Declare `android:foregroundServiceType="mediaPlayback"` in `AndroidManifest.xml` (plus `FOREGROUND_SERVICE_MEDIA_PLAYBACK` on API 34+).
- Configure `MediaPlayer` and `Vibrator` with `AudioAttributes.USAGE_ALARM` so alerts obey the Alarm volume channel rather than silent/media mode.
- Acquire a partial `WakeLock` with an explicit **5-minute (`300_000L` ms) timeout** and auto-stop the service if unanswered, posting a sticky missed-reminder notification.
- Allowlist `content://` and `android.resource://` URI schemes on custom alarm tones before calling `MediaPlayer.setDataSource(...)`, falling back cleanly to the default alarm/notification URI.
- Always run the requested primary punch on Stage 2 notification/modal buttons (do not suppress punches behind `if (!isTest)`), guard UI actions with `actionInFlight`, and cancel `REQUEST_CODE_SNOZE` (`401`) whenever an event is cancelled or resolved.

### 5. Lock-Screen Activity, Channel Migration & OEM Reliability
- For `FULL_ALARM`, attach `setFullScreenIntent(alarmActivityPi, true)` on `channel_reminders_alarm_v2` (`IMPORTANCE_HIGH`, `vibrationPattern = [0L, 1L]`, `setBypassDnd(true)` — bump channel ID and delete legacy channels whenever altering sound/vibration/DND attributes), and call `setShowWhenLocked(true)` + `setTurnScreenOn(true)` in `AlarmActivity`.
- Avoid duplicate heads-up banners when `AlarmActivity` opens directly: post the foreground service notification on an `IMPORTANCE_LOW` silent channel (`channel_reminders_alarm_silent`) whenever launching `AlarmActivity` directly, and broadcast package-scoped `ACTION_CLOSE_ALARM_UI` (received with `RECEIVER_NOT_EXPORTED`) so notification quick actions dismiss any open `AlarmActivity`.
- Do **not** request `SYSTEM_ALERT_WINDOW` (blocked by Restricted Settings on sideloaded APKs). For OEM Doze whitelisting, declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and open `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:<pkg>` alongside fallback battery settings.
- Register `BootAndTimeChangeReceiver` (`android:exported="false"` except system broadcasts) for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_CHANGED`, `TIMEZONE_CHANGED`, and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, always calling `reconcileAll()`.

## Common Mistakes & Verification
- **Mistake:** Scheduling Stage 2 with `setExactAndAllowWhileIdle` -> delayed up to 9+ minutes in deep Doze. Use `setAlarmClock`.
- **Mistake:** Launching `AlarmActivity` directly from a `BroadcastReceiver` on Android 10+ -> blocked by background activity launch restrictions. Start `AlarmRingingService` and surface `AlarmActivity` via `FullScreenIntent`.
- **Verify via ADB:**
  ```powershell
  adb shell dumpsys alarm | Select-String "com.personal.workhours"
  adb shell dumpsys deviceidle force-idle
  ```
