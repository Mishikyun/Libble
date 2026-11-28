# Privacy Policy — Libble

**Effective Date:** November 28, 2026  
**Application:** Libble (`com.personal.workhours`)  
**Repository:** [https://github.com/Mishikyun/Libble](https://github.com/Mishikyun/Libble)

---

## 1. Zero Data Collection & 100% Offline Operation
Libble is a strictly offline personal time-tracking and shift-alarm application.
- **No Network Permissions:** Libble does **not** request `INTERNET` or `ACCESS_NETWORK_STATE` permissions in `AndroidManifest.xml`.
- **No Remote Transmission:** Your shift logs, break times, schedules, notes, and settings never leave your device unless you explicitly export them.
- **No Analytics or Tracking:** Libble contains no third-party analytics SDKs, crash-reporting trackers, advertising identifiers, or telemetry services.

## 2. On-Device Data Storage
All application data is stored locally inside Android's private application sandbox using Room (SQLite) and Jetpack DataStore Preferences:
- Daily clock-in, clock-out, break sessions, Day Off overrides, and optional shift notes
- Weekday work schedules and reminder escalation preferences
- UI theme preference (`System`, `Light`, `Dark`) and setup completion state

Automatic Android cloud backup is explicitly disabled (`android:allowBackup="false"`) so your work records are not silently uploaded to external backup services.

## 3. Device Permissions Used
Libble requests only permissions required for local shift tracking and reliable offline alarms:
- **`POST_NOTIFICATIONS`** — Displays early reminder notifications and active alarm controls.
- **`SCHEDULE_EXACT_ALARM`** — Schedules shift and break alerts at exact user-configured times.
- **`USE_FULL_SCREEN_INTENT`** — Surfaces lock-screen alarm controls (`AlarmActivity`) when full-screen alarms trigger.
- **`FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`** — Keeps active alarm sound and vibration running reliably until dismissed or snoozed.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** *(Optional)* — Lets you grant unrestricted background battery usage so OEM power managers do not delay scheduled alarms.
- **`RECEIVE_BOOT_COMPLETED`** — Re-schedules upcoming shift reminders after your phone restarts or timezone changes.
- **`VIBRATE` & `WAKE_LOCK`** — Plays configured vibration patterns and keeps the CPU awake during an active ringing alarm (capped at a 5-minute timeout).

## 4. User-Initiated Exports & Backups
- **CSV Timesheet Export & JSON Backup:** Files are written **only** when you tap export or backup and choose a destination via the Android system file picker (Storage Access Framework). You are solely responsible for where you store or share those exported files.
- **JSON Restore:** Backup files selected by you are validated locally on-device (size limits, schema version, date format, timestamp ordering, and safe audio URI schemes) before restoring Room state.

## 5. Data Control & Deletion
You remain in full control of your data at all times:
- Edit or delete individual work-day records directly in the **History** tab.
- Erase all stored app data via **Android Settings → Apps → Libble → Storage & cache → Clear storage**, or by uninstalling Libble.

## 6. Policy Updates & Contact
Updates to this Privacy Policy will be shipped with future versions of Libble and published in the project repository at [https://github.com/Mishikyun/Libble](https://github.com/Mishikyun/Libble).
