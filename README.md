# Hush

Hush is an Android meditation app for Muse 2. Trusted Alpha, Theta, and Beta measurements drive an EEG-only Calmness trend and gradual galaxy visualization after 10 valid EEG seconds of personal calibration. Session results show three independent metrics: Calm (mean Calmness, 0-100), Stability (mean head stillness from acceleration, 0-100), and Heart Rate (mean PPG-derived BPM). Each requires 30 valid measured seconds of its own input; unavailable values display `—` and gaps never become zero scores. There is no Focus score or overall grade. These metrics are experimental, not a validated meditation-quality or medical assessment.

The bundled ten-minute simulation exercises timing, storage, summaries, and replay without a headband or Bluetooth permission. Loading it does not create a History entry; running a simulation saves an ordinary session. Its CSV has no heart rate, so simulated Heart Rate remains unavailable.

**History reset:** upgrading a database from version 1-4 to version 5 permanently deletes all existing sessions and samples, including unfinished sessions and the former `Saved simulation` entry. This happens once during the transactional upgrade. Subsequent launches preserve new history; simulation history is no longer automatically imported or restored. Connection preferences and other settings are unchanged.

## Build

1. Install Android Studio and an Android SDK that supports the versions in `app/build.gradle.kts`. Set the SDK path in local `local.properties` or `ANDROID_HOME`.
2. Run `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`).
3. Install the debug APK on a physical Android device for Muse 2 Bluetooth testing. Grant the Bluetooth permissions from Home before connecting.

The LibMuse 8.0.9 JAR and native libraries are checked in. No SDK download or account is needed for a local build. Simulation does not require a Muse or Bluetooth permission.

## Code map

- `muse/`: LibMuse adapter and idle auto-connect policy.
- `processing/`: packet aggregation, smoothing, and session result classification.
- `session/`: session state, monotonic clock, and recorded sample sequence.
- `service/`: foreground session owner for the Muse connection, timing, audio, and persistence.
- `storage/`: local SQLite sessions and downsampled samples.
- `replay/`: bundled simulation source and history replay cursor.
- `ui/`: Compose routes, reusable components, theme tokens, and the shared Canvas galaxy renderer.
- `assets/simulation/`: checked-in ten-minute replay CSV.
- `audio/` and `res/raw/`: offline, looping Rain, Ocean, and Fireplace field recordings.
- `MainActivity.kt`: permissions, idle discovery, route state, and service handoff.
- `app/src/test/` and `app/src/androidTest/`: JVM and device/Compose coverage.

See [architecture](docs/architecture.md) for data ownership and the live/simulation flow. [Muse SDK notes](docs/muse-sdk.md) and the [design system](docs/design-system.md) cover integration and UI constraints.

The Soundscapes sheet offers preview and selection of Rain (with occasional distant thunder), Ocean, and Fireplace. Recordings are bundled, so playback needs no network or account. Audio continues with the foreground session and pauses/resumes at its current position. See [audio sources](docs/audio-sources.md) for CC0 credits and processing details.

## Validation

Run `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin` for local checks. `./gradlew :app:connectedDebugAndroidTest` runs the Compose/device checks when a device is available. Bluetooth reliability, background sessions, and frame pacing require a physical device with a Muse 2; the emulator and bundled simulation do not validate that hardware path.
