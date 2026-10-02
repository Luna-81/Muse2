package com.blue.hush.session

/** Independent session metrics; presentation rounds only the displayed numbers. */
data class SessionScores(
    val calm: Double? = null,
    val stability: Double? = null,
    val heartRateBpm: Double? = null,
)
