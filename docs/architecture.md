# Architecture and data flow

## Ownership

`MuseConnectionRuntime` owns one process-local LibMuse adapter and retains its native connection across navigation, activity recreation, simulation selection, and session completion. `MainActivity` subscribes to connection state and owns permissions and idle discovery whenever the app is visible, regardless of the selected tab or detail screen. Backgrounding stops idle discovery but preserves an established connection. Manual disconnect, unavailable Bluetooth/permissions, and connection failures can release the adapter. Process termination ends the connection; idle background operation is not a foreground service guarantee.

`MeditationService` subscribes to that same adapter during a live session, without reconnecting an already-connected device. It owns active-session reconnection scanning, timing, audio, processing, and persistence; finishing or destroying the service only removes its subscription. Simulation does not subscribe to live sensor packets or disconnect a connected Muse. Connection/discovery callbacks are serialized on the main thread; packets retain their SDK-thread delivery. Detached consumers and callbacks from a released adapter cannot update the new connection owner. The service remains a foreground service so its timer and audio can continue while the UI is not visible. `SessionRuntime` publishes its current state to the activity; Compose renders that state and sends user commands back to the service.

## Soundscapes

`AmbientAudioEngine` streams bundled Ogg Vorbis recordings through Android `MediaPlayer`, with asynchronous preparation and looping playback. `MainActivity` owns a preview engine; `MeditationService` owns the session engine. Pause/resume preserves playback position, and volume is applied once by the player. Stopping or replacing a track releases its player; a stale preparation callback cannot restart a dismissed preview. No network, synthesis worker, or additional playback dependency is required.

New sessions offer Rain, Ocean, and Fireplace, with Rain selected by default. `MIST` and `TIDE` enum values remain readable with their original history labels; legacy playback commands map them to Rain and Ocean respectively. Existing database rows require no migration. The soundscape sheet scrolls on constrained screens.

The recordings are CC0 field recordings, edited offline for consistent loudness and a blended loop seam. See [audio sources](audio-sources.md) for provenance, processing, sizes, and device validation. Rain includes occasional distant thunder. Loop audibility and headphone balance require listening on the target device; automated checks only establish decoding and playback behavior.

## Live samples

1. `MuseDeviceManager` adapts LibMuse callbacks. LibMuse may call from worker threads.
2. `SignalProcessor` aggregates trusted EEG bands, dynamic acceleration, and estimated PPG heart rate into one `StateSample` per second. Each live band packet is filtered at arrival using fresh per-channel `IS_GOOD` flags; later flags cannot retroactively change accepted data. Only EEG1-EEG4 are considered, and all three bands require a shared trusted channel and a positive sum. Missing or untrusted bands remain null. Sensor activity (`valid`) is separate from EEG availability and composite availability (`calmness`). `CalmnessEstimator` owns personal calibration and fusion; `HeartRateEstimator` owns a bounded PPG window. Neither processing class uses Android APIs.
3. `SessionSamples` retains the continuous second-by-second sequence, valid count, and last valid sample used for the visual during a gap. If a service tick is delayed across several seconds, it inserts invalid samples for those skipped seconds. The service writes the new rows to `HushDatabase` and publishes the latest state through `SessionRuntime`.
4. At finish, `SessionResultClassifier` uses finite composite values for new sessions; fewer than two composite values produce no displayed assessment. Legacy sessions retain their original classifications. The service publishes a trend snapshot when new seconds are recorded and a final full snapshot at finish. History and replay read stored per-second values; raw packets are not persisted.

The service uses monotonic elapsed time for timing and checks it every 250 ms while running. A long scheduling delay cannot reconstruct missing sensor windows, so skipped seconds remain explicit data gaps. Long-session timing and collection still require physical-device validation.

