# Implementation Plan — WorkHours

> **Execution Model:** 7 vertical-slice phases. Every phase ends with runnable automated tests and a full Gradle verification pass (`testDebugUnitTest lintDebug assembleDebug`).

---

## Phase 0 — Project Scaffolding & Build Baseline
**Goal:** Stand up a clean, compiling Android Kotlin + Jetpack Compose + Room + KSP Gradle project inside `work-hours-monitoring/`.

### Deliverables
- Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `gradle/libs.versions.toml`, `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `.gitignore`.
- `app/build.gradle.kts` (`com.personal.workhours`, `minSdk = 26`, `compileSdk = 36`, `targetSdk = 35`, Compose, Room + KSP, DataStore, Serialization, Unit/UI Test deps).
- `app/src/main/AndroidManifest.xml` (explicit `android:exported`, `allowBackup="false"`, zero `INTERNET` permission).
- `WorkHoursApplication.kt` + manual `AppContainer.kt` + placeholder `MainActivity.kt` + `res/values/strings.xml`.

### Verification
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

---

## Phase 1 — Pure Domain Engine & Room Database (TDD First)
**Goal:** Implement and verify all time-tracking math, state resolution, and transactional Room persistence before building UI.

### Deliverables
- **Domain (`domain/`):**
  - `WorkDayStatus` (`NOT_STARTED`, `CLOCKED_IN`, `ON_BREAK`, `COMPLETED`, `DAY_OFF`, `MISSED`)
  - `WorkDurationCalculator` (gross, break, auto-deduct fallback, net, target delta)
  - `WorkDayStateResolver` (overnight unclosed-shift detection + daily state derivation)
  - `ScheduleResolver` (next scheduled Time In / Time Out across 7-day lookahead honoring `day_off_overrides`)
- **Data (`data/`):**
  - Room entities (`WorkDayEntity`, `BreakSessionEntity`, `DayScheduleEntity`, `ReminderConfigEntity`, `DayOffOverrideEntity`), DAOs, `WorkHoursDatabase` (`version = 1`, `exportSchema = true`, pre-population callback for Mon–Sun + 3 reminder configs).
  - `WorkLogRepository` (`@Transaction` `clockIn`, `startBreak`, `resumeWork`, `clockOut`, `skipToday`, `unskipToday`, `saveManualEdit`) + `ScheduleRepository` + `PreferencesRepository` (DataStore).
- **Tests:**
  - `WorkDurationCalculatorTest`, `WorkDayStateResolverTest`, `ScheduleResolverTest` (JVM tests with fixed `Clock`).
  - `WorkLogRepositoryTest` (in-memory Room tests verifying single-active-shift, auto-closing open break on clock out, and edit validation).

### Verification
```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

---

## Phase 2 — Material 3 Theme, Navigation & Dashboard Punch Flow
**Goal:** Build the Light/Dark/System Material 3 theme, 4-tab navigation shell, and functional daily punch card with live tabular timer.

### Deliverables
- `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`, `ExtendedColors.kt`) supporting System/Light/Dark modes.
- `ui/navigation/WorkHoursNavHost.kt` (`Dashboard`, `History`, `Schedule`, `Settings`).
- `ui/dashboard/DashboardViewModel.kt` + `DashboardScreen.kt`:
  - Live 1-second ticking work & break timer (`tnum`), status badge, state-aware punch buttons (`0.97f` press scale), `[Skip Today]` chip, overnight unclosed-shift banner, next scheduled alert summary.
  - Dual Light & Dark `@Preview`s for every state (`NOT_STARTED`, `CLOCKED_IN`, `ON_BREAK`, `COMPLETED`, `DAY_OFF`, `OVERNIGHT_ALERT`).
- **Tests:** `DashboardViewModelTest` + Compose UI test verifying Clock In -> Start Break -> Resume -> Clock Out flow.

### Verification
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

---

## Phase 3 — Dashboard Bar Graph, History & Manual Punch Editor
**Goal:** Add the Week/Month Canvas bar chart, monthly History log, and validated manual edit / missed-day bottom sheet.

