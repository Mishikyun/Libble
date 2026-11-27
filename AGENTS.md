# Libble (`work-hours-monitoring`) — Workspace Agent Guardrails

Mandatory workspace rules auto-loaded for all tasks under `work-hours-monitoring`. Read [docs/ProjectRules.MD](./docs/ProjectRules.MD) and the matching skill under `.agents/skills/` before editing code.

## Build, Toolchain & Windows Shell
- **PowerShell 5.1 Chaining:** Never use `&&` in terminal commands. Chain dependent commands with `; if ($LASTEXITCODE -eq 0) { ... }`.
- **AGP 9.0.1 Built-In Kotlin:** Never apply `org.jetbrains.kotlin.android` in Gradle scripts; AGP 9 handles Kotlin compilation natively.
- **Room Schema Bump Sequencing:** After incrementing `@Database(version = ...)`, run `.\gradlew.bat :app:kspDebugKotlin` once before running concurrent debug + release KSP builds so Room schema JSON export does not race with an empty EOF schema file.
- **Verification Gate:** Run `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` before claiming any build/test completion.

## Device Deployment (`adb`)
- **ColorOS / OPPO Non-Streaming Install:** Always pass `--no-streaming` when deploying to OPPO devices (e.g., `adb -s <serial> install -r --no-streaming app\build\outputs\apk\debug\app-debug.apk`) to prevent streamed installs from hanging.
- **Sideload Overlay Restriction:** Never re-introduce `SYSTEM_ALERT_WINDOW` ("Pop up over apps"). Android 13+ blocks Restricted Settings toggles on sideloaded APKs; rely on `USE_FULL_SCREEN_INTENT` and high-importance notification heads-up banners instead.

## Project Standards Pointer
- Follow [docs/ProjectRules.MD](./docs/ProjectRules.MD) for strict pure-JVM `domain/` isolation, zero `INTERNET` permission, `allowBackup="false"`, `FLAG_IMMUTABLE`, Light/Dark `@Preview` parity, and skills registry routing.
