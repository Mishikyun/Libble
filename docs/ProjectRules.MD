# Project Rules & Engineering Standards — WorkHours

> Mandatory rules for every contributor and AI agent working in `work-hours-monitoring`.

---

## 1. Core Engineering Principles

1. **Ponytail / YAGNI Discipline:** Write the smallest, cleanest working implementation. Do not add multi-module Gradle splits, Hilt/Dagger, Retrofit, or third-party charting libraries when manual `AppContainer` DI and Compose `Canvas` handle v1 cleanly.
2. **Complete Diffs Only:** Never leave `TODO`, `FIXME`, stubbed functions, or truncated placeholders in committed code.
3. **Pure Domain Isolation:** All duration calculations, state resolution, schedule matching, CSV formatting, and JSON backup validation must live in pure Kotlin classes under `domain/` with zero `android.*` imports, taking `java.time.Clock` as a constructor parameter for deterministic testing.

---

## 2. Kotlin & Jetpack Compose Conventions

- **Package Root:** `com.personal.workhours`
- **State Management:**
  - Unidirectional Data Flow: `ViewModel` exposes a single immutable `StateFlow<UiState>` using `stateIn(..., SharingStarted.WhileSubscribed(5_000), ...)`.
  - Side effects (snackbars, navigation, file picker triggers) use `Channel` / `SharedFlow` or one-shot UI state flags consumed cleanly.
- **Compose Hygiene:**
  - Every screen and reusable component must use `MaterialTheme.colorScheme` and `WorkHoursExtendedColors`—zero hardcoded `Color(0x...)` inside UI screens.
  - All numeric timers, durations, and clock strings must apply `fontFeatureSettings = "tnum"`.
  - All interactive elements must be at least `48dp x 48dp` with clear `contentDescription` for icons and semantic labels for custom `Canvas` graphics.
  - Every screen composable must include **both Light and Dark `@Preview` functions** using realistic sample state.

---

## 3. Android Reliability, Concurrency & Alarm Rules

- **Main Thread Protection:** Never access Room, DataStore, or file I/O on the main thread. All repository calls use `suspend` functions or `Flow` on `Dispatchers.IO`.
- **BroadcastReceiver Safety:** Any `BroadcastReceiver` that reads Room or DataStore (`AlarmBroadcastReceiver`, `NotificationActionReceiver`, `BootAndTimeChangeReceiver`) **must** call `goAsync()` and run inside a bounded coroutine scope, calling `pendingResult.finish()` in a `finally` block.
- **No `GlobalScope`:** Never launch work in `GlobalScope`. Use `viewModelScope`, `lifecycleScope`, or a bounded `CoroutineScope(SupervisorJob() + Dispatchers.IO)` inside services/receivers.
- **PendingIntent Hygiene:**
  - Always set `PendingIntent.FLAG_IMMUTABLE` (plus `FLAG_UPDATE_CURRENT` or `FLAG_NO_CREATE` as appropriate).
  - Always use the deterministic request codes documented in `SystemDesign.MD` (`101`, `102`, `201`, `202`, `301`, `302`, `401`, `999`) so updates and cancellations are exact.
- **Mandatory DB-Guard:** Stage 1 and Stage 2 alarm receivers must verify current Room state (`work_days`, `break_sessions`, `day_off_overrides`) before showing a notification or starting `AlarmRingingService`. Never ring for an already-punched event or Day Off.
- **WakeLock & Foreground Service Bounds:** `AlarmRingingService` must acquire a partial `WakeLock` with an explicit timeout (`5 * 60_000L` ms) and always stop self cleanly on user action or timeout.
- **Manifest Exports:** Every `<activity>`, `<service>`, and `<receiver>` in `AndroidManifest.xml` must declare `android:exported` explicitly (`false` for internal receivers/services, `true` only for launcher `MainActivity`).

---

## 4. Security & Privacy Hardening (Offline-First)

- **Zero Network Footprint (v1):** Do **not** declare `android.permission.INTERNET` or `ACCESS_NETWORK_STATE` in `AndroidManifest.xml`.
- **No Auto Backup Leakage:** Set `android:allowBackup="false"` in `AndroidManifest.xml` so private work logs are not silently uploaded to cloud backup outside the user's explicit JSON backup/CSV export.
- **SQL Safety:** Use Room `@Query` with bound parameters (`:workDate`, `:id`) only. Raw SQL string concatenation is forbidden.
- **CSV Formula Injection Prevention:** Escape any text field beginning with `=`, `+`, `-`, `@`, `\t`, or `\r` by prefixing `'` before RFC-4180 quoting.
- **JSON Restore Validation:** Enforce a `5 MB` file-size cap, `schemaVersion == 1`, strict `kotlinx.serialization` parsing (ignore unknown keys only if explicitly safe, reject malformed types), valid ISO-8601 date strings, and valid timestamp ranges (`clockOut >= clockIn`, `breakEnd > breakStart`) before mutating Room inside a `@Transaction`.
- **Release Logging:** Configure R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) and strip `Log.d`/`Log.v` calls in release builds. Never log user notes or file URIs in production logs.
- **Secrets Protection:** Signing keystore paths and passwords (`keystore.properties`, `*.jks`, `*.keystore`) must stay in `.gitignore` and never enter git history.

---

## 5. Testing & Verification Gates

- **TDD for Domain Logic:** Write unit tests first for `WorkDurationCalculator`, `WorkDayStateResolver`, `ScheduleResolver`, `CsvExporter`, and `BackupSerializer`.
- **Room Persistence Tests:** Verify `@Transaction` invariants (single open shift, auto-closing open breaks on clock-out, cascading deletes, invalid edit rejection) with `Room.inMemoryDatabaseBuilder`.
- **Mandatory Build Gate Before Completing Any Phase:**
  ```powershell
  .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
  ```
  All unit tests, Android Lint checks, and APK assembly must pass with zero errors after the final edit of every phase.

---

## 6. Skills Registry

Agents working on this project should load the appropriate skill before touching that area:

| Area | Skill Path / Name | When to Load |
| :--- | :--- | :--- |
| **Alarms, Notifications, Boot & Vibration** | `.agents/skills/android-escalating-alarms/SKILL.md` | Modifying `AlarmScheduler`, receivers, `AlarmRingingService`, or `AlarmActivity` |
| **Room DB, Punch Logic, CSV & JSON Backup** | `.agents/skills/android-offline-time-tracking/SKILL.md` | Modifying entities, DAOs, duration math, overnight shifts, or exporters |
| **Compose UI, Themes, Charts & Previews** | `.agents/skills/compose-material3-dashboard/SKILL.md` | Building/editing Compose screens, Material 3 tokens, Canvas charts, or `@Preview`s |
| **Visual Taste & Restraint** | `.agents/skills/taste-skill/SKILL.md` | Reviewing or polishing spacing, hairline surfaces, typography hierarchy, and anti-clutter |
| **Micro-Interactions & UI/UX Polish** | `.agents/skills/ui-ux-pro-max/SKILL.md` | Tuning easing curves, press feedback, Bento KPIs, layered charts, and WCAG contrast |
| **Compose Visual Verification** | Global skill `compose-preview` | Rendering and inspecting Compose `@Preview`s |
| **Android Device / Emulator Interaction** | Global skill `android-device-hub` / `android-cli` | Installing debug APK, running Doze/alarm/reboot tests via `adb` |
| **TDD, Debugging & Final Checks** | Global skills `test-driven-development`, `systematic-debugging`, `verification-before-completion` | Writing domain tests, diagnosing failures, or verifying phase completion |
