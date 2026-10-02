package com.blue.hush.muse

/** Idle discovery follows app visibility rather than the selected screen. */
object AutoConnectPolicy {
    fun eligible(appVisible: Boolean, idle: Boolean, permission: Boolean, bluetooth: Boolean,
        simulation: Boolean, paused: Boolean): Boolean = appVisible && idle && permission && bluetooth && !simulation && !paused

    fun choose(addresses: List<String>, remembered: String?): String? {
        val unique = addresses.distinct()
        return if (remembered != null) unique.firstOrNull { it == remembered } else unique.singleOrNull()
    }

    fun retryDelay(attempt: Int): Long = (2000L shl attempt.coerceIn(0, 4)).coerceAtMost(30_000L)
}
