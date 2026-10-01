package com.blue.hush.session

/** Keeps a continuous second-by-second timeline and the last usable visual state. */
class SessionSamples {
    private val samples = mutableListOf<StateSample>()
    private var latestValid: StateSample? = null

    var validCount: Int = 0
        private set

    val count: Int get() = samples.size
    val all: List<StateSample> get() = samples.toList()
    val calmnessCount: Int get() = samples.count { it.valid && it.calmness?.let { value -> value.isFinite() && value in 0.0..1.0 } == true }
    val lastSecond: Int get() = samples.lastOrNull()?.elapsedSeconds ?: 0
    val visualSample: StateSample? get() = latestValid ?: samples.lastOrNull()

    /** Returns rows to persist, including explicit gaps after a delayed tick. */
    fun record(sample: StateSample): List<StateSample> {
        require(sample.elapsedSeconds > lastSecond)
        val added = (lastSecond + 1 until sample.elapsedSeconds)
            .map { StateSample(elapsedSeconds = it, algorithmVersion = sample.algorithmVersion) } + sample
        added.forEach(::append)
        return added
    }

    private fun append(sample: StateSample) {
        samples += sample
        if (sample.valid) {
            validCount++
            latestValid = sample
        }
    }

    fun clear() {
        samples.clear()
        latestValid = null
        validCount = 0
    }
}
