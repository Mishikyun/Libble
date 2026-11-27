# Design Specification — Libble

> **Status:** Active Implementation · **Target Platform:** Android (Kotlin + Jetpack Compose + Material 3) · **Scope:** Personal Offline-First Time Tracker & Escalating Reminders

---

## 1. Intent & Why

This project exists so the user and their sister can stop forgetting to clock in, take/resume breaks, and clock out on time—without relying on a corporate workforce platform like Jibble. Commercial tools require team workspaces, internet access, and accounts, and their reminders are easy to swipe away and ignore.

**Libble** is a personal, customizable Android app installed directly on each person's phone, featuring a custom horological **L-clock** identity. Every user configures their own work schedule, break rules, and escalating alerts (notification -> vibration -> full lock-screen ringing alarm) so a missed punch becomes nearly impossible.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- **One-tap daily punching:** Support **Clock In -> Optional Break / Resume -> Clock Out** with a live elapsed timer.
- **Unmissable, polite-first reminders:** Two-stage escalating alerts per event (Time In, Break End, Time Out)—starting as a gentle notification with inline action buttons (`Clock In`, `Snooze`) and escalating to strong vibration and/or a ringing lock-screen alarm only if the user still has not punched after a configurable grace window.
- **Smart auto-cancellation:** Clocking in early or toggling **Day Off / Skip Today** automatically cancels pending reminders and alarms for that event.
- **Visual clarity:** Dashboard with today's status, live worked hours, and a **Week / Month bar graph** against a daily target line (e.g., 8.0h/day) to highlight undertime, overtime, and break durations.
- **Forgiving correction:** Manual edit and "Add Missed Punch" flows so forgotten days can be fixed cleanly.
- **Zero-account ownership:** 100% offline-first on-device storage with RFC-4180 CSV timesheet export and portable JSON backup/restore.

### Non-Goals (v1)
- Cloud sync, user accounts, login/registration, or remote server backends.
- Team management, manager approvals, GPS geofencing, selfie verification, or payroll/billing calculations.
- iOS or web builds in v1 (revisited separately if needed later).
- Direct integration or automation against Jibble's private APIs or UI.

### Anti-Goals (Failure Modes to Actively Prevent)
- **Silent alarm failures:** Alarms skipped because of Android Doze mode, missing exact-alarm permissions, or phone reboots.
- **Nagging alarms after action:** An alarm still ringing when the user already clocked in early or marked the day off.
- **Corporate UX clutter:** Multi-step forms, enterprise jargon, or buried controls when the user just wants to tap Clock In in under 2 seconds.

---

## 3. Approaches Considered & Chosen

| Approach | Summary | Trade-offs | Decision |
| :--- | :--- | :--- | :--- |
| **1. Native Android (Kotlin + Jetpack Compose + Room)** | Pure Android Material 3 UI, custom Compose `Canvas` bar chart, Room SQLite, and direct `AlarmManager.setAlarmClock()`, `FullScreenIntent`, `VibrationEffect`, and `BOOT_COMPLETED`. | Android-only; a future iOS app would be a separate codebase. Zero extra SDKs needed; smallest APK (~6–10 MB); best-in-class alarm reliability. | **CHOSEN** |
| **2. React Native + Expo (Custom Dev Build)** | TypeScript/React UI with SQLite and custom Kotlin native modules for alarms/boot. | Future iOS code sharing, but still requires maintaining native Kotlin alarm/foreground services in v1 plus JS-bridge & build overhead. | Rejected for v1 |
| **3. Capacitor (WebView + Native Alarm Plugin)** | Web UI inside Android WebView backed by a custom Kotlin alarm plugin. | Fast web UI iteration, but heavier startup and the core alarm engine still has to be written natively in Kotlin. | Rejected for v1 |
| **4. Flutter** | Cross-platform Dart UI + native Android alarm platform channels. | Flutter/Dart SDK is not installed on the target workstation, and lock-screen alarms still require native Kotlin code. | Dropped |

*Future iOS Note:* Keeping business rules isolated in pure domain models and exporting standard CSV/JSON schemas ensures schedules and historical logs remain portable if an iOS companion is ever built later.

---

## 4. Visual Design System & Accessibility

