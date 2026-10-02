"""Reproduce Hush's fixed resting reference using CogWear pilot EEG (stdlib only)."""
import argparse
import concurrent.futures
import csv
import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path
from statistics import median
import urllib.request

# PhysioNet's documented public S3 mirror; checksums still come from PhysioNet.
BASE_URL = "https://physionet-open.s3.amazonaws.com/consumer-grade-wearables/1.0.0/"
CHANNELS = ("TP9", "AF7", "AF8", "TP10")
BANDS = ("Delta", "Theta", "Alpha", "Beta", "Gamma")
SELECTED = (2, 1, 3)  # Alpha, Theta, Beta, matching SignalProcessor.
SMOOTHING = 0.2
EPSILON = 0.000001
MIN_SECONDS = 30
# Participant 3 has no published baseline EEG; the approved cohort is fixed, not auto-selected.
PARTICIPANTS = (0, 1, 2, 4, 5, 6, 7, 8, 9, 10)


def relative_powers(log_powers):
    if len(log_powers) != 5 or not all(math.isfinite(v) for v in log_powers):
        raise ValueError("Five finite log10 powers are required")
    # Equivalent to 10**value / sum(10**values), without overflow.
    offset = max(log_powers)
    powers = [10 ** (v - offset) for v in log_powers]
    total = sum(powers)
    return [v / total for v in powers]


def number(row, name):
    try:
        return float(row[name])
    except (ValueError, TypeError, KeyError):
        return float("nan")


def participant_features(rows):
    seconds = defaultdict(lambda: defaultdict(list))
    artifacts = set()
    observed = set()
    reasons = Counter()
    for row in rows:
        timestamp = number(row, "time")
        if not math.isfinite(timestamp):
            reasons["invalid_timestamp_rows"] += 1
            continue
        # CSV contains asynchronous event/sensor rows; bin by time, not file order.
        second = math.floor(timestamp)
        observed.add(second)
        event = (row.get("Elements") or "").lower()
        if "blink" in event or "jaw" in event or "clench" in event:
            artifacts.add(second)
        if number(row, "HeadBandOn") != 1:
            reasons["not_worn_rows"] += 1
            continue
        for channel in CHANNELS:
            fit = number(row, "HSI_" + channel)
            if not math.isfinite(fit) or not 1 <= fit <= 2:
                reasons["unusable_fit_channel_rows"] += 1
                continue
            logs = [number(row, band + "_" + channel) for band in BANDS]
            if not all(math.isfinite(v) for v in logs):
                reasons["missing_or_nonfinite_power_channel_rows"] += 1
                continue
            relative = relative_powers(logs)
            seconds[second][channel].append([relative[i] for i in SELECTED])
    features = []
    smoothed = None
    for second in sorted(observed):
        if second in artifacts:
            reasons["artifact_seconds"] += 1
            continue
        channels = seconds.get(second, {})
        if not channels:
            reasons["no_usable_channel_seconds"] += 1
            continue
        # Average each channel first so uneven packet counts do not weight electrodes.
        means = [[sum(row[i] for row in values) / len(values) for i in range(3)]
                 for values in channels.values()]
        bands = [sum(row[i] for row in means) / len(means) for i in range(3)]
        smoothed = bands if smoothed is None else [
            old + SMOOTHING * (new - old) for old, new in zip(smoothed, bands)]
        alpha, theta, beta = smoothed
        features.append(math.log((alpha + theta + EPSILON) / (beta + EPSILON)))
    return features, {"observed_seconds": len(observed), "valid_seconds": len(features),
                      "exclusions": dict(sorted(reasons.items()))}


def reference(groups):
    if len(groups) != len(PARTICIPANTS) or any(len(values) < MIN_SECONDS for values in groups):
        raise ValueError("All 10 selected participants must have at least 30 valid seconds")
    baseline = median([median(values) for values in groups])
    scale = max(1.4826 * median([median([abs(x - baseline) for x in values])
                               for values in groups]), 0.15)
    return baseline, scale


def download(relative_path, target, expected=None):
    if target.exists() and (expected is None or hashlib.sha256(target.read_bytes()).hexdigest() == expected):
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    temp = target.with_suffix(target.suffix + ".partial")
    with urllib.request.urlopen(BASE_URL + relative_path, timeout=120) as response, temp.open("wb") as out:
        while chunk := response.read(1024 * 1024):
            out.write(chunk)
    actual = hashlib.sha256(temp.read_bytes()).hexdigest()
    if expected is not None and actual != expected:
        temp.unlink()
        raise ValueError("SHA256 mismatch: " + relative_path)
    temp.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=Path("research-data/cogwear"))
    parser.add_argument("--output", type=Path, default=Path("docs/cogwear-reference-results.json"))
    args = parser.parse_args()
    args.data_dir.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen("https://physionet.org/files/consumer-grade-wearables/1.0.0/SHA256SUMS.txt", timeout=60) as response:
        (args.data_dir / "SHA256SUMS.txt").write_bytes(response.read())
    checksums = {}
    for line in (args.data_dir / "SHA256SUMS.txt").read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        checksums[name.lstrip("*./")] = digest
    paths = [f"pilot/{i}/baseline/muse_eeg.csv" for i in PARTICIPANTS]
    available = [name for name in paths if name in checksums]
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        jobs = [pool.submit(download, name, args.data_dir / name, checksums[name]) for name in available]
        for name, job in zip(available, jobs):
            job.result()
            print("Verified " + name, flush=True)
    for name in ("README.md", "LICENSE.txt", "description.csv"):
        download(name, args.data_dir / name, checksums.get(name))
    groups, participants = [], []
    for i, name in zip(PARTICIPANTS, paths):
        if name not in checksums:
            groups.append([])
            participants.append({"id": i, "file": name, "valid_seconds": 0,
                                 "error": "Baseline EEG file absent from the official SHA256 manifest"})
            print(f"Participant {i}: baseline EEG file missing", flush=True)
            continue
        with (args.data_dir / name).open(newline="", encoding="utf-8-sig") as stream:
            values, stats = participant_features(csv.DictReader(stream))
        groups.append(values)
        participants.append({"id": i, "file": name, "sha256": checksums[name],
                             **stats, "feature_median": median(values) if values else None})
        print(f"Participant {i}: {len(values)} valid seconds", flush=True)
    report = {"dataset": "CogWear 1.0.0", "doi": "10.13026/5f6t-b637",
              "algorithm_version": 5, "smoothing": SMOOTHING, "epsilon": EPSILON,
              "participants": participants, "excluded_participants": [{"id": 3, "reason": "No published baseline EEG file"}], "accepted": False}
    try:
        baseline, scale = reference(groups)
        report.update(accepted=True, baseline=baseline, scale=scale)
    except ValueError as error:
        report["error"] = str(error)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    if not report["accepted"]:
        raise SystemExit(report["error"])
    print(f"baseline={baseline:.17g}, scale={scale:.17g}")


if __name__ == "__main__":
    main()
