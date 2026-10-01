# LibMuse Android SDK

The project includes LibMuse Android SDK 8.0.9 for Muse 2 integration.

## Layout

- `app/libs/libmuse_android.jar` contains the Java/Kotlin API.
- `app/src/main/jniLibs/<abi>/libmuse_android.so` contains the native runtime for supported ABIs.
- `app/src/main/java/com/blue/hush/muse/MuseDeviceManager.kt` provides the application-facing adapter.
- `third_party/libmuse_android_8.0.9/` preserves the SDK README and license.

## Runtime requirements

The app must request the Bluetooth permissions declared in `AndroidManifest.xml` before scanning:

- Android 12 and newer: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`.
- Android 11 and older: `ACCESS_FINE_LOCATION` (and the legacy Bluetooth permissions).

`MuseDeviceManager` must be created after permission is granted. It initializes `MuseManagerAndroid`, discovers nearby devices with `startScanning()`, and connects to a selected device with `connect(...)`.

LibMuse callbacks are delivered from SDK worker threads. UI consumers must switch to the main thread before updating Compose state or views.

## Scanning and connection

The launcher activity exposes the MVP meditation flow. Home owns the connection entry point:

1. Grant the Bluetooth permission shown by the page.
2. Put the Muse 2 into pairing mode. Home searches automatically while visible.
3. Hush automatically connects the remembered device, or the only discovered device on first use. Select a device in the sheet when multiple devices are available.
4. Choose a duration and music track, then tap `Start meditation`.

## Sensor processing contract

The adapter uses `getAccelerometerValue(X/Y/Z)` and `getPpgChannelValue(IR/RED)` instead of assuming SDK array positions. Its application-facing PPG list contains IR and Red from a single sampling instant, never two consecutive time samples. Each packet includes a monotonic `receivedAtMillis` from `SystemClock.elapsedRealtime()`; SDK timestamps are preserved separately, and their undocumented units are not used to calculate BPM.

The demo assumes Muse 2 PPG at 64Hz. A rolling eight-second window checks its sample count against monotonic arrival duration (20% tolerance), resets after delivery gaps over 500ms or channel changes, and estimates heart rate from filtered peak intervals. Fresh `IS_PPG_GOOD` and `IS_HEART_GOOD` flags can reject the window; `IS_GOOD` excludes explicitly bad EEG channels. Quality flags expire after two seconds. Missing flags fall back to numerical/window checks, so physical-device validation remains required. Bluetooth delivery timing does not reconstruct every lost sample.

`ARTIFACTS` is not subscribed. The SDK-required artifact callback is a no-op, and blink, jaw-clench, and headband-wear events are not exposed to the application.

For physical validation, check actual PPG callback cadence and IR/Red availability, compare estimated BPM with a reference pulse measurement while still, then move/rotate the head and verify movement response and PPG rejection. Test pause, resume, and reconnect to confirm no heart intervals span interruptions. Emulator and synthetic-signal tests cannot establish measurement accuracy.