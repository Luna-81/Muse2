# Hush design system

## Direction

Use the blue-black, blue/lavender light, fine borders and quiet typography from `ui-concept.png`. Keep the Hush brand and English copy. The existing live particle renderer remains the visual system; the reference's scores and extra navigation are not product features. Procedural blue/lavender nebula wisps and dark dust add depth to the shared galaxy without image assets.

## Tokens

`HushTheme` always uses the dark Material scheme; system wallpaper colors and light mode do not override it. `HushColors` defines semantic background, surface, raised surface, text, muted text, accent/on-accent, border, error and success colors, plus the renderer/chart palette. Use semantic roles rather than new hex values in screens.

Typography uses the system sans-serif: Light titles/countdown, Regular body and Medium actions. Sizes are 12/14/16/22/28/48sp with explicit line heights. Avoid wide body-text tracking and preserve user font scaling.

`HushSpace` provides 4/8/12/16/24/32dp spacing and a 640dp content width. `HushShapes` provides 24dp panels, 12dp controls and capsule actions. `HushMotion.TransitionMillis` is 250ms for content transitions; the existing particle smoothing has separate physiological/visual responsibilities.

## Components and routes

- `HushPanel`: translucent dark surface, thin border, consistent padding and animated content size.
- `PrimaryAction`: full-width capsule with a minimum 52dp height. Icon controls retain a minimum 48dp touch target.
- Home: brand/device status, a width-responsive square galaxy area capped at 440dp high, and a compact preparation panel. All galaxy views use a radius of 43% of the canvas's smaller dimension; the subtitle and panel spacing are reduced while action touch targets remain unchanged. The page scrolls on constrained screens. Start is gated by a real connection or a usable simulation.
- Device sheet: permission, Bluetooth, connection state, device selection, disconnect and simulation. No raw packet counters in the user flow.
- Soundscape sheet: selection and preview. Dismissal, backgrounding and session start stop preview audio.
- Session: remaining time, circular Pause/Resume, volume and confirmed Finish. Landscape separates controls from the galaxy; constrained controls scroll.
- Completion: actual duration, Mindprint and existing relative-trend classification, with no assessment when fewer than two valid samples exist. Completion and history detail share the width-responsive galaxy panel capped at 440dp high, matching Home's sizing rule.
- History/detail: saved sessions, particle replay and labeled relative trends with gaps. History cards support swiping from end to start to reveal `Delete` and open a confirmation dialog; canceling restores the card. The same action is available to accessibility services. Small history star emblems are decorative session identifiers, not physiological measurements; the detail visualization uses recorded samples.

Reusable controls live in `HushComponents`. Route composition is in `HushApp`; charts and particle summaries are in `SessionVisuals`. Activity code coordinates permissions, idle connection, service events and storage. Do not introduce signal processing into composables or let a hidden Home route claim a service-owned Muse.

## Accessibility and layout

Use safe system insets, bounded content widths, scrollable content, semantic names for icon controls/switches/sliders, and text labels for device/signal state. Do not rely on color alone. Validate portrait, landscape and enlarged text on actual rendered screens. No additional fonts, image assets or UI libraries are required.

## Limits

No calm/focus scores or medical interpretation are introduced. Database version 3 preserves session data and removes the obsolete import marker. Deleted bundled history returns on the next app launch; ordinary history remains deleted. Device addresses remain local preferences. Frame-rate and Bluetooth reliability claims require measurement on a physical target device.

All main particle views use `GalaxyParticleField`, `GalaxyNebula`, and their fixed seed, palette, glow, and trails. Home and live meditation share motion state with the frozen completion Mindprint; recorded detail frames use the same renderer. The renderer is implemented with Compose Canvas and procedural 2D drawing.
