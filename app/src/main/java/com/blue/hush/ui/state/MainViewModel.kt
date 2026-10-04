package com.blue.hush.ui.state

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.blue.hush.audio.PreviewController
import com.blue.hush.muse.BluetoothCoordinator
import com.blue.hush.muse.MuseDeviceManager
import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.SessionCoordinator
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionRuntime
import com.blue.hush.session.SessionSummary
import com.blue.hush.storage.SessionRepository
import com.blue.hush.ui.AppTab
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
/**
 * 表现层 ViewModel：
 *  - 持有 MainUiState
 *  - 订阅 SessionRuntime
 *  - 蓝牙编排 → BluetoothCoordinator        （阶段 3）
 *  - 数据读写 → SessionRepository            （阶段 2）
 *  - 会话启动/指令 → SessionCoordinator      （阶段 4）
 *  - 试听 → PreviewController                （阶段 4）
 *  - 权限 → PermissionManager               （阶段 4）
 *
 * MainViewModel 本身只做"状态持有 + 事件转发"。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ---------- 状态 ----------
    var uiState by mutableStateOf(MainUiState())
        private set

    // ---------- 基础设施 ----------
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appContext: Context get() = getApplication()

    // ---------- 各功能模块 ----------
    private val permissions = PermissionManager(application)
    private val repository = SessionRepository(application)

    private val bluetooth = BluetoothCoordinator(
        context = application,
        readState = { uiState.connectionState },
        onState = { newState ->
            uiState = uiState.copy(connectionState = newState)
        },
    )

    private val session = SessionCoordinator(
        context = application,
        readState = { uiState },
        onSessionActiveChanged = { active ->
            bluetooth.setSessionActive(active)
        },
        onError = { message ->
            uiState = uiState.copy(
                connectionState = uiState.connectionState.copy(errorMessage = message),
            )
        },
    )

    private val preview = PreviewController(application)

    private var removeSessionListener: (() -> Unit)? = null

    // ---------- 初始化 ----------
    init {
        repository.restoreBundledHistoryOnce(appContext)

        bluetooth.updatePrerequisites(
            hasPermission = { permissions.hasBluetoothPermission() },
            bluetoothEnabled = { bluetoothEnabled() },
        )

        removeSessionListener = SessionRuntime.subscribe { state ->
            mainHandler.post {
                uiState = uiState.copy(sessionState = state)
                session.onSessionStateChanged(state.phase)

                if (state.phase == SessionPhase.IDLE && state.message != null) {
                    uiState = uiState.copy(
                        connectionState = uiState.connectionState.copy(errorMessage = state.message),
                    )
                }
                if (state.phase == SessionPhase.FINISHED) {
                    refreshHistory()
                }
                bluetooth.refresh()
            }
        }

        refreshSimulationData()
        refreshHistory()
    }

    // ---------- 生命周期转发 ----------
    fun onForeground() {
        bluetooth.onForeground()
    }

    fun onBackground() {
        preview.stop()
        uiState = uiState.copy(previewTrack = null)
        bluetooth.onBackground()
    }

    // ---------- UI 回调 ----------
    fun onTabSelected(tab: AppTab) {
        uiState = uiState.copy(activeTab = tab)
        bluetooth.refresh()
    }

    fun onDurationSelected(seconds: Int) {
        uiState = uiState.copy(selectedDurationSeconds = seconds)
    }

    fun onTrackSelected(track: MusicTrack) {
        uiState = uiState.copy(selectedTrack = track, previewTrack = null)
        preview.stop()
        if (uiState.sessionState.phase in listOf(SessionPhase.RUNNING, SessionPhase.PAUSED)) {
            session.setTrack(track)
        }
    }

    fun onSimulationModeChanged(enabled: Boolean) {
        uiState = uiState.copy(
            connectionState = uiState.connectionState.copy(
                simulationMode = enabled,
                simulationDataAvailable = uiState.simulationDataAvailable,
                errorMessage = null,
            ),
            selectedDurationSeconds = if (enabled) MuseReplaySource.DURATION_SECONDS
            else uiState.selectedDurationSeconds,
        )
        bluetooth.refresh()
    }

    fun onVolumeChanged(volume: Float) {
        session.setVolume(volume)
        uiState = uiState.copy(sessionState = uiState.sessionState.copy(volume = volume))
    }

    fun onReplayProgressChanged(progress: Float) {
        uiState = uiState.copy(replayProgress = progress)
    }

    fun onCloseDetail() {
        uiState = uiState.copy(detailSummary = null)
        bluetooth.refresh()
    }

    fun onPreviewTrack(track: MusicTrack) {
        val next = preview.toggle(track)
        uiState = uiState.copy(previewTrack = next)
    }

    fun onStopPreview() {
        preview.stop()
        uiState = uiState.copy(previewTrack = null)
    }

    fun onBluetoothPermissionDenied() {
        uiState = uiState.copy(
            connectionState = uiState.connectionState.copy(
                hasBluetoothPermission = false,
                errorMessage = "Bluetooth permission is required to scan for Muse 2. Allow it in Settings and try again.",
            ),
        )
    }

    // ---------- 权限（转发给 PermissionManager） ----------
    fun hasBluetoothPermission(): Boolean = permissions.hasBluetoothPermission()

    fun requiredBluetoothPermissions(): Array<String> = permissions.requiredBluetoothPermissions()

    fun requiredRequestPermissions(): Array<String> = permissions.requiredRequestPermissions()

    // ---------- 蓝牙命令（转发给 Coordinator） ----------
    fun startScanning() {
        bluetooth.startScanning()
    }

    fun bluetoothEnabled(): Boolean = runCatching {
        appContext.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    fun connectMuse(device: MuseDeviceManager.MuseDevice) {
        bluetooth.connect(device)
    }

    fun disconnectMuse() {
        bluetooth.disconnect()
    }

    // ---------- 会话（转发给 SessionCoordinator） ----------
    fun startSession(simulationMode: Boolean) {
        preview.stop()
        uiState = uiState.copy(previewTrack = null)
        session.start(simulationMode)
    }

    fun pause() = session.pause()
    fun resume() = session.resume()
    fun finish() = session.finish()

    fun startNewSession() {
        uiState = uiState.copy(activeTab = AppTab.MEDITATE)
        session.resetToIdle(
            plannedSeconds = uiState.selectedDurationSeconds,
            track = uiState.selectedTrack,
            volume = uiState.sessionState.volume,
        )
    }

    // ---------- 历史 / 详情 ----------
    fun openDetail(summary: SessionSummary) {
        repository.loadDetail(summary) { samples ->
            uiState = uiState.copy(
                detailSummary = summary,
                detailSamples = samples,
                replayProgress = 0f,
            )
            bluetooth.refresh()
        }
    }

    fun deleteSession(summary: SessionSummary) {
        repository.delete(summary) { result ->
            result.onSuccess { history ->
                uiState = uiState.copy(history = history)
            }.onFailure {
                Toast.makeText(
                    appContext,
                    "Could not delete session. Please try again.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun refreshHistory() {
        repository.loadHistory { summaries ->
            uiState = uiState.copy(history = summaries)
        }
    }

    private fun refreshSimulationData() {
        repository.loadSimulationData(appContext) { available ->
            uiState = uiState.copy(
                simulationDataAvailable = available,
                connectionState = uiState.connectionState.copy(simulationDataAvailable = available),
            )
        }
        refreshHistory()
    }

    // ---------- 清理 ----------
    override fun onCleared() {
        removeSessionListener?.invoke()
        mainHandler.removeCallbacksAndMessages(null)
        preview.stop()
        bluetooth.shutdown()
        repository.close()
        super.onCleared()
    }
}