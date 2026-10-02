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

The launcher activity exposes the MVP meditation flow. Home exposes the connection entry point; the connection is retained across screens and sessions:

1. Grant the Bluetooth permission shown by the page.
2. Put the Muse 2 into pairing mode. Hush searches automatically while the app is visible and no session is active.
3. Hush automatically connects the remembered device, or the only discovered device on first use. Select a device in the sheet when multiple devices are available.
4. Choose a duration and music track, then tap `Start meditation`.

## Sensor processing contract

The adapter uses `getEegChannelValue(EEG1/EEG2/EEG3/EEG4)` for raw EEG and the three selected relative bands, `getAccelerometerValue(X/Y/Z)`, and `getPpgChannelValue(IR/RED)` instead of assuming SDK array positions. The four `IS_GOOD` entries map to these same physical EEG channels; auxiliary channels never enter fusion. Its application-facing PPG list contains IR and Red from a single sampling instant, never two consecutive time samples. Each packet includes a monotonic `receivedAtMillis` from `SystemClock.elapsedRealtime()`; SDK timestamps are preserved separately, and their undocumented units are not used to calculate BPM.

Live EEG packets require a fresh per-channel `IS_GOOD` value of exactly 1 at arrival (age 0-2000 ms). `HSI_PRECISION` is read with explicit EEG1-EEG4 getters: fresh finite values in (2, 4] additionally reject poor-fit channels; [1, 2] means usable fit. `IS_GOOD == 0` still rejects interference even with usable fit, and missing, expired, nonfinite, or other flags leave it untrusted. Usable-fit rejections are classified as `INTERFERENCE`, with `Signal settling…` after sustained loss, rather than implying poor contact. Without usable fit information, zero retains the low-quality fallback. Each second aggregates only channels represented by accepted Alpha, Theta, and Beta packets. A late quality or fit flag never erases accepted packets or accepts rejected packets. No shared channel or a nonpositive total produces null persisted bands and Calmness. Quality problems do not trigger Bluetooth reconnection. `IS_GOOD` is a rolling one-second artifact-quality check, not a definitive diagnosis of electrode contact; rejected artifacts are excluded, not reconstructed or interpolated into measurements.

