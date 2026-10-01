package com.blue.hush.processing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.round
import kotlin.math.roundToInt

/** Bounded, nominal-64Hz PPG window. Channel values represent one simultaneous sample. */
class HeartRateEstimator {
    private val values = ArrayDeque<Double>()
    private val arrivalTimes = ArrayDeque<Long>()
    private var channel: Int? = null
    private var lastArrival: Long? = null
    private var previousInput = 0.0
    private var highPass = 0.0
    private var lowPass = 0.0
    private val highPassFactor = exp(-2 * PI * SignalRules.PPG_LOW_HZ / SignalRules.PPG_HZ)
    private val lowPassFactor = 1 - exp(-2 * PI * SignalRules.PPG_HIGH_HZ / SignalRules.PPG_HZ)

    fun accept(ir: Double?, red: Double?, receivedAtMillis: Long, qualityGood: Boolean = true) {
        val selected = if (ir != null && ir.isFinite() && ir > 0) 0 else if (red != null && red.isFinite() && red > 0) 1 else null
        val previousArrival = lastArrival
        if (!qualityGood || selected == null) { reset(); return }
        if (channel != selected || previousArrival != null && (receivedAtMillis < previousArrival || receivedAtMillis - previousArrival > SignalRules.SENSOR_GAP_MILLIS)) reset()
        val input = if (selected == 0) ir!! else red!!
        if (channel == null) {
            previousInput = input
            channel = selected
        }
        highPass = highPassFactor * (highPass + input - previousInput)
        previousInput = input
        lowPass += lowPassFactor * (highPass - lowPass)
        if (!lowPass.isFinite()) { reset(); return }
        values.addLast(lowPass)
        arrivalTimes.addLast(receivedAtMillis)
        if (values.size > SignalRules.PPG_HZ * SignalRules.PPG_WINDOW_SECONDS) { values.removeFirst(); arrivalTimes.removeFirst() }
        lastArrival = receivedAtMillis
    }

    fun estimate(nowMillis: Long): Double? {
        val arrival = lastArrival ?: return null
        if (nowMillis - arrival > SignalRules.SENSOR_GAP_MILLIS || nowMillis < arrival) { reset(); return null }
        if (values.size < SignalRules.PPG_HZ * SignalRules.PPG_WINDOW_SECONDS) return null
        val durationSeconds = (arrivalTimes.last() - arrivalTimes.first()) / 1000.0
        val nominalSeconds = (values.size - 1).toDouble() / SignalRules.PPG_HZ
        if (abs(durationSeconds - nominalSeconds) > nominalSeconds * SignalRules.PPG_CADENCE_TOLERANCE) return null
        val signal = values.toList()
        val mean = signal.average()
        val deviation = sqrt(signal.sumOf { (it - mean) * (it - mean) } / signal.size)
        if (!deviation.isFinite() || deviation < SignalRules.EPSILON) return null
        val peaks = mutableListOf<Double>()
        val refractory = SignalRules.PPG_REFRACTORY_SECONDS * SignalRules.PPG_HZ
        for (i in 1 until signal.lastIndex) {
            if (signal[i] > mean + deviation * SignalRules.PEAK_THRESHOLD_STD && signal[i] > signal[i - 1] && signal[i] >= signal[i + 1]) {
                // Sub-sample peaks avoid rounding 333ms up to 344ms at 64Hz and losing 180 BPM beats.
                val curvature = signal[i - 1] - 2 * signal[i] + signal[i + 1]
                val offset = if (abs(curvature) > SignalRules.EPSILON) (0.5 * (signal[i - 1] - signal[i + 1]) / curvature).coerceIn(-0.5, 0.5) else 0.0
                val peak = i + offset
                val previous = peaks.lastOrNull()
                if (previous == null || peak - previous >= refractory) peaks += peak
                else if (signal[i] > signal[previous.roundToInt()]) peaks[peaks.lastIndex] = peak
            }
        }
        if (peaks.size < 4) return null
        val intervals = peaks.zipWithNext { a, b -> (b - a) / SignalRules.PPG_HZ }
        val interval = intervals.median()
        if (intervals.map { abs(it - interval) }.median() / interval > SignalRules.INTERVAL_RELATIVE_MAD) return null
        // Also reject windows with many inconsistent beats; median alone can hide random peaks.
        if (intervals.count { abs(it - interval) / interval <= SignalRules.INTERVAL_RELATIVE_MAD }.toDouble() / intervals.size < SignalRules.CONSISTENT_BEAT_FRACTION) return null
        return (round(60 / interval * 10) / 10).takeIf { it.isFinite() && it in SignalRules.HEART_MIN_BPM..SignalRules.HEART_MAX_BPM }
    }

    fun reset() {
        values.clear()
        arrivalTimes.clear()
        channel = null
        lastArrival = null
        previousInput = 0.0
        highPass = 0.0
        lowPass = 0.0
    }
}