### Theme Modes
- **User-selectable:** `System Default` (follows Android OS setting), `Light`, or `Dark`.
- No hardcoded colors anywhere in composables; all surfaces, text, borders, and charts read from `MaterialTheme.colorScheme` and a custom `WorkHoursExtendedColors` `CompositionLocal`.

### Color Palette (Material 3 Harmonious Teal-Slate + Semantic Status)
Designed for >= 4.5:1 WCAG 2.2 AA text contrast in both Light and Dark themes:

| Token | Light Mode | Dark Mode | Purpose |
| :--- | :--- | :--- | :--- |
| `primary` | `#0F766E` (Deep Teal) | `#2DD4BF` (Bright Teal) | Primary actions, active tab, chart target bars |
| `onPrimary` | `#FFFFFF` | `#042F2E` | Text/icons on primary buttons |
| `secondary` | `#4338CA` (Indigo) | `#A5B4FC` (Soft Indigo) | Break/resume accents & secondary highlights |
| `background` | `#F8FAFC` (Cool Slate) | `#0B1117` (Deep Slate) | Screen canvas |
| `surface` | `#FFFFFF` | `#131B24` | Cards, bottom sheets, top bars |
| `surfaceVariant` | `#E2E8F0` | `#1E293B` | Unfilled tracks, subtle dividers |
| `onSurface` | `#0F172A` | `#F1F5F9` | Primary body & title text |
| `onSurfaceVariant` | `#475569` | `#94A3B8` | Subtitles, timestamps, axis labels |
| `statusClockedIn` | `#059669` (Emerald) | `#34D399` | Active shift badge & live indicator |
| `statusOnBreak` | `#D97706` (Amber) | `#FBBF24` | On-break badge & break bars |
| `statusClockedOut` | `#64748B` (Slate) | `#94A3B8` | Completed / idle state |
| `statusAlarm` / `error` | `#DC2626` (Coral Red) | `#F87171` | Ringing alarm, missed punch, undertime alert |

### Typography & Numeric Precision
- Material 3 type scale using system default sans-serif (`Roboto`) with crisp weight hierarchy (`headlineMedium` for live timer, `titleMedium` for card headers, `labelLarge` for actions).
- **Tabular numerals (`fontFeatureSettings = "tnum"`):** Required on every live clock, elapsed duration (`04:12:38`), schedule time (`08:00 AM`), and chart metric so digits never jitter as seconds tick.

### Motion & Interaction Feedback
- **Tactile press state:** Primary punch buttons and quick chips animate scale to `0.97f` on press (`animateFloatAsState` with `tween(durationMillis = 120, easing = FastOutSlowInEasing)`).
- **Duration discipline:** All UI transitions, sheet reveals, and chart bar updates stay under `300ms` using `EaseOut` / spring physics (`Spring.DampingRatioNoBouncy`, `Spring.StiffnessMediumLow`). Never use `EaseIn` on UI transitions.
- **Popovers & dialogs:** Animate scale from `0.95f` -> `1.0f` with alpha fade (never from `0.0f`).
- **Reduced motion:** Respect Android `ANIMATOR_DURATION_SCALE == 0f` by snapping transitions cleanly.

### Accessibility (WCAG 2.2 AA & TalkBack)
- Minimum `48dp x 48dp` interactive touch target on all buttons, chips, switches, and chart day bars.
- Status states always pair color with an icon and explicit text label (`"Clocked In"`, `"On Break"`, `"Day Off"`).
- Custom `Canvas` charts expose explicit `semantics { contentDescription = ... }` per day bar and a summary announcement for TalkBack.

---

## 5. Navigation & Screen Architecture

Top-level **4-tab Material 3 `NavigationBar`**:
1. **Dashboard** — Daily punch controls, live elapsed timer, Skip Today toggle, Week/Month hours bar graph.
2. **History** — Calendar/month list of all logs, missed/overnight warnings, manual punch editor, CSV export.
3. **Schedule** — Weekly work hours (e.g., Mon–Fri 8:00 AM–5:00 PM), per-day overrides, Day Off / Holiday dates, and Escalating Reminder settings.
4. **Settings** — Theme selector, Permissions Health check + Test Alarm, Auto-deduct lunch fallback, JSON Backup/Restore, CSV Export.

