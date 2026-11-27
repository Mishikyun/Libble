---
name: compose-material3-dashboard
description: Use when building or editing Jetpack Compose screens, Light and Dark Material 3 themes, live work timers, custom Canvas bar charts, or Compose previews in this Android project.
---

# Compose Material 3 Dashboard

## Overview
UI screens in WorkHours prioritize glanceability, crisp live timers, accessible Light/Dark Material 3 contrast, and custom Canvas charts without heavy third-party dependencies.

## Core Rules

### 1. Theme & Semantic Tokens
- Never hardcode `Color(0x...)` inside screen or component composables. Read exclusively from `MaterialTheme.colorScheme` and `WorkHoursExtendedColors` (`statusClockedIn`, `statusOnBreak`, `statusClockedOut`, `statusAlarm`).
- Support `System Default`, `Light`, and `Dark` theme modes seamlessly and verify >= 4.5:1 WCAG AA contrast on all text and status badges.
- Pair status colors with both an icon and explicit label text (`"Clocked In"`, `"On Break"`, `"Day Off"`).

### 2. Tabular Numerals & Touch Targets
- Apply `fontFeatureSettings = "tnum"` to every live timer, elapsed break counter, schedule time, and KPI number so digits never shift horizontally as seconds tick.
- Enforce a minimum `48dp x 48dp` touch target on all buttons, filter chips, switches, and tappable chart bars.

### 3. Motion & Tactile Feedback
- Primary punch buttons scale to `0.97f` on press using `animateFloatAsState` (`tween(120, easing = FastOutSlowInEasing)`).
- Keep all screen/sheet/bar animations under `300ms` with `EaseOut` or non-bouncy spring physics—never use `EaseIn` or `scale(0f)` pop-ins (start popovers from `0.95f`).
- **Digital Wheel Time Picker:** Prefer the 3-column snap drum (`Hour : Minute  AM/PM` with top/bottom surface fade gradients and haptic ticks) in `WorkTimePickerDialog` over radial dials for shift and break editing.
- **Swipe-to-Delete Gate:** Gate destructive swipe actions (`SwipeToDismissBox` at ~`0.42f` threshold with a haptic tick and immediate `dismissState.reset()`) behind a confirmation `AlertDialog` and pair removal with an Undo snackbar.
- **Schedule-Aware Form Defaults:** Default newly added manual breaks in edit sheets to the day's configured `breakStartMinuteOfDay` and `defaultBreakMinutes` (falling back to `12:00 PM` / `60m` only if unset).

### 4. Custom Compose `Canvas` Bar Charts
- Draw Week (Mon–Sun) and Month bar charts directly with Compose `Canvas` + `drawText` / `TextMeasurer` (no third-party chart library).
- Render a dashed horizontal target-hours reference line across the bars.
- Support tap hit-testing (`pointerInput(detectTapGestures)`) to display an inline day tooltip (`Worked`, `Break`, `Delta`).
- Add `semantics { contentDescription = ... }` so TalkBack announces the period summary and per-bar values cleanly.

### 5. Dual `@Preview` Requirement & Verification
- Every screen and card composable must declare both Light and Dark `@Preview` functions (`@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)` and `UI_MODE_NIGHT_YES`) populated with realistic sample data.
- When visually inspecting UI changes, load the global `compose-preview` skill.

## Common Mistakes
- **Mistake:** Collecting `Flow` with `collectAsState()` instead of `collectAsStateWithLifecycle()` -> leaks timer/DB collection in background.
- **Mistake:** Updating a timer inside Room every second -> compute live elapsed time in the UI/ViewModel from `clockInEpochMs` and `Clock.millis()` via a lightweight `LaunchedEffect` ticker only while `Lifecycle.State.STARTED`.
