# Architecture and data flow

## Ownership

`MainActivity` owns Home-only discovery and the pre-session Muse connection. Discovery stops when Home is hidden, the app is backgrounded, simulation is selected, or a session begins. At session start, the activity releases its LibMuse listener and `MeditationService` becomes the connection owner. The service is a foreground service so its timer and audio can continue while the UI is not visible. `SessionRuntime` publishes its current state to the activity; Compose renders that state and sends user commands back to the service.

## Live samples

1. `MuseDeviceManager` adapts LibMuse callbacks. LibMuse may call from worker threads.
2. `SignalProcessor` aggregates finite, measured EEG bands, dynamic acceleration, and estimated PPG heart rate into one `StateSample` per second. Missing fields remain null. Sensor activity (`valid`) is separate from EEG availability and composite availability (`calmness`). `CalmnessEstimator` owns personal calibration and fusion; `HeartRateEstimator` owns a bounded PPG window. Neither processing class uses Android APIs.
3. `SessionSamples` retains the continuous second-by-second sequence, valid count, and last valid sample used for the visual during a gap. If a service tick is delayed across several seconds, it inserts invalid samples for those skipped seconds. The service writes the new rows to `HushDatabase` and publishes the latest state through `SessionRuntime`.
4. At finish, `SessionResultClassifier` uses finite composite values for new sessions; fewer than two composite values produce no displayed assessment. Legacy sessions retain their original classifications. The service publishes a trend snapshot when new seconds are recorded and a final full snapshot at finish. History and replay read stored per-second values; raw packets are not persisted.

The service uses monotonic elapsed time for timing and checks it every 250 ms while running. A long scheduling delay cannot reconstruct missing sensor windows, so skipped seconds remain explicit data gaps. Long-session timing and collection still require physical-device validation.

## Simulation and history

`MuseReplaySource` loads the bundled CSV and enables simulation only if it contains exactly 600 consecutive, valid, finite samples for seconds 1–600. During initial data loading, `HushDatabase` imports that sequence in one transaction under a reserved session ID if it is not already present. Its synthetic time is placed immediately before the earliest existing session (or before first launch when history is empty), so it appears as the oldest History entry. The UI labels it `Saved simulation` and hides the synthetic date and placeholder music track.

The service also reads the same source by second through its normal timing, storage, summary, and UI state path when the user starts a new simulation. That run creates its own ordinary session record. History replay reads persisted samples and positions the shared galaxy using `ReplayCursor`; it does not restore the exact live particle positions.

History supports swiping a card from end to start to request deletion. Confirmation removes the session and all its samples in one database transaction; cancellation leaves the card available. Ordinary sessions are permanently deleted. The bundled `Saved simulation` is restored from the CSV on the next app launch, as stated in its confirmation dialog. `MainActivity` performs deletion on its storage executor and refreshes the list only after success. Database version 3 removes the obsolete version-two import-marker table without changing existing session or sample rows. Startup restores missing bundled history even if it was deleted under version two; the bundled CSV also remains available for new simulations.

## UI and rendering

The UI consumes processed `SessionState` and `StateSample` values. `GalaxyParticleField` and `GalaxyMotion` own the visual mapping and animation; composables do not receive raw LibMuse packets or write session samples. See [design system](design-system.md) for screen conventions and [README](../README.md) for build and device validation commands.

## Validation boundary

`./gradlew.bat test` covers the JVM processing, replay, session-sample, auto-connect, and deterministic-motion tests. Compose and service integration checks run through `:app:connectedDebugAndroidTest` on a configured device. Neither automated suite proves Muse Bluetooth reliability, long-session timing, background reconnection, audio behavior under lock screen, or frame pacing; those require a physical Android device and Muse 2.

## Demo signal fusion

