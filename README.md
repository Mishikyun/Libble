# Libble

**A 100% offline-first Android work-hours tracker and two-stage escalating shift alarm app.**

Built with Jetpack Compose, Material 3, Room, and native `AlarmManager` (`com.personal.workhours` · `v1.1.0`). Zero internet permissions, zero tracking, and complete portability via CSV exports and validated JSON backups.

---

## Features

- **Live Shift & Break Tracking:** One-tap **Clock In**, **Start Break**, **Resume Work**, and **Clock Out** with a real-time tabular (`tnum`) timer, automatic midnight rollover, overnight open-shift detection, and **Day Off** overrides.
- **Dashboard & Weekly Progress:**
  - **This Week** live progress card tracking worked hours against weekly targets, percentage completion, pace/overtime delta, and worked days.
  - Custom Compose `Canvas` **Week** (Mon–Sun) and **Month** bar charts featuring a dashed daily target line and interactive tap tooltips (`Worked`, `Break`, `Delta`).
  - Activity summary tiles for total hours, daily average, and break totals.
- **History, Activity Heatmap & Shift Editor:**
  - Monthly Mon–Sun activity heatmap highlighting worked days and today's ring.
  - Vertically aligned daily shift log list with swipe-left-to-delete, confirmation prompt, and lossless **Undo** snackbar.
  - Bottom-sheet editor with a **3-column scrollable digital wheel time picker** (`Hour : Minute  AM/PM`), schedule-aware break defaults, and strict non-overlapping break validation.
- **Two-Stage Escalating Reminders:**
  - Per-weekday shift schedules with configurable **Time In**, **Break Start**, **Break End**, and **Time Out** alerts.
  - **Stage 1:** Early gentle heads-up notification before an event.
  - **Stage 2:** Escalating vibration, sound, or full-screen lock-screen alarm (`setAlarmClock` + `USAGE_ALARM`), complete with snooze, direct punch actions, DB-guarded suppression if you already punched in/out or took a Day Off, and automatic rescheduling after reboot or timezone changes.
- **Privacy & Data Portability:**
  - Strictly offline (**zero `INTERNET` permissions**, `android:allowBackup="false"`).
  - **CSV Export:** RFC-4180 monthly timesheet export via Android Storage Access Framework (`CreateDocument`) with spreadsheet formula-injection neutralization.
  - **JSON Backup & Restore:** Full-state export and atomic Room restore guarded by schema version checks, size caps (`5 MB`), work-day/length bounds, timestamp validation, and allowlisted sound URI schemes.
- **Material 3 Light & Dark UI:** Supports **System**, **Light**, and **Dark** themes with hairline tonal `InsetGroup` cards, tabular numerals, and accessible contrast.

---

## Architecture & Project Structure

Libble follows a clean 4-layer architecture inside `:app` with manual `AppContainer` dependency injection:

```text
app/src/main/java/com/personal/workhours/
├── domain/        # Pure JVM Kotlin business logic (no android.* imports, Clock-injected)
│   ├── model/     # Domain records, schedules, reminder configs, backup payload
│   ├── export/    # CsvExporter (formula-safe), BackupSerializer (strict validation)
│   └── ...        # WorkDurationCalculator, WorkDayStateResolver, ScheduleResolver, EditWorkDayValidator
├── data/          # Persistence & repositories
│   ├── local/     # Room database (v2 + MIGRATION_1_2 + exported schemas), DAOs, entities
│   ├── preferences/ # Jetpack DataStore settings & onboarding flags
│   └── repository/  # WorkLogRepository, ScheduleRepository, AlarmReconciler
├── alarm/         # AlarmManager scheduling, foreground audio/vibration service, receivers & Lock Screen UI
└── ui/            # Jetpack Compose Material 3 screens, ViewModels, Canvas charts & wheel pickers
    ├── dashboard/ # DashboardScreen, DashboardViewModel, HoursBarChart
    ├── history/   # HistoryScreen, HistoryViewModel, EditWorkDaySheet
    ├── schedule/  # ScheduleScreen, ScheduleViewModel
    ├── settings/  # SettingsScreen, SettingsViewModel
    ├── onboarding/# First-run guided setup
    ├── navigation/# NavigationBar & App scaffold
    ├── components/# InsetGroup, Pickers (digital wheel), StatusBadge, TactileButton, LibbleLogo
    └── theme/     # Material 3 Light/Dark color schemes, typography, motion curves
```

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Build** | Kotlin `2.3.0`, Android Gradle Plugin `9.0.1` (built-in Kotlin), Gradle `9.1.0`, KSP `2.3.6` |
| **UI** | Jetpack Compose (BOM `2026.02.00`), Material 3, Navigation Compose `2.9.0`, Lifecycle `2.9.0` |
| **Storage** | Room `2.8.1` (SQLite, schema v2), Jetpack DataStore Preferences `1.1.7` |
| **Async & Serialization** | Kotlinx Coroutines `1.10.2`, Kotlinx Serialization JSON `1.8.1` |
| **SDK Targets** | `minSdk = 26` (Android 8.0+), `targetSdk = 35`, `compileSdk = 36`, Java 17 target |
| **Testing** | JUnit 4 (`4.13.2`), Kotlinx Coroutines Test, Room Testing |

