# Hush design system

## Direction

Use the blue-black, blue/lavender light, fine borders and quiet typography from `ui-concept.png`. Keep the Hush brand and English copy. The existing live particle renderer remains the visual system. Completion adds experimental scores without changing the renderer or primary navigation. Procedural blue/lavender nebula wisps and dark dust add depth to the shared galaxy without image assets.

## Tokens

`HushTheme` always uses the dark Material scheme; system wallpaper colors and light mode do not override it. `HushColors` defines semantic background, surface, raised surface, text, muted text, accent/on-accent, border, error and success colors, plus the renderer/chart palette. Use semantic roles rather than new hex values in screens.

Typography uses the system sans-serif: Light titles/countdown, Regular body and Medium actions. Sizes are 12/14/16/22/28/48sp with explicit line heights. Avoid wide body-text tracking and preserve user font scaling.

`HushSpace` provides 4/8/12/16/24/32dp spacing and a 640dp content width. `HushShapes` provides 24dp panels, 12dp controls and capsule actions. `HushMotion.TransitionMillis` is 250ms for content transitions; the existing particle smoothing has separate physiological/visual responsibilities.

## Components and routes

- `HushPanel`: translucent dark surface, thin border, consistent padding and animated content size.
- `PrimaryAction`: full-width capsule with a minimum 52dp height. Icon controls retain a minimum 48dp touch target.
- Home: brand/device status, a width-responsive square galaxy area capped at 440dp high, and a compact preparation panel. All galaxy views use a radius of 43% of the canvas's smaller dimension; the subtitle and panel spacing are reduced while action touch targets remain unchanged. The page scrolls on constrained screens. Start is gated by a real connection or a usable simulation.
- Device sheet: permission, Bluetooth, connection state, device selection, disconnect and simulation. No raw packet counters in the user flow.
- Soundscape sheet: Rain, Ocean, and Fireplace selection and preview in a scrollable list. Rain is the default. Dismissal, backgrounding and session start stop preview audio. Preserve the existing panel style and English labels.
- Session: remaining time, circular Pause/Resume, volume and confirmed Finish. A compact Calmness plot with a 96dp drawing area sits in the control region; landscape uses the right control column and constrained controls scroll. Available EEG before ten trusted seconds shows `Calibrating… n/10`. Brief EEG quality losses leave the main status quiet after calibration and preserve `Calibrating… n/10` during calibration. After 20 unavailable sampled seconds without two consecutive trusted seconds, show `Low signal quality`, `Checking signal…`, or `Waiting for EEG…` for the triggering condition. Keep that notice stable until two consecutive trusted seconds; pause, disconnect, and a new session reset the notice tracker. This presentation grace does not accept any untrusted EEG. A real disconnect shows `Reconnecting…`; `Paused` takes precedence. No diagnostic counters are added to the screen. Live gaps retain slow drift and the last physiological visual parameters with the existing reduced opacity; initial drift is decorative. Neither drift nor low-quality data creates a Calmness value; dashed chart bridges are decorative only. Pause stops motion and recorded replay gaps retain their original freeze.
- Completion: `Finished` shows the frozen Mindprint and full-session Calmness plot. A bottom sheet opens once per session with actual `Total Time`, a circular overall grade, and three 0-100 cards (`Calm`, `Focus`, `Stability`). Unavailable scores and grades display `—`. `More Details` waits for saved history to load. Swipe, scrim tap or Back dismisses the sheet; `Results` reopens it. Back with the sheet closed returns Home. Returning from details and configuration changes preserve dismissal. Completion uses the shared galaxy panel with a height limit of 34% of the available page height, clamped to 120-440dp, to leave room for its 96dp Calmness plot above the sheet in ordinary portrait layouts. History detail retains the width-responsive panel capped at 440dp high. Sheet content scrolls on constrained screens and respects system insets.
- History/detail: saved sessions, particle replay, a Calmness plot with a 160dp drawing area, and the existing labeled relative trends with gaps. History cards support swiping from end to start to reveal `Delete` and open a confirmation dialog; canceling restores the card. The same action is available to accessibility services. Small history star emblems are decorative session identifiers, not physiological measurements; the detail visualization uses recorded samples.

Reusable controls live in `HushComponents`. Route composition is in `HushApp`; charts and particle summaries are in `SessionVisuals`. Activity code coordinates permissions, idle connection, service events and storage. Do not introduce signal processing into composables or let a hidden Home route claim a service-owned Muse.

## Accessibility and layout

Use safe system insets, bounded content widths, scrollable content, semantic names for icon controls/switches/sliders, and text labels for device/signal state. Do not rely on color alone. Validate portrait, landscape and enlarged text on actual rendered screens. No additional fonts, image assets or UI libraries are required.

## Limits

Calmness and the completion scores are demo heuristics, not validated meditation-quality measurements or medical interpretations. Details show the same score summary and a short experimental-score notice. Focus and Calm can move in opposite directions; the grade must not be presented as a clinically or scientifically established assessment. Both charts use the same stored 0-1 values on a fixed 0-100 axis, elapsed-time coordinates, lavender stroke, isolated points, and visually distinct dashed bridges across gaps. No standalone BPM display is added; legacy history shows `No calmness data` and unavailable scores. No schema migration is needed for summary scores. Deleted bundled history returns on the next app launch; ordinary history remains deleted. Device addresses remain local preferences. Frame-rate and Bluetooth reliability claims require measurement on a physical target device.

All main particle views use `GalaxyParticleField`, `GalaxyNebula`, and their fixed seed, palette, glow, and trails. Home and live meditation share motion state with the frozen completion Mindprint; recorded detail frames use the same renderer. The renderer is implemented with Compose Canvas and procedural 2D drawing.

Charts round consecutive sample connections with cubic curves using horizontal endpoint tangents. Curves pass through every recorded value and remain between adjacent values; smoothing affects drawing only, never processing or scores. Invalid samples and nonconsecutive seconds split the measured solid curve; a subdued dashed cubic bridge links the surrounding trusted points. A trailing gap holds the last trusted level with a dashed line, and updates when a new trusted point arrives. No line is drawn before the first trusted point. Isolated samples remain visible as dots; continuous curves do not add a dot at every second.

Calmness charts use a 2dp stroke and 2dp isolated-point radius to keep short trusted segments visible. Missing intervals use subdued dashed bridges instead of measured solid strokes; quality-notice grace never creates chart measurements.