The [SDK enum reference](https://siddhantattavar.com/libmuse/enumcom_1_1choosemuse_1_1libmuse_1_1_muse_data_packet_type.html) is a hosted copy of LibMuse 6.0.3 documentation: it describes `IS_GOOD` as a rolling one-second check emitted every 100 ms that can fail on blinks or muscle activity, and `HSI_PRECISION` as good (1), mediocre (2), or poor (4) fit. Hush uses these meanings as engineering assumptions; verify the actual 8.0.9 values on Muse 2 using the debug trace. This is not the proprietary official-app algorithm.

The demo assumes Muse 2 PPG at 64Hz. A rolling eight-second window checks its sample count against monotonic arrival duration (20% tolerance), resets after delivery gaps over 500ms or channel changes, and estimates heart rate from filtered peak intervals. Fresh `IS_PPG_GOOD` and `IS_HEART_GOOD` flags can reject the window. These PPG flags still expire after two seconds and missing flags retain their numerical/window fallback; the stricter live EEG rule does not change heart-rate processing. Physical-device validation remains required. Bluetooth delivery timing does not reconstruct every lost sample.

Algorithm version 4 uses EEG alone for calibrated and smoothed Calmness. Acceleration supplies head stillness, and PPG supplies BPM independently. Session summaries show mean Calmness × 100, mean stillness × 100, and mean BPM, each requiring 30 valid measured seconds of its own sensor. Missing EEG does not invalidate other available sensors. No heart-rate baseline, heart-rate score, Focus, overall grade, or HRV is calculated. Simulations retain the bundled EEG/stillness CSV and leave heart rate null.

`ARTIFACTS` is not subscribed. The SDK-required artifact callback is a no-op, and blink, jaw-clench, and headband-wear events are not exposed to the application.

## Debugging EEG gaps

Debug builds write `files/signal-diagnostics.jsonl` in the app's private storage during a live session. Each new live session replaces the previous trace; simulation does not write a trace. The file stops growing at 2 MiB. Release builds do not write diagnostics. No raw waveforms, device addresses, or names are included, and the session database schema is unchanged.

The trace records session lifecycle and connection events, algorithm version, per-window nonempty packet counts (raw EEG, Alpha/Theta/Beta, acceleration, and PPG), accepted band-packet counts, and per-channel accepted/rejected/unknown-quality numeric band-value counts. It includes complete numeric and trusted EEG channel counts, the latest `IS_GOOD` values with age and freshness, EEG state, calibration progress, and skipped timer seconds. The latest quality flag can differ from the earlier flag that accepted a packet; `quality_accepted` records actual arrival-time decisions. Accepted band-packet counts precede common-channel intersection, so they alone do not prove a usable sample. Connection callbacks are serialized on the service's main thread; packet processing remains synchronized. Repeated notifications of the same collection state preserve the current window and incomplete calibration; an actual pause or disconnect still clears them.

Version-3 and later traces also include `hsi_precision`, `fit_age_ms`, `fit_fresh`, and per-channel `interference_rejected` counts. These counts identify numeric band values rejected with `IS_GOOD == 0` and usable fit at arrival. A complete numeric channel with these rejections can report `INTERFERENCE`; no rejected value enters calibration or persistence as EEG. Verify blinking and jaw movement produce excluded intervals, another clean channel can still supply the sample, and sustained poor fit is rejected even with a good artifact flag. Check that body stillness continues to react to acceleration without changing Calmness when EEG is held constant; changes in heart rate must also leave Calmness unchanged.

Read the latest trace from a USB-connected debug installation:

```powershell
adb exec-out run-as com.blue.hush cat files/signal-diagnostics.jsonl
```

`EEG_WITHOUT_BANDS` means raw EEG arrived but none of the three required derived bands did. `NO_EEG_PACKETS` means neither raw EEG nor band packets arrived in the window; check `connected` and connection events before attributing it to Bluetooth. `INCOMPLETE_BANDS` identifies a missing band and `INVALID_BANDS` identifies a lack of a shared numeric channel. `LOW_QUALITY` means no complete trusted result is available despite band arrivals, while `QUALITY_UNKNOWN` identifies missing, stale, or invalid quality information. `CALIBRATING` and `READY` indicate usable EEG. Older version-1 traces used `CONTACT_REJECTED`; that name overstated what the flag proves and is no longer emitted. Counts cover the collected window rather than reconstructing skipped seconds.

Acceptance on the physical Muse 2: record three minutes, pause/resume, then disconnect/reconnect. Confirm that trusted sample windows have a usable common channel and nonzero quality acceptance, late bad flags do not remove earlier accepted packets, low/unknown quality leaves persisted bands and Calmness null, calibration advances only on trusted windows, and low quality does not generate a connection event. Observe continuing faded animation and the correct status text; pause must stop motion. The database version-5 upgrade intentionally clears existing history once; new history must survive subsequent launches.

For physical validation, check actual PPG callback cadence and IR/Red availability, compare estimated BPM with a reference pulse measurement while still, then move/rotate the head and verify movement response and PPG rejection. Test pause, resume, and reconnect to confirm no heart intervals span interruptions. Emulator and synthetic-signal tests cannot establish measurement accuracy.

Main-screen quality warnings use the service presentation grace documented in architecture.md; debug `eeg_status` remains immediate. Muse documents notifications after more than 20 seconds of sensor-quality loss, not permission to accept poor-quality samples: https://choosemuse.my.site.com/s/article/Understanding-Gaps-in-my-Session-Data . Hush uses its own 20-consecutive-unavailable-second notice and two-second recovery policy; this is not a reproduction of Muse proprietary processing.
