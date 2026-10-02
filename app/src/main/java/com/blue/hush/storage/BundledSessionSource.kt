package com.blue.hush.storage

import android.content.Context
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.ResultLabel
import com.blue.hush.session.StateSample
import org.json.JSONObject

internal data class BundledSession(
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val track: MusicTrack,
    val result: ResultLabel,
    val samples: List<StateSample>,
)

/** Decode recorded samples identically for history restoration and simulation. */
internal object BundledSessionSource {
    const val EARLIEST_ASSET = "history/earliest_session.json"
    const val SECOND_EARLIEST_ASSET = "history/second_earliest_session.json"

    fun load(context: Context, assetPath: String): BundledSession {
        val source = context.assets.open(assetPath).bufferedReader().use { JSONObject(it.readText()) }
        require(source.getInt("format_version") == 1) { "Unsupported bundled history format" }
        val session = source.getJSONObject("session")
        val rows = session.getJSONArray("samples")
        val samples = List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val alpha = row.getDoubleOrNull("alpha")
            val theta = row.getDoubleOrNull("theta")
            val beta = row.getDoubleOrNull("beta")
            val valid = row.getInt("valid") == 1
            val version = row.getInt("algorithm_version")
            StateSample(
                elapsedSeconds = row.getInt("elapsed_seconds"),
                alpha = alpha, theta = theta, beta = beta,
                stillness = row.getDoubleOrNull("stillness"),
                heartRateBpm = row.getDoubleOrNull("heart_rate_bpm"),
                calmness = row.getDoubleOrNull("calmness"),
                algorithmVersion = version, valid = valid,
                // Reconstruct the live-only flag using the same rule as persisted history.
                eegBandsAvailable = version > 0 && valid && listOf(alpha, theta, beta).all { it != null },
            )
        }
        return BundledSession(
            startedAt = session.getLong("started_at"), endedAt = session.getLong("ended_at"),
            plannedSeconds = session.getInt("planned_seconds"), actualSeconds = session.getInt("actual_seconds"),
            track = MusicTrack.valueOf(session.getString("track")),
            result = ResultLabel.valueOf(session.getString("result")), samples = samples,
        )
    }

    private fun JSONObject.getDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else getDouble(key)
}