### Deliverables
- `ui/dashboard/HoursBarChart.kt` (custom Compose `Canvas` chart for Week & Month views, dashed target line, color-coded bars, tap-to-inspect tooltip, TalkBack semantics, Light/Dark previews).
- `ui/history/HistoryViewModel.kt` + `HistoryScreen.kt` (month switcher, summary KPI row, daily cards, `+ Add Missed Day`).
- `ui/history/EditWorkDaySheet.kt` (Material 3 `ModalBottomSheet` for editing Clock In, Clock Out, multiple Break intervals, Day Off toggle, Note, with inline validation errors).
- **Tests:** Chart aggregation & KPI tests, `EditWorkDayValidatorTest`, Compose UI test for adding a missed punch.

### Verification
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

---

## Phase 4 — Schedule Editor & Two-Stage Escalating Alarm Engine
**Goal:** Implement schedule/reminder configuration and the full Android exact-alarm, vibration, notification-action, and lock-screen alarm engine.

### Deliverables
- `ui/schedule/ScheduleViewModel.kt` + `ScheduleScreen.kt` (default & per-day Mon–Sun editor, Day-Off/Holiday date picker, per-event Stage 1 + Stage 2 escalation config + vibration pattern picker).
- `alarm/`:
  - `AlarmNotificationManager.kt` (`channel_reminders_gentle`, `channel_reminders_alarm`, inline `[Clock In]` / `[Resume]` / `[Clock Out]` / `[Snooze]` actions).
  - `AlarmScheduler.kt` (`reconcileAll()`, `scheduleBreakEnd()`, `scheduleSnooze()`, `cancelEvent()`, deterministic request codes `101..999`).
  - `AlarmBroadcastReceiver.kt` (`goAsync()` + Room DB guard before Stage 1 or Stage 2).
  - `NotificationActionReceiver.kt` (handles notification button taps & cancels Stage 2).
  - `AlarmRingingService.kt` (foreground `mediaPlayback` service, `USAGE_ALARM` `MediaPlayer` + `VibrationEffect` patterns, 5-minute bounded `WakeLock`).
  - `AlarmActivity.kt` (lock-screen Compose UI with `CLOCK IN NOW` / `Snooze` / `Dismiss`).
  - `BootAndTimeChangeReceiver.kt` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_CHANGED`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`).
- **Tests:** `AlarmSchedulerPlanTest` (pure trigger computation tests across weekends, holidays, early clock-in, and snooze) + DB-guard receiver logic tests.

### Verification
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# On connected device/emulator:
adb shell dumpsys alarm | Select-String "com.personal.workhours"
```

---

## Phase 5 — Onboarding, Permissions Health, CSV Export & JSON Backup
**Goal:** Complete Settings, first-launch onboarding, 5-second test alert, RFC-4180 CSV export, and validated JSON backup/restore.

### Deliverables
- `domain/export/CsvExporter.kt` (RFC-4180 + formula-injection escaping) & `domain/export/BackupSerializer.kt` (schema v1 + strict validation).
- `ui/onboarding/OnboardingScreen.kt` (3-step setup: default schedule -> permissions checklist -> optional test alert).
- `ui/settings/SettingsViewModel.kt` + `SettingsScreen.kt` (Theme picker, Permissions Health status + quick deep-links, `"Test 5s Escalation"` button, Auto-deduct break toggle, SAF CSV Export + Share, SAF JSON Backup & Restore).
- **Tests:** `CsvExporterTest` (formula-injection vectors `=`, `+`, `-`, `@`), `BackupSerializerTest` (corrupted/oversized/invalid-range JSON rejection + round-trip restore).

### Verification
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

---

## Phase 6 — Hardening, ADB Device QA & Signed Release APK
**Goal:** Verify real-device Doze/reboot alarm behavior, accessibility, R8 release shrink, and produce a clean sideloadable APK for both phones.

### Deliverables
- Release build config (`isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-rules.pro` keeping Room/Serialization/Receiver entries, gitignored `keystore.properties` template).
- End-to-end physical device / emulator QA checklist executed via `adb`:
  - Test Stage 1 -> Stage 2 escalation while screen is locked and in forced Doze:
    ```powershell
    adb shell dumpsys deviceidle force-idle
    ```
  - Test early Clock In cancelling pending Stage 1/2 alarms (`dumpsys alarm` confirms cleared).
  - Test `Skip Today` cancelling alarms.
  - Test reboot persistence (`adb reboot` -> `dumpsys alarm` confirms alarms re-armed).
  - Test TalkBack traversal and Light/Dark font-scale 1.3x layout checks.
- Final signed `app-release.apk` verified cleanly installed on device (`adb install -r`).

### Final Verification Gate
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```
