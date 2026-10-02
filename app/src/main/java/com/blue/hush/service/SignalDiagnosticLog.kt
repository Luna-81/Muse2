package com.blue.hush.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.blue.hush.processing.SignalDiagnostics
import com.blue.hush.processing.SignalRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Debug-only trace of the latest live session, without raw sensor data. */
internal class SignalDiagnosticLog(context: Context, private val sessionId: Long) {
    private val file = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        File(context.filesDir, "signal-diagnostics.jsonl") else null
    private var writable = true

    init {
        file?.let { target ->
            runCatching { target.writeText("") }.onFailure { disable(it) }
        }
        event("start")
    }

    @Synchronized
    fun event(type: String, details: String? = null) {
        write(JSONObject().put("event", type).put("details", details ?: JSONObject.NULL))
    }

    @Synchronized
    fun sample(second: Int, connected: Boolean, skippedSeconds: Int, diagnostics: SignalDiagnostics) {
        write(JSONObject()
            .put("event", "sample")
            .put("second", second)
            .put("algorithm_version", SignalRules.VERSION)
            .put("connected", connected)
            .put("skipped_seconds", skippedSeconds)
            .put("raw_eeg_packets", diagnostics.rawEegPackets)
            .put("band_packets", JSONArray(diagnostics.bandPackets))
            .put("accepted_band_packets", JSONArray(diagnostics.acceptedBandPackets))
            .put("quality_accepted", JSONArray(diagnostics.qualityAccepted))
            .put("quality_rejected", JSONArray(diagnostics.qualityRejected))
            .put("quality_unknown", JSONArray(diagnostics.qualityUnknown))
            .put("acceleration_packets", diagnostics.accelerationPackets)
            .put("ppg_packets", diagnostics.ppgPackets)
            .put("numeric_channels", diagnostics.numericChannels)
            .put("usable_channels", diagnostics.usableChannels)
            // This is the latest flag, not necessarily the flag that accepted an earlier packet.
            .put("quality_fresh", diagnostics.qualityFresh)
            .put("quality_age_ms", diagnostics.qualityAgeMillis ?: JSONObject.NULL)
            .put("is_good", JSONArray(diagnostics.quality.map { if (it.isFinite()) it else JSONObject.NULL }))
            .put("interference_rejected", JSONArray(diagnostics.interferenceRejected))
            .put("hsi_precision", JSONArray(diagnostics.fit.map { if (it.isFinite()) it else JSONObject.NULL }))
            .put("fit_fresh", diagnostics.fitFresh)
            .put("fit_age_ms", diagnostics.fitAgeMillis ?: JSONObject.NULL)
            .put("calibration_seconds", diagnostics.calibrationSeconds)
            .put("eeg_status", diagnostics.eegStatus.name)
            .put("status", diagnostics.status))
    }

    private fun write(row: JSONObject) {
        val target = file ?: return
        if (!writable) return
        // Bound disk use even if a device repeatedly emits connection events.
        if (target.length() >= 2 * 1024 * 1024) {
            writable = false
            return
        }
        row.put("session_id", sessionId).put("monotonic_ms", SystemClock.elapsedRealtime())
        runCatching { target.appendText("$row\n") }.onFailure { disable(it) }
    }

    private fun disable(error: Throwable) {
        writable = false
        Log.w("HushSignal", "Signal diagnostics could not be written", error)
    }
}