The first 10 valid EEG seconds calibrate the session while its timer continues. EEG uses `log((alpha + theta + 0.000001) / (beta + 0.000001))`, a median baseline, and `max(1.4826 * MAD, 0.15)` as its sigmoid scale. Finite 0-1 values are required, with explicitly bad contact channels excluded. Missing contact-quality flags do not prevent the demo from using finite bands; this fallback is not proof of signal quality.

Acceleration removes a low-pass gravity estimate (one-second time constant), then maps per-second dynamic RMS to `exp(-RMS / 0.05g)`. PPG reads simultaneous IR/Red channels, uses an eight-second nominal-64Hz window with 0.7-3Hz filtering and refractory peak detection, and rejects flat, inconsistent, stale, explicitly poor-quality, or implausibly sampled windows. Demo BPM support is 40-180. Heart baseline is the first 10 valid BPM seconds; its component is `clamp(0.5 + (baselineBpm - bpm) / 20, 0, 1)`. It can join after EEG calibration, without blocking the session. No HRV is calculated.

Composite weights are EEG 0.60, motion 0.25, and heart 0.15, renormalized over available components. EEG is required. The composite is smoothed with coefficient 0.2 per valid second; galaxy agitation is `1 - calmness`. These thresholds and weights are engineering heuristics for the demo, not a validated meditation-quality or medical assessment. No blink, jaw-clench, or headband-wear artifact events enter processing.

Pause and disconnect reject new sensor data and clear short windows, while retaining completed baselines. Incomplete baselines restart after an interruption. A new session resets all processing state. The last visual shape is held during unavailable composite data, while charts leave explicit gaps.

## Fusion storage compatibility

Database version 4 adds nullable `heart_rate_bpm` and `calmness`, plus `algorithm_version` (0 for legacy samples, 1 for this demo). New samples persist the actual composite used by the galaxy, including null calibration and gap values. Old user sessions are not backfilled: they retain their original band-driven replay and relative charts, with `No calmness data` in the new chart. The reserved bundled simulation is backfilled once from the checked-in CSV using the same EEG calibration and stored stillness, with no invented heart rate. New simulation runs also use these two-component results.

The existing swipe-deletion transaction removes all new sample fields with the row. Deleted bundled history is still restored on the next launch; ordinary deleted sessions remain absent.

## Experimental completion scores

`SessionScoreCalculator` is a pure Kotlin, summary-only calculator. At finish the service computes `SessionScores` from the full processed sample list and publishes both scores and the complete trend snapshot in `SessionState`. Detail screens use the same calculator over persisted samples. Scores are derived rather than stored; database version and real-time signal fusion remain unchanged. Legacy (`algorithmVersion == 0`) and invalid rows are excluded. Simulated sessions follow the same calculation path.

Calm is the mean finite 0-1 Calmness value multiplied by 100. Stability is `100 * clamp(1 - populationStddev(Calmness) / 0.25, 0, 1)`. Each requires at least 30 valid Calmness samples. Focus infers EEG availability from valid, complete persisted bands in 0-1 with a positive band sum; the transient live availability flag is not stored and is not used by this summary calculator: `log((beta + 0.000001) / (alpha + theta + 0.000001))`. The first 10 valid EEG samples provide the median baseline and `max(1.4826 * MAD, 0.15)` scale, independent of the live estimator's interruption-aware calibration. At least 30 subsequent EEG samples are required. Focus is their mean sigmoid-normalized deviation times 100; sigmoid inputs are bounded to -30 through 30 for numeric safety. Missing seconds are excluded, never treated as zero or penalized.

Overall is the unrounded `0.60 * Calm + 0.20 * Focus + 0.20 * Stability`, available only when all three scores exist. Grades have inclusive lower bounds: A+ 95, A 90, A− 85, B+ 80, B 75, B− 70, C 60, otherwise D. Only displayed component scores are rounded to integers; absent values display `—`. These scales, thresholds and grades are engineering choices. Focus can oppose Calm and has not been validated as attention measurement; neither the components nor overall grade establish meditation quality.
