"""Synthetic checks for reference extraction, independent of network and real subjects."""
import math
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from cogwear_reference import BANDS, CHANNELS, download, participant_features, reference, relative_powers


def row(second=0, channel="TP9", powers=(0, 0, 0, 0, 0), **overrides):
    value = {"time": str(1000 + second), "HeadBandOn": "1", "Elements": ""}
    for name in CHANNELS:
        value["HSI_" + name] = "4"
    value["HSI_" + channel] = "1"
    for band, power in zip(BANDS, powers):
        value[band + "_" + channel] = str(power)
    value.update(overrides)
    return value


class ReferenceTest(unittest.TestCase):
    def test_log_conversion_is_normalized_and_stable(self):
        expected = [1 / 14, 1 / 14, 10 / 14, 1 / 14, 1 / 14]
        for logs in ([0, 0, 1, 0, 0], [1000, 1000, 1001, 1000, 1000]):
            for target, actual in zip(expected, relative_powers(logs)):
                self.assertAlmostEqual(target, actual)
        with self.assertRaises(ValueError):
            relative_powers([0, 0, math.nan, 0, 0])

    def test_channels_have_equal_weight_despite_uneven_row_counts(self):
        rows = [row(powers=(0, 0, 1, 0, 0)) for _ in range(8)]
        rows.append(row(channel="AF7", powers=(0, 0, 0, 1, 0)))
        features, stats = participant_features(rows)
        self.assertEqual(1, stats["valid_seconds"])
        alpha, theta, beta = 11 / 28, 1 / 14, 11 / 28
        self.assertAlmostEqual(math.log((alpha + theta + 1e-6) / (beta + 1e-6)), features[0])

    def test_artifacts_discard_whole_second_and_invalid_channels_are_excluded(self):
        rows = [row(0), row(0.1, Elements="/muse/elements/blink"),
                row(1, HSI_TP9="3"), row(2, Alpha_TP9="NaN"),
                row(3, HeadBandOn="0"), row(4)]
        features, stats = participant_features(rows)
        self.assertEqual(1, len(features))
        self.assertEqual(1, stats["exclusions"]["artifact_seconds"])
        self.assertEqual(3, stats["exclusions"]["no_usable_channel_seconds"])
        self.assertEqual(1, stats["exclusions"]["not_worn_rows"])

    def test_smoothing_preserves_state_across_missing_seconds(self):
        rows = [row(0), row(1, HeadBandOn="0"), row(2, powers=(0, 0, 1, 0, 0))]
        values, _ = participant_features(rows)
        alpha = 0.2 + 0.2 * (10 / 14 - 0.2)
        other = 0.2 + 0.2 * (1 / 14 - 0.2)
        self.assertAlmostEqual(math.log((alpha + other + 1e-6) / (other + 1e-6)), values[1])

    def test_asynchronous_row_order_uses_timestamp_bins(self):
        values, stats = participant_features([row(1), row(0), row(1, Elements="jaw_clench")])
        self.assertEqual(1, len(values))
        self.assertEqual(2, stats["observed_seconds"])
        self.assertEqual(1, stats["exclusions"]["artifact_seconds"])

    def test_checksum_mismatch_never_promotes_a_download(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "eeg.csv"
            with patch("cogwear_reference.urllib.request.urlopen", return_value=io.BytesIO(b"corrupt")):
                with self.assertRaisesRegex(ValueError, "SHA256 mismatch"):
                    download("eeg.csv", target, "0" * 64)
            self.assertFalse(target.exists())
            self.assertFalse(target.with_suffix(".csv.partial").exists())

    def test_reference_is_subject_balanced_and_requires_every_subject(self):
        groups = [[float(i)] * 30 for i in range(10)]
        groups[-1] *= 100
        baseline, scale = reference(groups)
        self.assertEqual(4.5, baseline)
        self.assertAlmostEqual(1.4826 * 2.5, scale)
        self.assertEqual((2, 0.15), reference([[2] * 30 for _ in range(10)]))
        groups[0] = [0] * 29
        with self.assertRaises(ValueError):
            reference(groups)
        groups[0] = []
        with self.assertRaises(ValueError):
            reference(groups)


if __name__ == "__main__":
    unittest.main()
