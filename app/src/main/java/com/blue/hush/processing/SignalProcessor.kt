package com.blue.hush.processing

import com.blue.hush.session.StateSample
import com.blue.hush.session.EegSignalStatus
import com.choosemuse.libmuse.MuseDataPacketType
import kotlin.math.exp
import kotlin.math.sqrt

/** Aggregates measured fields per second; unavailable fields remain null. */
class SignalProcessor(private val smoothingFactor: Double = SignalRules.SMOOTHING) {
    private val bands = List(3) { mutableListOf<List<Double>>() }
    private val acceptedBands = List(3) { mutableListOf<List<Double?>>() }
    private val interferenceRejected = IntArray(4)
    private val qualityAccepted = IntArray(4)
    private val qualityRejected = IntArray(4)
    private val qualityUnknown = IntArray(4)
    private val motionEnergy = mutableListOf<Double>()
    private val heart = HeartRateEstimator()
    private val calmness = CalmnessEstimator(smoothingFactor)
    private var gravity: DoubleArray? = null
    private var lastAcceleration: Long? = null
    private var eegQuality: List<Double> = emptyList()
    private var eegQualityAt: Long? = null
    private var eegFit: List<Double> = emptyList()
    private var eegFitAt: Long? = null
    private var ppgGood: Boolean? = null
    private var ppgQualityAt: Long? = null
    private var heartGood: Boolean? = null
    private var heartQualityAt: Long? = null
    private var receivedSensorData = false
    private var collecting = true
    private var rawEegPackets = 0
    private var accelerationPackets = 0
    private var ppgPackets = 0
    private var diagnostics = SignalDiagnostics()
    private val smoothedBands = arrayOfNulls<Double>(3)
    private var smoothedStillness: Double? = null

    @get:Synchronized
    val latestDiagnostics: SignalDiagnostics get() = diagnostics

