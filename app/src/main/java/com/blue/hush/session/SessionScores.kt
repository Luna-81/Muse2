package com.blue.hush.session

/** Unrounded experimental scores; presentation rounds only the displayed numbers. */
data class SessionScores(
    val calm: Double? = null,
    val focus: Double? = null,
    val stability: Double? = null,
    val overall: Double? = null,
    val grade: String? = null,
)
