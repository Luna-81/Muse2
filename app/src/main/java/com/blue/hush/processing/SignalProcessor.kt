package com.blue.hush.processing

import com.blue.hush.session.StateSample
import com.choosemuse.libmuse.MuseDataPacketType
import kotlin.math.exp
import kotlin.math.sqrt

/** Aggregates measured fields per second; unavailable fields remain null. */
class SignalProcessor(private val smoothingFactor: Double = SignalRules.SMOOTHING) {
    private val bands = List(3) { mutableListOf<List<Double>>() }
    private val motionEnergy = mutableListOf<Double>()
    private val heart = HeartRateEstimator()
    private val calmness = CalmnessEstimator(smoothingFactor)
    private var gravity: DoubleArray? = null
    private var lastAcceleration: Long? = null
    private var eegQuality: List<Double> = emptyList()
    private var eegQualityAt: Long? = null
    private var ppgGood: Boolean? = null
    private var ppgQualityAt: Long? = null
    private var heartGood: Boolean? = null
    private var heartQualityAt: Long? = null
    private var receivedSensorData = false
    private var collecting = true
    private val smoothedBands = arrayOfNulls<Double>(3)
    private var smoothedStillness: Double? = null

    @get:Synchronized
    val calibrationSeconds: Int get() = calmness.calibrationSeconds

    @Synchronized
    fun accept(type: MuseDataPacketType, values: List<Double>, receivedAtMillis: Long = monotonicMillis()) {
        if (!collecting || values.isEmpty()) return
        when (type) {
            MuseDataPacketType.ALPHA_RELATIVE -> acceptBand(0, values)
            MuseDataPacketType.THETA_RELATIVE -> acceptBand(1, values)
            MuseDataPacketType.BETA_RELATIVE -> acceptBand(2, values)
            MuseDataPacketType.IS_GOOD -> { eegQuality = values; eegQualityAt = receivedAtMillis }
            MuseDataPacketType.IS_PPG_GOOD -> {
                ppgGood = values.any { it.isFinite() && it > 0 }; ppgQualityAt = receivedAtMillis
                if (ppgGood == false) heart.reset()
            }
            MuseDataPacketType.IS_HEART_GOOD -> {
                heartGood = values.any { it.isFinite() && it > 0 }; heartQualityAt = receivedAtMillis
                if (heartGood == false) heart.reset()
            }
            MuseDataPacketType.ACCELEROMETER -> {
                if (values.size >= 3 && values.take(3).all { it.isFinite() }) {
                    receivedSensorData = true
                    val vector = values.take(3).toDoubleArray()
                    val previous = lastAcceleration
                    if (previous != null && (receivedAtMillis < previous || receivedAtMillis - previous > SignalRules.SENSOR_GAP_MILLIS)) {
                        gravity = null
                        motionEnergy.clear()
                    }
                    val estimate = gravity ?: vector.copyOf().also { gravity = it }
                    val dt = if (previous == null || receivedAtMillis <= previous) 1 / SignalRules.ACCELEROMETER_HZ
                        else ((receivedAtMillis - previous) / 1000.0).coerceAtMost(0.1)
                    val factor = 1 - exp(-dt / SignalRules.GRAVITY_SECONDS)
                    var energy = 0.0
                    for (axis in 0..2) {
                        estimate[axis] += factor * (vector[axis] - estimate[axis])
                        val dynamic = vector[axis] - estimate[axis]
                        energy += dynamic * dynamic
                    }
                    if (energy.isFinite()) motionEnergy += energy
                    lastAcceleration = receivedAtMillis
                }
            }
            MuseDataPacketType.PPG -> {
                // Adapter contract: IR and Red are concurrent channels, not sequential samples.
                val good = qualityAllows(ppgGood, ppgQualityAt, receivedAtMillis) && qualityAllows(heartGood, heartQualityAt, receivedAtMillis)
                heart.accept(values.getOrNull(0), values.getOrNull(1), receivedAtMillis, good)
                if (values.any { it.isFinite() && it > 0 }) receivedSensorData = true
            }
            MuseDataPacketType.EEG -> if (values.any { it.isFinite() }) receivedSensorData = true
            else -> Unit
        }
    }

