package com.blue.hush.processing

import com.blue.hush.session.EegSignalStatus

/** Packet counts describe the latest sampling window, before quality filtering. */
data class SignalDiagnostics(
    val rawEegPackets: Int = 0,
    val bandPackets: List<Int> = listOf(0, 0, 0),
    val acceptedBandPackets: List<Int> = listOf(0, 0, 0),
    val qualityAccepted: List<Int> = listOf(0, 0, 0, 0),
    val qualityRejected: List<Int> = listOf(0, 0, 0, 0),
    val qualityUnknown: List<Int> = listOf(0, 0, 0, 0),
    val accelerationPackets: Int = 0,
    val ppgPackets: Int = 0,
    val numericChannels: Int = 0,
    val usableChannels: Int = 0,
    val qualityFresh: Boolean = false,
    val qualityAgeMillis: Long? = null,
    val quality: List<Double> = emptyList(),
    val interferenceRejected: List<Int> = listOf(0, 0, 0, 0),
    val fit: List<Double> = emptyList(),
    val fitAgeMillis: Long? = null,
    val fitFresh: Boolean = false,
    val status: String = "NOT_COLLECTING",
    val eegStatus: EegSignalStatus = EegSignalStatus.UNKNOWN,
)
