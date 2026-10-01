# Architecture and data flow

## Ownership

`MainActivity` owns Home-only discovery and the pre-session Muse connection. Discovery stops when Home is hidden, the app is backgrounded, simulation is selected, or a session begins. At session start, the activity releases its LibMuse listener and `MeditationService` becomes the connection owner. The service is a foreground service so its timer and audio can continue while the UI is not visible. `SessionRuntime` publishes its current state to the activity; Compose renders that state and sends user commands back to the service.

## Live samples

1. `MuseDeviceManager` adapts LibMuse callbacks. LibMuse may call from worker threads.
2. `SignalProcessor` accumulates sensor packets and emits a smoothed `StateSample` when the service ticks at a new elapsed second. A second with no sensor callback is invalid; a second with some sensor data can be valid even if EEG bands are unavailable. The separate live-only `eegBandsAvailable` flag prevents default band values from driving galaxy motion.
3. `SessionSamples` retains the continuous second-by-second sequence, valid count, and last valid sample used for the visual during a gap. If a service tick is delayed across several seconds, it inserts invalid samples for those skipped seconds. The service writes the new rows to `HushDatabase` and publishes the latest state through `SessionRuntime`.
4. At finish, `SessionResultClassifier` consumes the recorded sequence. History and replay read the saved, per-second samples; raw packets are not persisted.

The service uses monotonic elapsed time for timing and checks it every 250 ms while running. A long scheduling delay cannot reconstruct missing sensor windows, so skipped seconds remain explicit data gaps. Long-session timing and collection still require physical-device validation.

## Simulation and history

`MuseReplaySource` loads the bundled CSV and enables simulation only if it contains exactly 600 consecutive, valid, finite samples for seconds 1–600. During initial data loading, `HushDatabase` imports that sequence in one transaction under a reserved session ID if it is not already present. Its synthetic time is placed immediately before the earliest existing session (or before first launch when history is empty), so it appears as the oldest History entry. The UI labels it `Saved simulation` and hides the synthetic date and placeholder music track.

The service also reads the same source by second through its normal timing, storage, summary, and UI state path when the user starts a new simulation. That run creates its own ordinary session record. History replay reads persisted samples and positions the shared galaxy using `ReplayCursor`; it does not restore the exact live particle positions.

History supports swiping a card from end to start to request deletion. Confirmation removes the session and all its samples in one database transaction; cancellation leaves the card available. Ordinary sessions are permanently deleted. The bundled `Saved simulation` is restored from the CSV on the next app launch, as stated in its confirmation dialog. `MainActivity` performs deletion on its storage executor and refreshes the list only after success. Database version 3 removes the obsolete version-two import-marker table without changing existing session or sample rows. Startup restores missing bundled history even if it was deleted under version two; the bundled CSV also remains available for new simulations.

## UI and rendering

The UI consumes processed `SessionState` and `StateSample` values. `GalaxyParticleField` and `GalaxyMotion` own the visual mapping and animation; composables do not receive raw LibMuse packets or write session samples. See [design system](design-system.md) for screen conventions and [README](../README.md) for build and device validation commands.

## Validation boundary

`./gradlew.bat test` covers the JVM processing, replay, session-sample, auto-connect, and deterministic-motion tests. Compose and service integration checks run through `:app:connectedDebugAndroidTest` on a configured device. Neither automated suite proves Muse Bluetooth reliability, long-session timing, background reconnection, audio behavior under lock screen, or frame pacing; those require a physical Android device and Muse 2.