    private fun acceptBand(index: Int, values: List<Double>) {
        bands[index] += values
        if (values.any { it.isFinite() && it in 0.0..1.0 }) receivedSensorData = true
    }

    @Synchronized
    fun nextSample(elapsedSeconds: Int, nowMillis: Long = monotonicMillis()): StateSample {
        val qualityFresh = eegQualityAt?.let { nowMillis - it in 0..SignalRules.QUALITY_TTL_MILLIS } == true
        val channels = (0 until (bands.flatMap { it }.maxOfOrNull { it.size } ?: 0)).filter { channel ->
            (!qualityFresh || eegQuality.getOrNull(channel)?.let { it.isFinite() && it > 0 } != false) &&
                bands.all { packets -> packets.any { it.getOrNull(channel)?.let { value -> value.isFinite() && value in 0.0..1.0 } == true } }
        }
        val raw = bands.map { packets ->
            val values = packets.flatMap { packet -> packet.mapIndexedNotNull { channel, value ->
                value.takeIf { it.isFinite() && it in 0.0..1.0 &&
                    (!qualityFresh || eegQuality.getOrNull(channel)?.let { good -> good.isFinite() && good > 0 } != false) &&
                    (channels.isEmpty() || channel in channels) }
            } }
            values.takeIf { it.isNotEmpty() }?.average()
        }
        val measured = raw.mapIndexed { index, value ->
            value?.let { current -> smooth(smoothedBands[index], current).also { smoothedBands[index] = it } }
        }
        val stillness = motionEnergy.takeIf { it.isNotEmpty() }?.average()?.let { energy ->
            exp(-sqrt(energy) / SignalRules.MOTION_SCALE_G).let { smooth(smoothedStillness, it).also { value -> smoothedStillness = value } }
        }
        val bpm = if (qualityAllows(ppgGood, ppgQualityAt, nowMillis) && qualityAllows(heartGood, heartQualityAt, nowMillis)) heart.estimate(nowMillis) else null
        val sample = calmness.process(StateSample(
            elapsedSeconds = elapsedSeconds,
            alpha = measured[0], theta = measured[1], beta = measured[2], stillness = stillness,
            valid = receivedSensorData,
            eegBandsAvailable = channels.isNotEmpty() && raw.all { it != null }, heartRateBpm = bpm,
        ))
        bands.forEach { it.clear() }
        motionEnergy.clear()
        receivedSensorData = false
        return sample
    }

    @Synchronized
    fun setCollecting(enabled: Boolean) {
        // Replayed connection notifications do not represent a sensor interruption.
        if (collecting == enabled) return
        collecting = enabled
        clearWindows()
        calmness.interrupt()
    }

    @Synchronized
    fun reset() {
        clearWindows()
        calmness.reset()
        smoothedBands.fill(null)
        smoothedStillness = null
    }

    private fun clearWindows() {
        bands.forEach { it.clear() }
        motionEnergy.clear()
        receivedSensorData = false
        heart.reset()
        gravity = null
        lastAcceleration = null
        eegQuality = emptyList()
        eegQualityAt = null
        ppgGood = null
        ppgQualityAt = null
        heartGood = null
        heartQualityAt = null
    }

    private fun smooth(previous: Double?, current: Double): Double = previous?.let { it + smoothingFactor * (current - it) } ?: current
    private fun qualityAllows(value: Boolean?, timestamp: Long?, now: Long): Boolean =
        !(value == false && timestamp != null && now - timestamp in 0..SignalRules.QUALITY_TTL_MILLIS)

    private companion object {
        fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
    }
}