Plus two non-tab modal/dedicated surfaces:
- **First-Launch Onboarding Flow** (3 quick steps: Default Schedule -> Permissions Grant -> Done).
- **Full-Screen `AlarmActivity`** (lock-screen overlay when a Stage 2 alarm rings).

---

## 6. Screen Specifications & Wireframes

### 6.1 Dashboard Screen
```text
+------------------------------------------+
| WorkHours                  [Skip Today]  |
+------------------------------------------+
| +--------------------------------------+ |
| | ● CLOCKED IN · Since 8:02 AM         | |
| |                                      | |
| |           04:18:22                   | |
| |     Worked today · Break: 00:00      | |
| |                                      | |
| |  [  Start Break  ]  [  Clock Out  ]  | |
| +--------------------------------------+ |
|                                          |
| Next alert: Time Out @ 5:00 PM (Gentle)  |
|                                          |
| Hours Overview       [ Week | Month ]    |
| +--------------------------------------+ |
| | 32.4h / 40.0h target · Avg 8.1h/day  | |
| |                                      | |
| | 10h |                                | |
| |  8h | - - - - - - - - - - - - target | |
| |  4h |  ██   ███  ██   ███  ░░        | |
| |  0h +--M----T----W----T----F---S--S--+ |
| |     (Tap a bar to view day details)  | |
| +--------------------------------------+ |
+------------------------------------------+
| [Dashboard] [History] [Schedule] [Set.]  |
+------------------------------------------+
```
- **Overnight open-session banner:** If yesterday's shift was never clocked out, a prominent amber/red banner appears at the top: `"Open shift from Nov 24 (8:00 AM) — Close yesterday's shift"` opening the manual edit sheet before starting today.
- **State-driven primary buttons:**
  - `NOT_STARTED` -> Single large **Clock In** button (`56dp` height).
  - `CLOCKED_IN` -> **Start Break** (secondary) + **Clock Out** (primary).
  - `ON_BREAK` -> **Resume Work** (primary, showing live break timer) + **Clock Out** (outlined).
  - `COMPLETED` -> Summary card (`Worked 8h 12m · Break 58m`) + **Re-open / Edit Today** secondary button.
  - `DAY_OFF` -> Calm Slate card (`Scheduled Day Off — Reminders Paused`) + **Work Today Anyway** button.
- **Interactive Bar Graph (`Canvas`):**
  - **Week view:** Mon–Sun bars with dashed horizontal target line (e.g., 8h), color-coded (Teal = met/over target, Amber = undertime/in-progress, Grey = day off). Tapping any bar displays a compact tooltip (`Tue Nov 24: 8h 30m worked · 1h 00m break`) with a link to edit.
  - **Month view:** Scrollable or grouped daily bars for the selected month + monthly total, average hours/day, and overtime/undertime delta.

### 6.2 History & Manual Edit Screen
```text
+------------------------------------------+
| History               [< Nov 2026 >] [CSV]|
+------------------------------------------+
| Total: 152.5h · Days: 19 · Avg: 8.03h    |
+------------------------------------------+
| [+ Add Missed Day]                       |
|                                          |
| Wed, Nov 25  [CLOCKED IN]                |
| In 8:02 AM · Active now                  |
|                                          |
| Tue, Nov 24  [8h 30m]                    |
| In 7:58 AM · Out 5:28 PM · Break 1h 00m  |
|                                          |
| Mon, Nov 23  [MISSED - Tap to fix]       |
| Scheduled 8:00 AM–5:00 PM · No punches   |
+------------------------------------------+
```
- Tapping any day card (or `+ Add Missed Day`) opens a Material 3 **`ModalBottomSheet` Editor**:
  - Edit `Clock In` time, `Clock Out` time, add/edit/remove `Break` intervals, mark `Day Off`, or add an optional short note (e.g., `"Forgot to punch morning"`).
  - Live validation prevents `Clock Out < Clock In` or breaks outside the shift window, showing clear inline error text.

