package com.blue.hush.replay

import com.blue.hush.session.StateSample
import kotlin.math.abs
import kotlin.math.roundToInt

class ReplayCursor(private val samples: List<StateSample>) {
    fun sampleAt(progress: Float): StateSample? {
        if (samples.isEmpty()) return null
        val index = (progress.coerceIn(0f, 1f) * samples.lastIndex).roundToInt()
        return samples[index]
    }

    // Chart positions use elapsed time, including missing/invalid recorded seconds.
    fun progressAtSecond(second: Float): Float {
        if (samples.size < 2 || !second.isFinite()) return 0f
        val match = samples.binarySearch { it.elapsedSeconds.toFloat().compareTo(second) }
        val index = if (match >= 0) match else {
            val next = (-match - 1).coerceIn(0, samples.lastIndex)
            val previous = (next - 1).coerceAtLeast(0)
            if (abs(samples[previous].elapsedSeconds - second) <= abs(samples[next].elapsedSeconds - second)) previous else next
        }
        return index.toFloat() / samples.lastIndex
    }
}
