# Public EEG reference dataset

Research date: 2026-10-03. Algorithm version 5 uses a fixed public resting reference derived from 10 available pilot baseline EEG files. Participant 3 is explicitly excluded, as approved after confirming its EEG file is unpublished. The previous 11-participant gate was not met; the revised fixed cohort is IDs 0, 1, 2, 4, 5, 6, 7, 8, 9, 10.

## Selected source

[CogWear v1.0.0 on PhysioNet](https://physionet.org/content/consumer-grade-wearables/1.0.0/) provides publicly downloadable Muse S Gen2 recordings. DOI: [10.13026/5f6t-b637](https://doi.org/10.13026/5f6t-b637). The files use the [Open Data Commons Open Database License v1.0](https://physionet.org/files/consumer-grade-wearables/1.0.0/LICENSE.txt); retain attribution and check its terms before redistributing data.

The pilot cohort contains 11 participants, IDs 0–10, with separate `baseline` and `cognitive_load` folders. During baseline, participants were instructed to sit relaxed for three minutes without distractions or performing a task. Cognitive load recordings use a Stroop task. The documented baseline protocol does not specify whether eyes were open or closed.

EEG was recorded using Mind Monitor at a raw sampling rate of 256 Hz. Channels are TP9, AF7, AF8 and TP10, with Fpz as reference. This matches the electrode locations used by Hush's Muse 2, but the recording device is Muse S Gen2 rather than Muse 2.

## Verified access and schema

- [Pilot recordings](https://physionet.org/files/consumer-grade-wearables/1.0.0/pilot/)
- [Participant 0 baseline CSV](https://physionet.org/files/consumer-grade-wearables/1.0.0/pilot/0/baseline/muse_eeg.csv)
- [Dataset README](https://physionet.org/files/consumer-grade-wearables/1.0.0/README.md)
- [Column description](https://physionet.org/files/consumer-grade-wearables/1.0.0/description.csv)
- [Published checksums](https://physionet.org/files/consumer-grade-wearables/1.0.0/SHA256SUMS.txt)

The actual CSV places `time` last; the script parses column names. All 10 available pilot baseline EEG files were downloaded from PhysioNet's documented public S3 mirror and matched the official SHA256 manifest. Participant 3's [baseline directory](https://physionet.org/files/consumer-grade-wearables/1.0.0/pilot/3/baseline/) contains Empatica data but no Muse EEG file; the EEG file is also absent from the manifest. Local source files and metadata are under ignored `research-data/cogwear/`.

Mind Monitor's developer explains the conversion in [this forum response](https://mind-monitor.com/forums0/viewtopic.php?p=3434&sid=d1176461451280cba7741b0caddb5662): exponentiate log10 band power and divide by the sum of all five linear powers. The script subtracts the largest log power before exponentiation, which is algebraically equivalent and prevents overflow. This supports using the precomputed bands rather than introducing a different raw-EEG FFT pipeline; it does not prove identical measurement behavior between Muse S and Muse 2.

## Reproduction and processing

Run from the repository root with Python 3.9 or newer, using only its standard library:

```powershell
python -m unittest discover -s tools -p 'test_cogwear_reference.py'
python tools/cogwear_reference.py
```

The extraction writes [the full participant report](cogwear-reference-results.json) with `accepted: true`, the baseline and scale, and participant 3 listed separately as excluded. All 10 selected participants must have at least 30 valid seconds; a missing selected file or insufficient data still fails the gate. Re-running verifies cached files before using them. Retain the downloaded license and source attribution; raw recordings are not bundled in the App.

The script rejects rows unless `HeadBandOn == 1`; for each channel it requires finite HSI in [1,2] and all five finite band values. Missing/nonfinite values are excluded rather than inferred. Any second containing a blink, jaw or clench marker is excluded in full. Each timestamp is binned into its Unix second (`floor(time)`); asynchronous CSV row order does not determine temporal order. Each channel is averaged within the second before averaging available channels with equal weight. Alpha, Theta and Beta share the same usable channels. The three averaged bands use EMA 0.2, initialized from the first usable second and retained across gaps. The report separately counts discarded rows, channel-rows and seconds; those categories overlap and must not be added as if they were one unit.

Public HSI/artifact filtering is not equivalent to Hush's packet-time `IS_GOOD` check. In particular, lack of a public artifact marker does not prove an artifact-free EEG interval.

## Observed results

| Participant | Valid EEG seconds | Status |
| --- | ---: | --- |
| 0 | 148 | SHA256 verified |
| 1 | 117 | SHA256 verified |
| 2 | 130 | SHA256 verified |
| 3 | 0 | No published baseline EEG file |
| 4 | 118 | SHA256 verified |
| 5 | 71 | SHA256 verified |
| 6 | 132 | SHA256 verified |
| 7 | 124 | SHA256 verified |
| 8 | 160 | SHA256 verified |
| 9 | 85 | SHA256 verified |
| 10 | 92 | SHA256 verified |

All 10 selected participants exceed 30 valid seconds. The generated constants are:

```text
b = 0.76507906870225273
s = 0.32801364656479376
```

`b` is the median of the 10 participant feature medians. `s = max(1.4826 * median(per-participant median(abs(x-b))), 0.15)`. Participants have equal weight even with different valid recording lengths. These constants are compiled into `SignalRules`, not downloaded by the App. Repeated extraction produces identical report contents.

New live samples use version 5 and `sigmoid(clamp((x-b)/s, -30, 30))`, followed by Calmness EMA 0.2. New sessions initialize both band and score smoothing; interruptions retain smoothing but require fresh live quality information. Missing values stay null. History remains unchanged and replays stored scores; database version remains 5. The simulation replays the second-earliest recorded device session from `assets/history/second_earliest_session.json`, preserving its recorded version-5 Calmness, measured heart rate, and missing values without running the estimator again.

## Coursework interpretation

Use the pilot baseline recordings first. Exclude missing/nonfinite values and unusable contact intervals, aggregate usable channels into per-second Alpha, Theta and Beta powers, and calculate the same feature used by Hush:

```text
x = ln((alpha + theta + epsilon) / (beta + epsilon))
```

Compute each participant's median valid resting feature, then use the median of those participant medians as the fixed reference `b`. This gives participants equal weight despite different recording lengths. Define `x == b` to map to 50 points. The dispersion estimate above sets the fixed scoring scale.

There is no existing 50-point Calm label in this dataset. Mapping its resting reference to 50 is a coursework convention. The Stroop recordings can provide a separate comparison condition, but they should not be assumed to yield lower scores without checking. The baseline is a resting-condition reference, not proof of an individual's subjective calmness.

The reference is a coursework convention across Muse S and Muse 2, not a validated psychological-state classifier. Open/closed-eye differences, device differences and the smaller available cohort remain limitations. SDK artifact filtering and experimental PPG accuracy still require physical Muse 2 testing.
