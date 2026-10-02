## Description

**Hush ** is an Android meditation app powered by the Muse 2 brain-sensing headband.

## Goal

Build a polished meditation experience where users can visually perceive changes in their physiological state while meditating.
Core goals:

- Connect reliably to a Muse 2 over Bluetooth.
- Receive and process EEG, PPG, and IMU sensor data in real time.
- Extract useful features such as Alpha, Theta, Beta, heart rate, and stillness.
- Smooth and normalize sensor data before it affects the UI.
- Provide session timing, history, and post-session summaries.
- Generate a unique visual representation ("Mindprint") for completed sessions.
- Maintain smooth real-time rendering, ideally at 60 FPS.
- Keep signal processing, Muse integration, application state, and rendering logic clearly separated.
  
  
  

The visual system should respond gradually rather than directly mirroring noisy sensor values. The experience should feel organic, calm, and alive.

## Startup

Before changing code, read the relevant files under `docs/`. The main local checks are:

- Windows build: `./gradlew.bat :app:assembleDebug`
- Unit tests and Kotlin compilation: `./gradlew.bat test`
- Connected UI tests: `./gradlew.bat :app:connectedDebugAndroidTest` on an Android device or emulator

The bundled ten-minute simulation exercises the session, persistence, and replay flow without a Muse or Bluetooth permission. Muse discovery, foreground-session behavior, background reconnection, and signal quality require a physical Android device with a Muse 2.

## Tech

- Kotlin
- Android SDK: min 26, target 36, compile 37.1
- Jetpack Compose Material 3 and Compose Canvas
- Muse SDK 8.0.9
- Android foreground service and `MediaPlayer` for bundled, looping session audio
- `SQLiteOpenHelper` for local session and sample storage
- Procedural 2D galaxy rendering; no OpenGL or shader pipeline is currently used

## Structure

```text
app/src/main/
├── java/com/blue/hush/
│   ├── MainActivity.kt             # permissions, Home discovery, routes, service handoff
│   ├── audio/                      # bundled nature recording playback
│   ├── muse/                       # LibMuse adapter and idle auto-connect policy
│   ├── processing/                 # packet aggregation, smoothing, result classification
│   ├── replay/                     # bundled CSV source and history replay cursor
│   ├── service/                    # foreground session owner and reconnect flow
│   ├── session/                    # session state, clock, samples, and shared runtime
│   ├── storage/                    # SQLite sessions and per-second samples
│   └── ui/
│       ├── theme/                  # colors, typography, spacing, shapes, motion
│       └── ...                     # routes, reusable components, galaxy renderer
├── assets/simulation/              # checked-in ten-minute Muse replay CSV
├── jniLibs/                        # LibMuse native libraries
└── res/                            # Android resources and backup rules

app/src/test/                       # JVM unit tests for processing, replay, policy, and motion
app/src/androidTest/                # Compose/device tests for the app and simulation flow
app/libs/libmuse_android.jar        # LibMuse JVM API bundled for the Android build
third_party/libmuse_android_8.0.9/  # SDK README and license
docs/                               # architecture, design system, and Muse integration notes
```

Ownership boundaries:

- `MainActivity` owns permissions, visible Home discovery, idle connection, history loading, and service handoff.
- `MeditationService` owns an active live or simulated session, timing, audio, Muse callbacks, reconnection, and persistence.
- `SignalProcessor` converts selected Muse packets into smoothed per-second samples. Ten-second EEG calibration feeds an EEG-only Calmness trend; acceleration-derived head stillness and demo PPG heart-rate estimates supply independent summary metrics. Physical-device measurement accuracy is not yet established.
- `HushApp` and the UI package render `SessionState` and persisted `StateSample` values; composables must not consume raw Muse packets or write the database.
- `HushDatabase` stores session metadata and downsampled samples locally. Raw EEG, PPG, and IMU packets are not persisted.

## Rules
- Check the relevant files under `docs/` before coding and update them when behavior or ownership changes.
- Use English for user-facing UI copy.
- Preserve the current dark blue/lavender Hush visual language; do not change the UI style without permission.
- Keep Muse integration, signal processing, session state, persistence, and rendering separated.
- Treat signal gaps as missing data, not as a meditation-quality judgment. Do not add medical interpretation or unimplemented heart-rate claims.


