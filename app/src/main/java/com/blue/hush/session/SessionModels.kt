package com.blue.hush.session

const val BUNDLED_SIMULATION_SESSION_ID = -1L

enum class SessionPhase {
    IDLE,
    CONNECTING,
    RUNNING,
    PAUSED,
    FINISHED,
}

enum class MusicTrack(val title: String, val subtitle: String) {
    // Keep persisted enum names and historical labels compatible with earlier sessions.
    MIST("Mist", "Soft low tones with a breathing texture"),
    TIDE("Tide", "Slowly rising and falling dual tones"),
    RAIN("Rain", "Steady rainfall with distant thunder"),
    OCEAN("Ocean", "Gentle waves washing over the shore"),
    FIREPLACE("Fireplace", "Warm fire and soft wood crackles");

    companion object {
        val soundscapes = listOf(RAIN, OCEAN, FIREPLACE)
    }
}

enum class ResultLabel(val title: String, val description: String) {
    STEADY("Steady", "Your state changed only slightly during this session."),
    SETTLING("Settling", "The second half was steadier than the first."),
    VARIABLE("Variable", "Your state changed noticeably during this session."),
}

data class StateSample(
    val elapsedSeconds: Int,
    val alpha: Double? = null,
    val theta: Double? = null,
    val beta: Double? = null,
    val stillness: Double? = null,
    val valid: Boolean = false,
    // Legacy rows infer availability only for their original visual mapping.
    val eegBandsAvailable: Boolean = false,
    val heartRateBpm: Double? = null,
    val calmness: Double? = null,
    val algorithmVersion: Int = 0,
)

data class SessionSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val track: MusicTrack,
    val result: ResultLabel,
    val sampleCount: Int,
    val validSampleCount: Int,
    val resultSampleCount: Int = validSampleCount,
) {
    val isBundledSimulation: Boolean get() = id == BUNDLED_SIMULATION_SESSION_ID
}

enum class EegSignalStatus { AVAILABLE, LOW_QUALITY, INTERFERENCE, UNKNOWN, MISSING }

data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val sessionId: Long? = null,
    val plannedSeconds: Int = 20 * 60,
    val elapsedSeconds: Int = 0,
    val connected: Boolean = false,
    val deviceName: String = "",
    val dataGap: Boolean = false,
    val validSampleCount: Int = 0,
    val sampleCount: Int = 0,
    val latestSample: StateSample? = null,
    val track: MusicTrack = MusicTrack.RAIN,
    val volume: Float = 0.7f,
    val result: ResultLabel? = null,
    val message: String? = null,
    val calibrationSeconds: Int = 0,
    val eegStatus: EegSignalStatus = EegSignalStatus.UNKNOWN,
    val eegNotice: EegSignalStatus? = null,
    val calmnessSampleCount: Int = 0,
    val trendSamples: List<StateSample> = emptyList(),
    val scores: SessionScores = SessionScores(),
)
