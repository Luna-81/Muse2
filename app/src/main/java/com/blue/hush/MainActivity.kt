@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.blue.hush

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.blue.hush.ui.HushApp
import com.blue.hush.ui.state.MainViewModel
import com.blue.hush.ui.theme.HushTheme



class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val allGranted = viewModel.requiredBluetoothPermissions()
            .all { granted[it] == true || hasPermission(it) }
        if (allGranted) {
            viewModel.startScanning()
        } else {
            viewModel.onBluetoothPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HushTheme {
                val state = viewModel.uiState
                HushApp(
                    sessionState = state.sessionState,
                    history = state.history,
                    activeTab = state.activeTab,
                    selectedDurationSeconds = state.selectedDurationSeconds,
                    selectedTrack = state.selectedTrack,
                    detailSummary = state.detailSummary,
                    detailSamples = state.detailSamples,
                    replayProgress = state.replayProgress,
                    connectionState = state.connectionState,
                    previewTrack = state.previewTrack,

                    onTabSelected = viewModel::onTabSelected,
                    onDurationSelected = viewModel::onDurationSelected,
                    onTrackSelected = viewModel::onTrackSelected,
                    onStartScanning = ::ensureBluetoothThenScan,
                    onConnect = viewModel::connectMuse,
                    onDisconnect = viewModel::disconnectMuse,
                    onStartSession = { viewModel.startSession(state.connectionState.simulationMode) },
                    onSimulationModeChanged = viewModel::onSimulationModeChanged,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onFinish = viewModel::finish,
                    onStartNewSession = viewModel::startNewSession,
                    onVolumeChanged = viewModel::onVolumeChanged,
                    onOpenDetail = viewModel::openDetail,
                    onCloseDetail = viewModel::onCloseDetail,
                    onReplayProgressChanged = viewModel::onReplayProgressChanged,
                    onPreviewTrack = viewModel::onPreviewTrack,
                    onStopPreview = viewModel::onStopPreview,
                    onDeleteSession = viewModel::deleteSession,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }

    private fun ensureBluetoothThenScan() {
        if (!viewModel.hasBluetoothPermission()) {
            permissionLauncher.launch(viewModel.requiredRequestPermissions())
            return
        }
        if (!viewModel.bluetoothEnabled()) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        viewModel.startScanning()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}