# Hush

Hush is an Android meditation app for Muse 2. It combines Alpha, Theta, Beta and PPG-derived heart rate into a gradual galaxy visualization and a Calmness trend, then saves local session summaries and replay data. Acceleration measures body stillness independently and does not affect Calmness or scores. The demo calibrates from 10 valid EEG seconds; heart rate joins after its own baseline is available. A bundled ten-minute simulation lets the session flow run without a headband and appears once as the oldest History entry. New simulations use EEG alone without inventing heart rate; stored stillness remains available for its separate chart. Calmness is an engineering heuristic, not a validated meditation-quality or medical assessment. History retains its original four relative curves and adds the saved Calmness trend.

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
