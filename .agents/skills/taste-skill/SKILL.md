---
name: taste-skill
description: Use when designing, reviewing, or refining Compose UI screens, spacing, color surfaces, typography hierarchy, or visual polish to eliminate generic boilerplate styling.
---

# Taste Skill

## Overview
Visual taste comes from restraint, crisp hairline structure, generous breathing room, and intentional typography—not heavy drop shadows, neon gradients, or decorative emoji clutter.

## Core Principles

### 1. Hairline Tonal Surfaces Over Heavy Shadows
- Prefer flat tonal elevation (`elevation = 0.dp`) paired with a crisp `1.dp` border (`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`) over stacked grey drop shadows.
- Separate major chrome (`TopAppBar`, `NavigationBar`) with a clean `1.dp` `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)`.

### 2. Typography Hierarchy & Negative Tracking
- Apply subtle negative letter-spacing on large display and headline titles (`-0.8.sp` on `displaySmall`, `-0.4.sp` on `headlineMedium`) for an editorial, engineered look.
- Use `fontFeatureSettings = "tnum"` on every timer, duration, schedule time, and KPI number so columns and ticking digits stay rock-solid.
- Keep copy ultra-concise: cut explanatory filler subtitles when the label and value already speak for themselves.

### 3. Single Focal Accent & Zero Emoji Clutter
- Use at most one primary accent (`MaterialTheme.colorScheme.primary`) per focal screen state; keep secondary cards and metadata neutral (`surface` / `onSurfaceVariant`).
- Never place decorative emojis in headings, buttons, or empty states—use crisp geometric icons or clean typography instead.

### 4. Spacing Rhythm (4dp Grid) & Contrast
- Align all padding, gaps, and radii to a `4.dp` scale (`8`, `12`, `16`, `20`, `24.dp`; `14.dp`–`20.dp` card corners).
- Verify >= 4.5:1 text contrast in both Light (`#F8FAFC` canvas, `#FFFFFF` surface) and Dark (`#090E14` canvas, `#111822` surface) modes.

### 5. Control Clarity & Vertical Rail Alignment
- **Single Control Per Navigation Row:** Do not combine a master `Switch` on a row that opens a configuration sheet; show concise `On / Off >` status on the outer row and house the master switch inside the sheet.
- **Clickable Time Affordances:** Style editable time triggers as recessed tinted pills with a trailing chevron (`>`), avoiding ambiguous static boxes.
- **Two-Rail List & Uniform Permission Pills:** Align list rows on consistent left/right rails (metadata left; `tabular()` metric + `StatusBadge` right + centered `>`), and terminate Settings/Onboarding permission rows with uniform flush-right rounded status/action pills (`✓ Ready`, `Enable`, `Run`) and single-line subtitles.
