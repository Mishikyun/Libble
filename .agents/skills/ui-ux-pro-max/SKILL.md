---
name: ui-ux-pro-max
description: Use when implementing Compose micro-interactions, easing curves, press feedback, Bento KPI cards, Canvas data charts, or accessibility and touch-target polish.
---

# UI/UX Pro Max

## Overview
Production-grade mobile UX combines Emil Kowalski's crisp `ease-out` motion physics, tactile press feedback, layered data visualizations, and strict WCAG 2.2 touch/contrast guarantees.

## Core Standards

### 1. Motion Physics & Easing
- **Signature Curve:** Use `EaseOutExpo = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)` for enters, exits, and chart animations. Never use `ease-in` on UI interactions.
- **Fast Timing:** Keep interactive transitions under `300ms` (`120–140ms` press feedback, `220–280ms` screen/step transitions).
- **Never Animate From `scale(0)`:** Start scale reveals at `0.95f` paired with alpha fade (`0f -> 1f`).
- **Tactile Press Feedback:** Interactive primary and secondary buttons scale to `0.97f` on press via `graphicsLayer` (GPU-only `transform` and `alpha`, never animating layout `width`/`height`/`padding`).

### 2. Bento KPI Hierarchy & Card Discipline
- Group metrics into clean Bento tiles (`Worked`, `Avg/Day`, `Delta`) with clear label-above-metric hierarchy (`labelMedium` muted label, `titleMedium.tabular()` value).
- Color-code meaningful deltas (e.g., positive overtime teal/green, undertime amber) without drowning the rest of the card.

### 3. Layered Chart Composition
- Render Compose `Canvas` charts in strict layer order:
  1. Background grid & dashed target line
  2. Unfilled bar tracks
  3. Animated data bars (`EaseOutExpo` / non-bouncy spring)
  4. Axis labels (`TextMeasurer` with `tnum`)
  5. Interactive tap selection highlight + inline detail strip
- Expose `semantics { contentDescription = ... }` for TalkBack summaries.

### 4. Accessibility & Touch Geometry
- Minimum `48.dp` touch target height/width on all buttons, chips, and icon controls with >= `8.dp` separation.
- Pair semantic status colors with both an icon/dot and explicit label text.
