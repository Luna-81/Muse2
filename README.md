# Hush

Hush is an Android meditation app for Muse 2. Trusted Alpha, Theta, and Beta measurements drive an EEG-only Calmness trend and gradual galaxy visualization from the first trusted EEG second using a fixed public resting reference. Session results show three independent metrics: Calm (mean Calmness, 0-100), Stability (mean head stillness from acceleration, 0-100), and Heart Rate (mean PPG-derived BPM). Each requires 30 valid measured seconds of its own input; unavailable values display `—` and gaps never become zero scores. There is no Focus score or overall grade. These metrics are experimental, not a validated meditation-quality or medical assessment.

Algorithm version 5 maps the typical CogWear resting reference to 50, using baseline `0.76507906870225273` and scale `0.32801364656479376`. There is no per-session calibration or network requirement. The reference uses 10 pilot participants; participant 3 has no published baseline EEG and is explicitly excluded. Reproduce it with `python tools/cogwear_reference.py` (Python 3.9+, standard library only). Downloads go to ignored `research-data/cogwear/`, and statistics to `docs/cogwear-reference-results.json`. See [data processing and results](docs/eeg-reference-dataset.md); targeted script tests run with `python -m unittest discover -s tools -p 'test_cogwear_reference.py'`. Existing history retains its recorded values and the database remains version 5.

The bundled ten-minute simulation exercises timing, storage, summaries, and replay without a headband or Bluetooth permission. Loading it does not create a History entry; running a simulation saves an ordinary session. It replays the second-earliest device recording from `app/src/main/assets/history/second_earliest_session.json`, retaining measured heart rate, Calmness, stillness, and signal gaps. The two earliest original sessions are bundled as separate JSON files and restored if missing on a cold app restart; ordinary simulated runs are not restored after deletion.

**History reset:** upgrading a database from version 1-4 to version 5 permanently deletes all existing sessions and samples, including unfinished sessions and the former `Saved simulation` entry. This happens once during the transactional upgrade. Subsequent launches preserve new history; ordinary simulation runs are not automatically imported or restored; the two explicitly bundled original snapshots are restored separately at startup. Connection preferences and other settings are unchanged.

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
- `assets/history/`: two separate recorded session snapshots; the second-earliest also supplies the ten-minute simulation.
- `audio/` and `res/raw/`: offline, looping Rain, Ocean, and Fireplace field recordings.
- `MainActivity.kt`: permissions, idle discovery, route state, and service handoff.
- `app/src/test/` and `app/src/androidTest/`: JVM and device/Compose coverage.

See [architecture](docs/architecture.md) for data ownership and the live/simulation flow. [Muse SDK notes](docs/muse-sdk.md) and the [design system](docs/design-system.md) cover integration and UI constraints.

The Soundscapes sheet offers preview and selection of Rain (with occasional distant thunder), Ocean, and Fireplace. Recordings are bundled, so playback needs no network or account. Audio continues with the foreground session and pauses/resumes at its current position. See [audio sources](docs/audio-sources.md) for CC0 credits and processing details.

## Validation

Run `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin` for local checks. `./gradlew :app:connectedDebugAndroidTest` runs the Compose/device checks when a device is available. Bluetooth reliability, background sessions, and frame pacing require a physical device with a Muse 2; the emulator and bundled simulation do not validate that hardware path.
