package com.blue.hush.session

/** Presentation grace only; never changes sample acceptance or calibration. */
internal class EegNoticeTracker {
    private var lastSecond: Int? = null
    private var unavailableSeconds = 0
    private var recoverySeconds = 0
    private var notice: EegSignalStatus? = null

    fun update(second: Int, status: EegSignalStatus): EegSignalStatus? {
        val previous = lastSecond
        if (previous != null && second <= previous) return notice
        val skipped = if (previous == null) 0 else (second - previous - 1).coerceAtLeast(0)
        if (skipped > 0) {
            recoverySeconds = 0
            unavailableSeconds += skipped
        }
        lastSecond = second
        if (status == EegSignalStatus.AVAILABLE) {
            // Before warning, only uninterrupted loss counts toward the grace period.
            if (notice == null) unavailableSeconds = 0
            recoverySeconds++
            if (recoverySeconds >= 2) {
                unavailableSeconds = 0
                notice = null
            }
        } else {
            recoverySeconds = 0
            unavailableSeconds++
            // A recovered fit must not keep an old contact warning during interference.
            if (unavailableSeconds >= 20) notice = status
        }
        return notice
    }

    fun reset() {
        lastSecond = null
        unavailableSeconds = 0
        recoverySeconds = 0
        notice = null
    }
}