### 6.3 Schedule & Escalating Reminders Screen
```text
+------------------------------------------+
| Schedule & Alerts                        |
+------------------------------------------+
| Default Shift: 8:00 AM – 5:00 PM         |
| Target: 8h 00m · Lunch: 1h 00m           |
| Active Days: [M][T][W][T][F][ ][ ]       |
| (Tap a day to customize individually)    |
+------------------------------------------+
| Upcoming Day-Off / Holiday Dates  [+ Add]|
| • Nov 30, 2026 (Bonifacio Day)       [x] |
+------------------------------------------+
| Escalating Reminders                     |
|                                          |
| TIME IN (8:00 AM)                  [ON]  |
|  Stage 1: Gentle notif at 8:00 AM        |
|  Stage 2: If still not clocked in +10m   |
|           -> [Full Alarm + Vibrate v]    |
|                                          |
| BREAK END (After 60m on break)     [ON]  |
|  Stage 1: Gentle notif at +55m           |
|  Stage 2: If still on break +5m -> Vib.  |
|                                          |
| TIME OUT (5:00 PM)                 [ON]  |
|  Stage 1: Gentle notif at 5:00 PM        |
|  Stage 2: If still clocked in +10m       |
|           -> [Vibrate + Sound v]         |
+------------------------------------------+
```
- **Per-Event Escalation Picker:** Each reminder (`Time In`, `Break End`, `Time Out`) lets the user customize:
  - Stage 1 lead offset (`At time`, `5 min before`, `10 min before`, `15 min before`).
  - Stage 2 grace delay (`Off`, `5 min`, `10 min`, `15 min`, `30 min`).
  - Stage 2 escalation intensity (`Strong Vibration`, `Ring + Vibrate`, `Full Lock-Screen Alarm`), vibration pattern (`Pulse`, `Heartbeat`, `Urgent`), alarm ringtone (system alarm picker or bundled gentle chime), and snooze length (`5m`, `10m`, `15m`).

### 6.4 Full-Screen Lock-Screen Alarm (`AlarmActivity`)
```text
+------------------------------------------+
|         WORKHOURS REMINDER               |
|                                          |
|              08:10 AM                    |
|                                          |
|       Time to Clock In!                  |
|   Scheduled at 8:00 AM (10m overdue)     |
|                                          |
|                                          |
|      [     CLOCK IN NOW     ]            |
|        (Stops alarm & logs)              |
|                                          |
|   [ Snooze 5m ]         [ Dismiss ]      |
+------------------------------------------+
```
- Appears directly over the lock screen (`setShowWhenLocked(true)`, `setTurnScreenOn(true)`).
- **CLOCK IN NOW** (or **RESUME WORK** / **CLOCK OUT NOW** depending on the event) immediately records the timestamp in Room, stops the ringtone/vibration, and cancels any remaining reminders for that event.

### 6.5 Settings Screen
- **Appearance:** Theme (`System`, `Light`, `Dark`).
- **Permissions & Alarm Health Card:** Live green/amber status badges for:
  - Notifications (`POST_NOTIFICATIONS`)
  - Exact Alarms (`SCHEDULE_EXACT_ALARM`)
  - Full-Screen Lock Alerts (`USE_FULL_SCREEN_INTENT`)
  - Battery Optimization (`Ignore Battery Optimizations` guidance + shortcut to Android settings)
  - **`Test Escalation Alert`** button (fires a 5-second test notification/alarm so the user and their sister can confirm their phone volume and lock screen work before Monday morning).
- **Break Behavior:** Toggle `"Auto-deduct default break (1h) if I forget to tap Start Break"` (off by default, or user-enabled per preference).
- **Data Portability:**
  - `Export Timesheet (CSV)` (date range selector: This Week / This Month / All Time).
  - `Backup All Data (JSON)` and `Restore from Backup (JSON)`.

---

## 7. What's Left to the Builder
- Final app display name and launcher icon artwork (currently using placeholder working name `WorkHours` and clean vector clock+bell adaptive icon).
- Fine-tuning chart bar corner radii, spacing, and typography contrast across Light/Dark `@Preview`s during implementation.
- Selecting one bundled default alarm chime OGG/MP3 asset (CC0/royalty-free) in `res/raw/` as fallback if the device's default alarm tone URI is silent or unset.
