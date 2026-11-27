---
name: android-offline-time-tracking
description: Use when designing or modifying Room database schemas, clock-in/break/clock-out state transitions, work duration calculations, overnight shift handling, or CSV and JSON export/backup code.
---

# Android Offline Time Tracking

## Overview
Personal time logs must survive timezone changes, missed punches, app kills, and file exports without corrupting totals or leaking spreadsheet formulas.

## Core Patterns

### 1. Time Representation & DST Safety
- Store timestamps as **UTC epoch milliseconds (`Long`)** alongside a logical **`workDate` (`YYYY-MM-DD` ISO-8601)** resolved at punch time.
- Inject `java.time.Clock` into all domain calculators, state resolvers, and schedule resolvers so JVM unit tests remain 100% deterministic without `Thread.sleep`.

### 2. Transactional State Invariants
Wrap every punch and manual edit in Room `@Transaction`:
- **Single active shift:** `clockIn(now)` is a no-op if any `work_day` already has `clockOutEpochMs == null`.
- **Single active break:** `startBreak(now)` requires an open `work_day` and zero open `break_sessions`.
- **Clean clock-out:** `clockOut(now)` automatically sets `endEpochMs = now` on any open `break_session` before closing the `work_day`.
- **Reconcile alarms after commit:** Trigger `alarmScheduler.reconcileAll()` right after the transaction finishes—never before.
- **Lossless Delete Undo:** When deleting a logged `WorkDayRecord`, keep a snapshot in memory and provide `restoreWorkDay(snapshot)` inside a `@Transaction` for Snackbar Undo rather than adding soft-delete columns.
- **Room Schema Version Bumps:** After incrementing `@Database(version = ...)`, run `:app:kspDebugKotlin` once before running concurrent debug + release builds so Room schema JSON export (`<n>.json`) does not hit EOF races.

### 2b. Midnight Date Rollover in ViewModels
- Never capture `today` statically when constructing `StateFlow` queries (`observeWorkDay(today)`). Wrap date-scoped queries in `activeDate.flatMapLatest { currentDay -> ... }` and call `refreshDateIfNeeded()` on `Lifecycle.Event.ON_RESUME` and minute ticks.

### 3. Duration Math & Overnight Shifts
- `grossMs = (clockOutEpochMs ?: nowEpochMs) - clockInEpochMs`
- `manualBreakMs = sum((endEpochMs ?: nowEpochMs) - startEpochMs)`
- `effectiveBreakMs = if (manualBreakMs > 0) manualBreakMs else autoDeductBreakMinutes * 60_000L`
- `netWorkMs = max(0L, grossMs - effectiveBreakMs)`
- If a prior date (`workDate < today`) still has `clockOutEpochMs == null`, surface an explicit unresolved-shift banner on the Dashboard instead of silently discarding or auto-closing it.

### 4. Manual Edit Validation
Reject edits unless all hold:
- `clockOutEpochMs > clockInEpochMs` (when clocked out).
- Each break satisfies `startEpochMs >= clockInEpochMs`, `endEpochMs > startEpochMs`, and `endEpochMs <= clockOutEpochMs`.
- Break intervals do not overlap each other.

### 5. Safe CSV & JSON Portability
- **CSV (RFC-4180):** Neutralize spreadsheet formula injection by prefixing `'` to any string cell starting with `=`, `+`, `-`, `@`, `\t`, or `\r` before quoting. Use Storage Access Framework (`CreateDocument("text/csv")`)—never request broad storage permissions.
- **JSON Backup/Restore:** Include `"schemaVersion"`. On restore, enforce a `5 MB` cap, `MAX_WORK_DAYS` (`5_000`), `MAX_REASON_LENGTH` (`120`), schema version, ISO date strings, schedule/reminder timing bounds, allowlisted `soundUri` schemes (`content://`, `android.resource://`), and timestamp ordering in memory **before** replacing Room tables inside one atomic `@Transaction`.

## Common Mistakes
- **Mistake:** Using raw SQL string concatenation for date filters -> always bind `:workDate` parameters in `@Query`.
- **Mistake:** Letting `allowBackup="true"` silently upload logs to Google Drive -> set `android:allowBackup="false"` in `AndroidManifest.xml`.
