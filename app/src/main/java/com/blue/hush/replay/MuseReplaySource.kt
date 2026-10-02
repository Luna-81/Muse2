package com.blue.hush.replay

import android.content.Context
import com.blue.hush.session.StateSample
import com.blue.hush.storage.BundledSessionSource

/** Replay the second-earliest recorded session, retaining scores, heart rate, and gaps. */
object MuseReplaySource {
    const val DURATION_SECONDS = 10 * 60
    const val MIN_SAMPLE_COUNT = DURATION_SECONDS

    fun load(context: Context): List<StateSample> = runCatching {
        BundledSessionSource.load(context, BundledSessionSource.SECOND_EARLIEST_ASSET).samples
    }.getOrDefault(emptyList())

    fun isUsable(samples: List<StateSample>): Boolean =
        samples.size == MIN_SAMPLE_COUNT &&
            samples.any { it.valid && it.calmness != null } &&
            samples.withIndex().all { (index, sample) ->
                // Missing measurements are part of the recording, not a broken replay file.
                sample.elapsedSeconds == index + 1 && sample.algorithmVersion > 0 &&
                    listOf(sample.alpha, sample.theta, sample.beta, sample.stillness, sample.calmness)
                        .all { it == null || (it.isFinite() && it in 0.0..1.0) } &&
                    (sample.heartRateBpm == null || (sample.heartRateBpm.isFinite() && sample.heartRateBpm in 40.0..180.0)) &&
                    (sample.calmness == null || (sample.valid && sample.eegBandsAvailable))
            }
}