Service discovery and connection callbacks are serialized on its main thread; sensor packet aggregation remains synchronized in `SignalProcessor`. Repeated collection-state notifications do not reset windows or calibration. Debug live sessions keep a bounded private diagnostic trace of packet counts, connection events, and quality filtering, without changing persisted samples. See [EEG gap diagnostics](muse-sdk.md#debugging-eeg-gaps) for interpretation and retrieval.

## Simulation and history

`MuseReplaySource` loads the bundled CSV and enables simulation only if it contains exactly 600 consecutive, valid, finite samples for seconds 1–600. During initial data loading, `HushDatabase` imports that sequence in one transaction under a reserved session ID if it is not already present. Its synthetic time is placed immediately before the earliest existing session (or before first launch when history is empty), so it appears as the oldest History entry. The UI labels it `Saved simulation` and hides the synthetic date and placeholder music track.

The service also reads the same source by second through its normal timing, storage, summary, and UI state path when the user starts a new simulation. That run creates its own ordinary session record. History replay reads persisted samples and positions the shared galaxy using `ReplayCursor`; it does not restore the exact live particle positions.

History supports swiping a card from end to start to request deletion. Confirmation removes the session and all its samples in one database transaction; cancellation leaves the card available. Ordinary sessions are permanently deleted. The bundled `Saved simulation` is restored from the CSV on the next app launch, as stated in its confirmation dialog. `MainActivity` performs deletion on its storage executor and refreshes the list only after success. Database version 3 removes the obsolete version-two import-marker table without changing existing session or sample rows. Startup restores missing bundled history even if it was deleted under version two; the bundled CSV also remains available for new simulations.

## UI and rendering

The UI consumes processed `SessionState` and `StateSample` values. `GalaxyParticleField` and `GalaxyMotion` own the visual mapping and animation; composables do not receive raw LibMuse packets or write session samples. See [design system](design-system.md) for screen conventions and [README](../README.md) for build and device validation commands.

`SessionState.eegStatus` is transient and distinguishes available, low-quality, unknown-quality, and missing EEG. It comes from the processor (or trusted simulation samples), independently of Bluetooth connection state. Live gaps keep the galaxy drifting at its retained visual parameters and reduced opacity, without producing Calmness or scores. Before any trusted composite exists the drift is decorative. Pause stops the frame loop; recorded replay gaps retain their original frozen shape.

## Validation boundary

`./gradlew.bat test` covers the JVM processing, replay, session-sample, auto-connect, and deterministic-motion tests. Compose and service integration checks run through `:app:connectedDebugAndroidTest` on a configured device. Neither automated suite proves Muse Bluetooth reliability, long-session timing, background reconnection, audio behavior under lock screen, or frame pacing; those require a physical Android device and Muse 2.

## Demo signal fusion

The first 10 trusted EEG seconds calibrate the session while its timer continues. EEG uses `log((alpha + theta + 0.000001) / (beta + 0.000001))`, a median baseline, and `max(1.4826 * MAD, 0.15)` as its sigmoid scale. Finite 0-1 values are required. Live channels additionally require `IS_GOOD == 1` at packet arrival with a nonnegative age of at most two seconds. Zero means low quality; missing, expired, nonfinite, or other flag values mean unknown quality. Neither low nor unknown quality advances calibration or contributes to Calmness or Focus. A zero flag does not prove bad electrode contact or Bluetooth failure. Simulation bypasses live quality checks through the validated CSV source.

Acceleration removes a low-pass gravity estimate (one-second time constant), then maps per-second dynamic RMS to `exp(-RMS / 0.05g)`. PPG reads simultaneous IR/Red channels, uses an eight-second nominal-64Hz window with 0.7-3Hz filtering and refractory peak detection, and rejects flat, inconsistent, stale, explicitly poor-quality, or implausibly sampled windows. Demo BPM support is 40-180. Heart baseline is the first 10 valid BPM seconds; its component is `clamp(0.5 + (baselineBpm - bpm) / 20, 0, 1)`. It can join after EEG calibration, without blocking the session. No HRV is calculated.

Composite weights are EEG 0.60, motion 0.25, and heart 0.15, renormalized over available components. EEG is required. The composite is smoothed with coefficient 0.2 per valid second; galaxy agitation is `1 - calmness`. These thresholds and weights are engineering heuristics for the demo, not a validated meditation-quality or medical assessment. No blink, jaw-clench, or headband-wear artifact events enter processing.

Pause and disconnect reject new sensor data and clear short windows, while retaining completed baselines. Incomplete baselines restart after an interruption. Low-quality and missing seconds leave baseline progress intact. Repeated notifications of an unchanged collection state leave calibration and sensor windows intact. A new session resets all processing state. Live animation continues without updating its physiological parameters during unavailable composite data, while charts mark missing intervals with decorative dashed bridges.

## Fusion storage compatibility

Database version 4 adds nullable `heart_rate_bpm` and `calmness`, plus `algorithm_version` (0 for legacy samples, 1 for this demo). New samples persist the actual composite used by the galaxy, including null calibration and gap values. Old user sessions are not backfilled: they retain their original band-driven replay and relative charts, with `No calmness data` in the new chart. The reserved bundled simulation is backfilled once from the checked-in CSV using the same EEG calibration and stored stillness, with no invented heart rate. New simulation runs also use these two-component results.

Algorithm version 2 introduces packet-time EEG quality filtering for new live samples. Invalid or incomplete trusted EEG sets all three persisted bands to null, ensuring historical Focus cannot treat sensor activity alone as trusted EEG. New simulations also use version 2 with the unchanged fusion formula. Existing version 0/1 session rows and existing bundled history remain unchanged; no schema migration or retroactive quality reconstruction is performed.

The existing swipe-deletion transaction removes all new sample fields with the row. Deleted bundled history is still restored on the next launch; ordinary deleted sessions remain absent.

## Experimental completion scores

`SessionScoreCalculator` is a pure Kotlin, summary-only calculator. At finish the service computes `SessionScores` from the full processed sample list and publishes both scores and the complete trend snapshot in `SessionState`. Detail screens use the same calculator over persisted samples. Scores are derived rather than stored; database version and real-time signal fusion remain unchanged. Legacy (`algorithmVersion == 0`) and invalid rows are excluded. Simulated sessions follow the same calculation path.

Calm is the mean finite 0-1 Calmness value multiplied by 100. Stability is `100 * clamp(1 - populationStddev(Calmness) / 0.25, 0, 1)`. Each requires at least 30 valid Calmness samples. Focus infers EEG availability from valid, complete persisted bands in 0-1 with a positive band sum; the transient live availability flag is not stored and is not used by this summary calculator: `log((beta + 0.000001) / (alpha + theta + 0.000001))`. The first 10 valid EEG samples provide the median baseline and `max(1.4826 * MAD, 0.15)` scale, independent of the live estimator's interruption-aware calibration. At least 30 subsequent EEG samples are required. Focus is their mean sigmoid-normalized deviation times 100; sigmoid inputs are bounded to -30 through 30 for numeric safety. Missing seconds are excluded, never treated as zero or penalized.

Overall is the unrounded `0.60 * Calm + 0.20 * Focus + 0.20 * Stability`, available only when all three scores exist. Grades have inclusive lower bounds: A+ 95, A 90, A− 85, B+ 80, B 75, B− 70, C 60, otherwise D. Only displayed component scores are rounded to integers; absent values display `—`. These scales, thresholds and grades are engineering choices. Focus can oppose Calm and has not been validated as attention measurement; neither the components nor overall grade establish meditation quality.

`EegNoticeTracker` is a service-owned presentation policy. `SessionState.eegStatus` remains the immediate processor result; nullable `eegNotice` gates the main warning after 20 unavailable sampled seconds, with two consecutive available seconds required to recover. Missing timer seconds interrupt recovery. Repeated updates for the same second are ignored. Pause, disconnect, and new sessions reset it. The policy leaves calibration, persisted samples, charts, and scores unchanged.

Charts draw smooth solid curves between consecutive trusted samples and subdued dashed curves across unavailable intervals. A live trailing gap holds the last trusted level as a dashed guide until a new trusted value arrives. These guides exist only in Canvas drawing; stored null values, calibration, scoring, and replay samples are unchanged.
