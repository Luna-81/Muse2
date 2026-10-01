package com.blue.hush.replay

import android.content.Context
import com.blue.hush.processing.CalmnessEstimator
import com.blue.hush.session.StateSample

/** Loads the checked-in ten-minute Muse session used by the device simulation mode. */
object MuseReplaySource {
    const val DURATION_SECONDS = 10 * 60
    const val MIN_SAMPLE_COUNT = DURATION_SECONDS

    private const val ASSET_PATH = "simulation/muse_last_10m.csv"

    fun load(context: Context): List<StateSample> = runCatching {
        val estimator = CalmnessEstimator()
        context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
            lines
                .drop(1)
                .mapNotNull(::parseLine)
                .sortedBy { it.elapsedSeconds }
                .map(estimator::process)
                .toList()
        }
    }.getOrDefault(emptyList())

    fun isUsable(samples: List<StateSample>): Boolean =
        samples.size == MIN_SAMPLE_COUNT &&
            samples.withIndex().all { (index, sample) ->
                sample.elapsedSeconds == index + 1 && sample.valid &&
                    listOf(sample.alpha, sample.theta, sample.beta, sample.stillness)
                        .all { it != null && it.isFinite() && it in 0.0..1.0 }
            }

    private fun parseLine(line: String): StateSample? {
        val columns = line.split(',')
        if (columns.size != 6) return null
        val elapsedSeconds = columns[0].toIntOrNull() ?: return null
        val alpha = columns[1].toDoubleOrNull() ?: return null
        val theta = columns[2].toDoubleOrNull() ?: return null
        val beta = columns[3].toDoubleOrNull() ?: return null
        val stillness = columns[4].toDoubleOrNull() ?: return null
        val valid = columns[5] == "1"
        val bandsAvailable = valid && listOf(alpha, theta, beta).all { it in 0.0..1.0 }
        return StateSample(
            elapsedSeconds = elapsedSeconds,
            alpha = alpha,
            theta = theta,
            beta = beta,
            stillness = stillness,
            valid = valid,
            // Historical database rows do not persist this live-only flag. The
            // checked-in replay is known-good sample data, so restore it here.
            eegBandsAvailable = bandsAvailable,
        )
    }
}
