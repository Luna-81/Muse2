package com.blue.hush.processing

/** Packet counts describe the latest sampling window, before quality filtering. */
data class SignalDiagnostics(
    val rawEegPackets: Int = 0,
    val bandPackets: List<Int> = listOf(0, 0, 0),
    val accelerationPackets: Int = 0,
    val ppgPackets: Int = 0,
    val numericChannels: Int = 0,
    val usableChannels: Int = 0,
    val qualityFresh: Boolean = false,
    val quality: List<Double> = emptyList(),
    val calibrationSeconds: Int = 0,
    val status: String = "NOT_COLLECTING",
)