    @Synchronized
    fun accept(type: MuseDataPacketType, values: List<Double>, receivedAtMillis: Long = monotonicMillis()) {
        if (!collecting || values.isEmpty()) return
        when (type) {
            MuseDataPacketType.EEG -> rawEegPackets++
            MuseDataPacketType.ACCELEROMETER -> accelerationPackets++
            MuseDataPacketType.PPG -> ppgPackets++
            else -> Unit
        }
        when (type) {
            MuseDataPacketType.ALPHA_RELATIVE -> acceptBand(0, values, receivedAtMillis)
            MuseDataPacketType.THETA_RELATIVE -> acceptBand(1, values, receivedAtMillis)
            MuseDataPacketType.BETA_RELATIVE -> acceptBand(2, values, receivedAtMillis)
            MuseDataPacketType.IS_GOOD -> { eegQuality = values.take(4); eegQualityAt = receivedAtMillis }
            MuseDataPacketType.HSI_PRECISION -> { eegFit = values.take(4); eegFitAt = receivedAtMillis }
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

    private fun acceptBand(index: Int, values: List<Double>, receivedAtMillis: Long) {
        bands[index] += values.take(4)
        if (values.take(4).any { it.isFinite() && it in 0.0..1.0 }) receivedSensorData = true
        val fresh = eegQualityAt?.let { receivedAtMillis - it in 0..SignalRules.QUALITY_TTL_MILLIS } == true
        val fitFresh = eegFitAt?.let { receivedAtMillis - it in 0..SignalRules.QUALITY_TTL_MILLIS } == true
        // Evaluate quality at arrival. Later flags cannot retroactively accept or erase a packet.
        val accepted = List(4) { channel ->
            val value = values.getOrNull(channel)?.takeIf { it.isFinite() && it in 0.0..1.0 }
            val quality = if (fresh) eegQuality.getOrNull(channel) else null
            val fit = if (fitFresh) eegFit.getOrNull(channel)?.takeIf { it.isFinite() && it in 1.0..4.0 } else null
            // Fit and artifact quality are independent: good contact cannot clean a blink.
            when {
                value == null -> null
                fit != null && fit > 2.0 -> { qualityRejected[channel]++; null }
                quality == 1.0 -> { qualityAccepted[channel]++; value }
                quality == 0.0 -> {
                    qualityRejected[channel]++
                    if (fit != null) interferenceRejected[channel]++
                    null
                }
                else -> { qualityUnknown[channel]++; null }
            }
        }
        if (accepted.any { it != null }) acceptedBands[index] += accepted
    }

    @Synchronized
    fun nextSample(elapsedSeconds: Int, nowMillis: Long = monotonicMillis()): StateSample {
        val qualityFresh = eegQualityAt?.let { nowMillis - it in 0..SignalRules.QUALITY_TTL_MILLIS } == true
        val numericChannels = (0 until (bands.flatMap { it }.maxOfOrNull { it.size } ?: 0)).filter { channel ->
            bands.all { packets -> packets.any { it.getOrNull(channel)?.let { value -> value.isFinite() && value in 0.0..1.0 } == true } }
        }
        val channels = (0..3).filter { channel ->
            acceptedBands.all { packets -> packets.any { it[channel] != null } }
        }
        val aggregated = acceptedBands.map { packets ->
            // Equal channel weights match the offline resting reference extraction.
            channels.map { channel -> packets.mapNotNull { it[channel] }.average() }
                .takeIf { it.isNotEmpty() }?.average()
        }
        // Persist only complete trusted bands; partial rows must never become EEG measurements.
        val available = aggregated.all { it != null } && aggregated.sumOf { it ?: 0.0 } > 0
        val raw = if (available) aggregated else List(3) { null }
        val eegStatus = when {
            available -> EegSignalStatus.AVAILABLE
            bands.any { it.isEmpty() } -> EegSignalStatus.MISSING
            numericChannels.isEmpty() -> EegSignalStatus.LOW_QUALITY
            qualityUnknown.any { it > 0 } -> EegSignalStatus.UNKNOWN
            numericChannels.any { interferenceRejected[it] > 0 } -> EegSignalStatus.INTERFERENCE
            else -> EegSignalStatus.LOW_QUALITY
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
            eegBandsAvailable = available, heartRateBpm = bpm,
        ))
        diagnostics = SignalDiagnostics(
            rawEegPackets = rawEegPackets,
            bandPackets = bands.map { it.size },
            acceptedBandPackets = acceptedBands.map { it.size },
            qualityAccepted = qualityAccepted.toList(),
            qualityRejected = qualityRejected.toList(),
            qualityUnknown = qualityUnknown.toList(),
            accelerationPackets = accelerationPackets,
            ppgPackets = ppgPackets,
            numericChannels = numericChannels.size,
            usableChannels = if (available) channels.size else 0,
            qualityFresh = qualityFresh,
            qualityAgeMillis = eegQualityAt?.let { nowMillis - it },
            quality = eegQuality.toList(),
            interferenceRejected = interferenceRejected.toList(),
            fit = eegFit.toList(),
            fitAgeMillis = eegFitAt?.let { nowMillis - it },
            fitFresh = eegFitAt?.let { nowMillis - it in 0..SignalRules.QUALITY_TTL_MILLIS } == true,
            status = when {
                !collecting -> "NOT_COLLECTING"
                bands.all { it.isEmpty() } -> if (rawEegPackets > 0) "EEG_WITHOUT_BANDS" else "NO_EEG_PACKETS"
                bands.any { it.isEmpty() } -> "INCOMPLETE_BANDS"
                numericChannels.isEmpty() -> "INVALID_BANDS"
                eegStatus == EegSignalStatus.UNKNOWN -> "QUALITY_UNKNOWN"
                eegStatus == EegSignalStatus.INTERFERENCE -> "INTERFERENCE"
                !available -> "LOW_QUALITY"
                else -> "READY"
            },
            eegStatus = eegStatus,
        )
        clearPacketCounts()
        bands.forEach { it.clear() }
        acceptedBands.forEach { it.clear() }
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
    }

    @Synchronized
    fun reset() {
        clearWindows()
        calmness.reset()
        smoothedBands.fill(null)
        smoothedStillness = null
        diagnostics = SignalDiagnostics()
    }

    private fun clearWindows() {
        clearPacketCounts()
        bands.forEach { it.clear() }
        acceptedBands.forEach { it.clear() }
        motionEnergy.clear()
        receivedSensorData = false
        heart.reset()
        gravity = null
        lastAcceleration = null
        eegQuality = emptyList()
        eegQualityAt = null
        eegFit = emptyList()
        eegFitAt = null
        ppgGood = null
        ppgQualityAt = null
        heartGood = null
        heartQualityAt = null
    }

    private fun smooth(previous: Double?, current: Double): Double = previous?.let { it + smoothingFactor * (current - it) } ?: current

    private fun clearPacketCounts() {
        rawEegPackets = 0
        accelerationPackets = 0
        ppgPackets = 0
        qualityAccepted.fill(0)
        qualityRejected.fill(0)
        qualityUnknown.fill(0)
        interferenceRejected.fill(0)
    }
    private fun qualityAllows(value: Boolean?, timestamp: Long?, now: Long): Boolean =
        !(value == false && timestamp != null && now - timestamp in 0..SignalRules.QUALITY_TTL_MILLIS)

    private companion object {
        fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
    }
}
