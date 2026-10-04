package com.blue.hush.session

import android.content.Context
import com.blue.hush.service.MeditationService
import com.blue.hush.ui.state.MainUiState
import com.choosemuse.libmuse.ConnectionState

class SessionCoordinator(
    private val context: Context,
    private val readState: () -> MainUiState,
    private val onSessionActiveChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {

    private var handoffPending = false

    val isHandoffPending: Boolean get() = handoffPending

    fun start(simulationMode: Boolean): Boolean {
        val state = readState()
        if (handoffPending || state.sessionState.phase != SessionPhase.IDLE) return false

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

    fun onSessionStateChanged(phase: SessionPhase) {
        when (phase) {
            SessionPhase.RUNNING, SessionPhase.PAUSED -> {
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

    fun resetToIdle(plannedSeconds: Int, track: MusicTrack, volume: Float) {
        SessionRuntime.resetToIdle(plannedSeconds, track, volume)
    }

    private fun startFailed() {
        handoffPending = false
        onSessionActiveChanged(false)
        onError("Could not start the session. Please reconnect and try again.")
    }
}