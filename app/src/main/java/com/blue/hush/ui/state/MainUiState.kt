package com.blue.hush.ui.state

import com.blue.hush.session.MusicTrack
import com.blue.hush.session.SessionDuration
import com.blue.hush.session.SessionRuntime
import com.blue.hush.session.SessionState
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample
import com.blue.hush.ui.AppTab
import com.blue.hush.ui.ConnectionUiState

/**
 * MainActivity 之前持有的所有 UI 状态，现在集中在这里。
 *
 * 好处：
 *  - 状态一目了然，谁在 UI 上用到什么，看这个类就够了
 *  - ViewModel 只暴露一个 `uiState: MainUiState`，而不是散落十几个 var
 */
data class MainUiState(
    // ---- 会话 ----
    val sessionState: SessionState = SessionRuntime.current,

    // ---- 历史 ----
    val history: List<SessionSummary> = emptyList(),

    // ---- 当前 Tab ----
    val activeTab: AppTab = AppTab.MEDITATE,

    // ---- 准备面板选择 ----
    val selectedDurationSeconds: Int = SessionDuration.DEFAULT_SECONDS,
    val selectedTrack: MusicTrack = MusicTrack.RAIN,

    // ---- 详情页 ----
    val detailSummary: SessionSummary? = null,
    val detailSamples: List<StateSample> = emptyList(),
    val replayProgress: Float = 0f,

    // ---- 蓝牙 / 连接 ----
    val connectionState: ConnectionUiState = ConnectionUiState(),

    // ---- 模拟数据是否可用 ----
    val simulationDataAvailable: Boolean = false,

    // ---- 试听 ----
    val previewTrack: MusicTrack? = null,
)