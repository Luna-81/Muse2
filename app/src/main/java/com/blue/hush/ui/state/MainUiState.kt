package com.blue.hush.ui.state

import com.blue.hush.session.MusicTrack
import com.blue.hush.session.SessionDuration
import com.blue.hush.session.SessionRuntime
import com.blue.hush.session.SessionState
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample
import com.blue.hush.ui.AppTab
import com.blue.hush.ui.ConnectionUiState

data class MainUiState(
    val sessionState: SessionState = SessionRuntime.current,

    val history: List<SessionSummary> = emptyList(),

    val activeTab: AppTab = AppTab.MEDITATE,

    val selectedDurationSeconds: Int = SessionDuration.DEFAULT_SECONDS,
    val selectedTrack: MusicTrack = MusicTrack.RAIN,

    val detailSummary: SessionSummary? = null,
    val detailSamples: List<StateSample> = emptyList(),
    val replayProgress: Float = 0f,

    val connectionState: ConnectionUiState = ConnectionUiState(),

    val simulationDataAvailable: Boolean = false,

    val previewTrack: MusicTrack? = null,
)