---

## Security & Permissions

Libble is engineered to keep personal shift records private on-device:
- **No Network Access:** `INTERNET` and `ACCESS_NETWORK_STATE` are intentionally **not** declared.
- **No Silent Cloud Backups:** `android:allowBackup="false"` prevents automatic cloud extraction outside your manual CSV/JSON exports.
- **No Restricted Sideload Overlay Permission:** Uses native `USE_FULL_SCREEN_INTENT` and notification channels rather than `SYSTEM_ALERT_WINDOW`.
- **Internal Component Isolation:** Internal services, receivers, and `AlarmActivity` are marked `android:exported="false"`, `PendingIntent`s use `FLAG_IMMUTABLE`, and release builds enable R8 minification & resource shrinking.

Declared permissions in [AndroidManifest.xml](app/src/main/AndroidManifest.xml):
- `POST_NOTIFICATIONS` — Reminder heads-up and active alarm notifications.
- `SCHEDULE_EXACT_ALARM` — Exact shift and break reminder timing.
- `USE_FULL_SCREEN_INTENT` — Lock-screen alarm display (`AlarmActivity`).
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Reliable looping alarm sound/vibration service.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — Optional Doze exemption so alarms fire reliably on OEM devices.
- `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK` — Rescheduling after reboot and vibrating/waking during active alerts.

---

## Getting Started & Build Commands

### Prerequisites
- JDK 17+ (built with OpenJDK 17+ / 25)
- Android SDK with platform `36` (`compileSdk = 36`)
- Optional for signed release builds: copy `keystore.properties.example` to `keystore.properties` and configure your signing keystore (already gitignored).

### Build, Lint & Test

**Windows (PowerShell):**
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

**macOS / Linux:**
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

**Release APK (minified & resource-shrunk):**
```powershell
.\gradlew.bat assembleRelease
```

### Installing via ADB
On OPPO / ColorOS devices, pass `--no-streaming` to avoid streamed install hangs:
```powershell
adb install -r --no-streaming app\build\outputs\apk\debug\app-debug.apk
```

---

## Testing

Pure-JVM unit tests cover core business rules, alarm resolution, CSV formula safety, and JSON backup validation under `app/src/test/java/com/personal/workhours/domain/` (21 unit tests across 6 suites):
- `WorkDurationCalculatorTest`
- `WorkDayStateResolverTest`
- `ScheduleResolverTest`
- `EditWorkDayValidatorTest`
- `CsvExporterTest`
- `BackupSerializerTest`

---

## Documentation

Project specs, architecture notes, and engineering guidelines live in [`docs/`](docs):
- [Design.MD](docs/Design.MD) — Visual design system and screen specifications
- [AppFlow.MD](docs/AppFlow.MD) — User journeys and navigation flows
- [SystemDesign.MD](docs/SystemDesign.MD) — Architecture, Room schema, and alarm request-code design
- [TechnicalRequirements.MD](docs/TechnicalRequirements.MD) — Core functional and non-functional requirements
- [implementation.MD](docs/implementation.MD) — Implementation plan and build notes
- [ProjectRules.MD](docs/ProjectRules.MD) — Codebase engineering and security standards
