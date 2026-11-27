# Technical Requirements — WorkHours

> **Platform:** Android · **Language:** Kotlin · **UI:** Jetpack Compose (Material 3) · **Distribution:** Direct Signed APK (Sideload)

---

## 1. SDK, Language & Toolchain Matrix

| Component | Target / Version | Notes |
| :--- | :--- | :--- |
| **Minimum SDK (`minSdk`)** | `26` (Android 8.0 Oreo) | Native `NotificationChannel`, `VibrationEffect`, and `java.time` without desugaring complexity |
| **Target SDK (`targetSdk`)** | `35` (Android 15) or `36` | Compliant with modern `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, and foreground service types |
| **Compile SDK (`compileSdk`)** | `36` (stable) | See Workstation Environment Note below |
| **Language** | Kotlin `2.1.x` | Compose Compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) |
| **Annotation Processing** | KSP (`2.1.x-...`) | Used by Room compiler (avoid legacy `kapt`) |
| **Android Gradle Plugin (AGP)** | `8.9.x` / `8.10.x` | Supports Java 21/25 toolchain & Kotlin 2.1 |
| **Gradle Wrapper** | `8.11.1+` / `8.13` | Committed wrapper (`gradlew`, `gradlew.bat`) for reproducible builds |

### Workstation Environment Note (Verified)
- **Installed Java:** `C:\Program Files\Android\Android Studio\jbr` (`OpenJDK 25.0.3`). Ensure `gradle.properties` sets `org.gradle.java.home` or uses a compatible Gradle/Java toolchain (`JvmTarget.JVM_17` bytecode target) so AGP compiles cleanly without JDK 25 bytecode mismatches.
- **Installed Android SDK:** `C:\Users\lance\AppData\Local\Android\Sdk` currently has `build-tools/36.0.0`, `platform-tools` (`adb`), and `platforms/android-37.0` (preview). Before Phase 0 Gradle sync, install stable **`platforms;android-36`** (or `35`) via Android Studio SDK Manager or `sdkmanager "platforms;android-36"` if Gradle requests `android-36`.

---

## 2. Dependencies (Minimal & Purpose-Built)

| Library | Artifact / Group | Purpose |
| :--- | :--- | :--- |
| **AndroidX Core KTX** | `androidx.core:core-ktx` | Compat APIs (`NotificationCompat`, `ContextCompat`, `VibratorManager`) |
| **Lifecycle & ViewModel** | `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | Lifecycle-aware `collectAsStateWithLifecycle()` & `ViewModel` |
| **Activity Compose** | `androidx.activity:activity-compose` | `enableEdgeToEdge()`, SAF `CreateDocument`/`OpenDocument` launchers |
| **Jetpack Compose BOM** | `androidx.compose:compose-bom` | Material 3 (`material3`), Icons Extended, Foundation, UI Tooling & Preview |
| **Navigation Compose** | `androidx.navigation:navigation-compose` | Type-safe bottom-bar tab navigation + onboarding gate |
| **Room SQLite + KSP** | `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (KSP) | Local persistence, reactive `Flow` queries, `@Transaction` safety |
| **DataStore Preferences** | `androidx.datastore:datastore-preferences` | Lightweight settings (theme mode, onboarding flag, auto-deduct toggle) |
| **Kotlinx Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | Strict versioned JSON backup/restore |
| **Testing Stack** | `junit4`, `kotlinx-coroutines-test`, `androidx.room:room-testing`, `androidx.compose.ui:ui-test-junit4` | Domain TDD, Room in-memory DB tests, Compose UI flow tests |

*Explicitly excluded to keep APK small and code simple:* Hilt/Dagger, Retrofit/OkHttp, WorkManager (redundant for exact-minute alarms where `AlarmManager` + `BootReceiver` is required), and external charting libraries (Compose `Canvas` handles bar charts natively).

---

## 3. Android Permissions Matrix

| Permission | API Level | Necessity | User UX |
| :--- | :--- | :--- | :--- |
| `POST_NOTIFICATIONS` | 33+ | **Required** for Stage 1 & Stage 2 notifications | Requested in Onboarding + checked in Settings Health |
| `SCHEDULE_EXACT_ALARM` | 31+ | **Required** for on-the-minute `setExactAndAllowWhileIdle` & `setAlarmClock` | Opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` if denied; falls back gracefully |
| `USE_FULL_SCREEN_INTENT` | 34+ | **Required** for lock-screen `AlarmActivity` overlay | Checked via `NotificationManager.canUseFullScreenIntent()`; links to settings if denied |
| `VIBRATE` | All | **Required** for custom vibration waveforms | Normal install-time permission |
| `WAKE_LOCK` | All | **Required** to keep CPU awake briefly during Stage 2 ringing | Bounded 5-minute timeout in `AlarmRingingService` |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 28+ / 34+ | **Required** for `AlarmRingingService` playing `USAGE_ALARM` audio | Persistent notification while alarm rings |
| `RECEIVE_BOOT_COMPLETED` | All | **Required** to reschedule alarms after phone reboot | Normal install-time permission |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` *(optional intent)* | 23+ | Helpful on OEMs (Xiaomi/Samsung/Oppo) that kill background alarms | Never demanded on startup; optional link in Settings Health card (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) |

**Explicitly Forbidden Permissions:** `INTERNET`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` (use Storage Access Framework instead), `CAMERA`, `READ_CONTACTS`.

---

## 4. Non-Functional Requirements

- **Cold Start Time:** < 600 ms to interactive Dashboard on mid-range Android devices.
- **Release APK Size:** <= 12 MB (expected ~6–9 MB with R8 minification and resource shrinking enabled).
- **Alarm Precision:** Within +/- 15 seconds under Doze mode for `setAlarmClock()` Stage 2 alerts; zero background battery drain (`0%` idle polling—pure event-driven `AlarmManager`).
- **Database Durability:** Room WAL mode (default) + exported schema JSONs in `app/schemas/` for safe future migrations (`version = 1 -> 2`).
- **Localization-Ready Strings:** All UI text in `res/values/strings.xml` so the app name (`app_name`) and labels can be updated in one place.

---

## 5. Build, Signing & Distribution Plan (You & Your Sister)

1. **Local Debug Testing:** `.\gradlew.bat installDebug` or APK sideload over USB/ADB (`adb install -r ...`).
2. **Personal Release Signing:**
   - Generate one personal keystore (`workhours-release.jks`) stored outside git or excluded via `.gitignore`.
   - Load credentials from `keystore.properties` (gitignored) in `app/build.gradle.kts`.
   - Build signed release APK (`.\gradlew.bat assembleRelease`) and share directly with your sister via local file share / Google Drive / USB—both phones install updates cleanly with the same signature without wiping existing logs.
