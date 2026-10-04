package com.blue.hush.session

import android.content.Context
import com.blue.hush.service.MeditationService
import com.blue.hush.ui.state.MainUiState
import com.choosemuse.libmuse.ConnectionState

/**
 * 会话编排器：把"开始会话 / 暂停 / 恢复 / 结束 / 新会话"的逻辑从 ViewModel 抽出来。
 *
 * 职责：
 *  - 判断能否开始（handoffPending、phase）
 *  - 调用 MeditationService.start / startSimulation
 *  - 转发 pause / resume / finish 指令
 *  - 管理 handoffPending 状态
 *  - 通知外部（ViewModel）"会话活跃/不活跃"
 *
 * 不管：
 *  - UI 状态持有（ViewModel 负责）
 *  - 蓝牙扫描（BluetoothCoordinator 负责）
 *  - 数据库（SessionRepository 负责）
 *
 * 线程：所有调用都应在主线程。
 */
class SessionCoordinator(
    private val context: Context,
    private val readState: () -> MainUiState,
    private val onSessionActiveChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {

    private var handoffPending = false

    /** 当前是否处于"会话启动中"（防重复启动）。 */
    val isHandoffPending: Boolean get() = handoffPending

    /**
     * 开始会话。
     * @return true 表示已发起（或已在处理），false 表示被拒绝（状态不对）
     */
    fun start(simulationMode: Boolean): Boolean {
        val state = readState()
        if (handoffPending || state.sessionState.phase != SessionPhase.IDLE) return false

        // 通知外部：会话即将活跃
        onSessionActiveChanged(true)

        if (simulationMode) {
            if (!state.simulationDataAvailable) {
                onError("The saved 10-minute simulation data is unavailable.")
                onSessionActiveChanged(false)
                return false
            }
            handoffPending = true
            runCatching {
                MeditationService.startSimulation(
                    context,
                    state.selectedTrack,
                    state.sessionState.volume,
                )
            }.onFailure { startFailed() }
            return true
        }

        // 真实设备模式
        val address = state.connectionState.connectedDeviceAddress
        if (address == null || state.connectionState.connectionState != ConnectionState.CONNECTED) {
            onError("Connect Muse 2 before starting meditation.")
            onSessionActiveChanged(false)
            return false
        }
        val deviceName = state.connectionState.devices
            .firstOrNull { it.macAddress == address }?.name ?: "Muse 2"
        handoffPending = true
        runCatching {
            MeditationService.start(
                context, address, deviceName,
                state.selectedDurationSeconds, state.selectedTrack,
                state.sessionState.volume,
            )
        }.onFailure { startFailed() }
        return true
    }

    /** 由 ViewModel 在收到 session 状态变化时调用，用于同步 handoffPending。 */
    fun onSessionStateChanged(phase: SessionPhase) {
        when (phase) {
            SessionPhase.RUNNING, SessionPhase.PAUSED -> {
                // 会话真正跑起来了，handoff 完成
                handoffPending = false
            }
            SessionPhase.IDLE, SessionPhase.FINISHED -> {
                handoffPending = false
            }
        }
    }

    fun pause() = MeditationService.command(context, MeditationService.ACTION_PAUSE)
    fun resume() = MeditationService.command(context, MeditationService.ACTION_RESUME)
    fun finish() = MeditationService.command(context, MeditationService.ACTION_FINISH)

    fun setTrack(track: MusicTrack) {
        MeditationService.setTrack(context, track)
    }

    fun setVolume(volume: Float) {
        MeditationService.setVolume(context, volume)
    }

    /** 重置到 IDLE（用户看完 FINISHED 屏幕后点"新会话"）。 */
    fun resetToIdle(plannedSeconds: Int, track: MusicTrack, volume: Float) {
        SessionRuntime.resetToIdle(plannedSeconds, track, volume)
    }

    private fun startFailed() {
        handoffPending = false
        onSessionActiveChanged(false)
        onError("Could not start the session. Please reconnect and try again.")
    }
}