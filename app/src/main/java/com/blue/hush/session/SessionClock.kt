package com.blue.hush.session

/** Monotonic session clock whose elapsed time excludes pauses. */
class SessionClock {
    private var activeSinceMillis: Long? = null
    private var accumulatedMillis: Long = 0L

    fun start(nowMillis: Long) {
        accumulatedMillis = 0L
        activeSinceMillis = nowMillis
    }

    fun pause(nowMillis: Long) {
        val activeSince = activeSinceMillis ?: return
        accumulatedMillis += (nowMillis - activeSince).coerceAtLeast(0L)
        activeSinceMillis = null
    }

    fun resume(nowMillis: Long) {
        if (activeSinceMillis == null) activeSinceMillis = nowMillis
    }

    fun elapsedMillis(nowMillis: Long): Long {
        val activeSince = activeSinceMillis ?: return accumulatedMillis
        return accumulatedMillis + (nowMillis - activeSince).coerceAtLeast(0L)
    }

}
