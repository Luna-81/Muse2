@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.blue.hush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blue.hush.muse.MuseDeviceManager
import com.blue.hush.session.*
import com.blue.hush.ui.components.HushNavIcon
import com.blue.hush.ui.components.PrimaryAction
import com.blue.hush.ui.components.SoundscapeSheet
import com.blue.hush.ui.galaxy.GalaxyParticleField
import com.blue.hush.ui.galaxy.rememberGalaxyMotion
import com.blue.hush.ui.screens.CompletionScreen
import com.blue.hush.ui.screens.HistoryScreen
import com.blue.hush.ui.screens.MeditationGalaxyScreen
import com.blue.hush.ui.screens.MusicButton
import com.blue.hush.ui.screens.SessionDetailScreen
import com.blue.hush.ui.screens.SessionPreparationPanel
import com.blue.hush.ui.theme.*
import com.choosemuse.libmuse.ConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab(val title: String) { MEDITATE("Home"), HISTORY("History") }

data class ConnectionUiState(
    val hasBluetoothPermission: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val isScanning: Boolean = false,
    val devices: List<MuseDeviceManager.MuseDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectedDeviceAddress: String? = null,
    val errorMessage: String? = null,
    val simulationMode: Boolean = false,
    val simulationDataAvailable: Boolean = false,
    val automaticConnectionPaused: Boolean = false,
) {
    val ready get() = if (simulationMode) simulationDataAvailable else connectionState == ConnectionState.CONNECTED
    val status get() = when {
        simulationMode -> if (simulationDataAvailable) "Simulation \u00B7 10 min" else "Simulation unavailable"
        !hasBluetoothPermission -> "Bluetooth permission needed"
        !bluetoothEnabled -> "Turn on Bluetooth"
        connectionState == ConnectionState.CONNECTED -> "Muse connected"
        connectionState == ConnectionState.CONNECTING -> "Connecting\u2026"
        automaticConnectionPaused -> "Connection paused"
        isScanning -> "Searching for Muse\u2026"
        else -> "Connect your Muse"
    }
}

@Composable
internal fun Page(title: String, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (onBack != null) BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
            navigationIcon = {
                if (onBack != null) IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Back" },
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        val stroke = 2.dp.toPx()
                        drawLine(HushColors.Text, Offset(size.width * 0.85f, center.y), Offset(size.width * 0.15f, center.y), stroke, StrokeCap.Round)
                        drawLine(HushColors.Text, Offset(size.width * 0.45f, size.height * 0.2f), Offset(size.width * 0.15f, center.y), stroke, StrokeCap.Round)
                        drawLine(HushColors.Text, Offset(size.width * 0.45f, size.height * 0.8f), Offset(size.width * 0.15f, center.y), stroke, StrokeCap.Round)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = HushColors.Background),
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) { content() }
    }
}

@Composable
fun HushApp(
    sessionState: SessionState, history: List<SessionSummary>, activeTab: AppTab,
    selectedDurationSeconds: Int, selectedTrack: MusicTrack, detailSummary: SessionSummary?,
    detailSamples: List<StateSample>, replayProgress: Float, connectionState: ConnectionUiState,
    previewTrack: MusicTrack?, onTabSelected: (AppTab) -> Unit, onDurationSelected: (Int) -> Unit,
    onTrackSelected: (MusicTrack) -> Unit, onStartScanning: () -> Unit,
    onConnect: (MuseDeviceManager.MuseDevice) -> Unit, onDisconnect: () -> Unit,
    onStartSession: () -> Unit, onSimulationModeChanged: (Boolean) -> Unit,
    onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit, onStartNewSession: () -> Unit,
    onVolumeChanged: (Float) -> Unit, onOpenDetail: (SessionSummary) -> Unit,
    onCloseDetail: () -> Unit, onReplayProgressChanged: (Float) -> Unit,
    onPreviewTrack: (MusicTrack) -> Unit, onStopPreview: () -> Unit,
    onDeleteSession: (SessionSummary) -> Unit,
) {
    var deviceSheet by rememberSaveable { mutableStateOf(false) }
    var musicSheet by rememberSaveable { mutableStateOf(false) }
    // Kept above route returns so details and configuration changes preserve dismissal.
    var resultsSheet by rememberSaveable(sessionState.sessionId) { mutableStateOf(true) }
    val galaxyMotion = rememberGalaxyMotion()

    if (sessionState.phase in listOf(SessionPhase.RUNNING, SessionPhase.PAUSED)) {
        MeditationGalaxyScreen(sessionState, onPause, onResume, onFinish, onVolumeChanged, galaxyMotion, onTrackSelected)
        return
    }
    if (detailSummary != null) {
        SessionDetailScreen(detailSummary, detailSamples, replayProgress, onCloseDetail, onReplayProgressChanged)
        return
    }
    if (sessionState.phase == SessionPhase.FINISHED) {
        val summary = history.firstOrNull { it.id == sessionState.sessionId }
        CompletionScreen(
            sessionState, galaxyMotion, resultsSheet,
            onShowResults = { resultsSheet = true },
            onDismissResults = { resultsSheet = false },
            onBack = onStartNewSession,
            detailAvailable = summary != null,
            onDetails = { summary?.let { resultsSheet = false; onOpenDetail(it) } },
        )
        return
    }

    Scaffold(
        containerColor = HushColors.Background,
        bottomBar = {
            NavigationBar(containerColor = HushColors.Background) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { HushNavIcon(tab) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (activeTab == AppTab.MEDITATE) {
                LazyColumn(
                    Modifier.widthIn(max = HushSpace.contentWidth).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = HushSpace.lg, vertical = HushSpace.sm),
                    verticalArrangement = Arrangement.spacedBy(HushSpace.sm),
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Hush", style = MaterialTheme.typography.headlineLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                               // ChartHelpButton()
                                MusicButton(selectedTrack.title, onClick = { musicSheet = true })
                            }
                        }
                    }
                    item {
                        GalaxyParticleField(
                            null, dataGap = false, paused = false,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).aspectRatio(1f),
                            state = galaxyMotion, preview = true,
                        )
                    }
                    item { Text("A moment of stillness", style = MaterialTheme.typography.titleMedium) }
                    item {
                        SessionPreparationPanel(
                            selectedDurationSeconds, connectionState,
                            onDurationSelected,
                            onDeviceSelected = { deviceSheet = true },
                            onStart = {
                                if (connectionState.ready) onStartSession() else { deviceSheet = true; onStartScanning() }
                            },
                        )
                    }
                }
            } else {
                HistoryScreen(history, onOpenDetail, onDeleteSession)
            }
        }
    }

    if (deviceSheet) ModalBottomSheet(onDismissRequest = { deviceSheet = false }, containerColor = HushColors.Surface) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(HushSpace.xl),
            verticalArrangement = Arrangement.spacedBy(HushSpace.lg),
        ) {
            item {
                Text("Your Muse", style = MaterialTheme.typography.headlineMedium)
                Text(connectionState.status, color = HushColors.Muted)
            }
            connectionState.errorMessage?.let { message ->
                item { Text(message, color = HushColors.Error) }
            }
            if (!connectionState.simulationMode) {
                if (!connectionState.ready) item {
                    PrimaryAction(
                        if (connectionState.hasBluetoothPermission) "Connect Muse" else "Allow Bluetooth",
                        onStartScanning,
                    )
                }
                items(connectionState.devices, key = { it.macAddress }) { device ->
                    OutlinedButton(
                        onClick = { onConnect(device) },
                        enabled = connectionState.connectionState != ConnectionState.CONNECTING &&
                                connectionState.connectedDeviceAddress != device.macAddress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(device.name.ifBlank { "Muse 2" })
                            Text(device.macAddress.takeLast(5), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (connectionState.ready) item {
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                }
            }
            item { HorizontalDivider(color = HushColors.Border) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Try a simulation")
                        Text(
                            "Saved session \u00B7 10 min",
                            style = MaterialTheme.typography.bodySmall,
                            color = HushColors.Muted,
                        )
                    }
                    Switch(
                        modifier = Modifier.semantics { contentDescription = "Use saved simulation data" },
                        checked = connectionState.simulationMode,
                        onCheckedChange = onSimulationModeChanged,
                        enabled = connectionState.simulationDataAvailable,
                    )
                }
            }
        }
    }

    if (musicSheet) {
        DisposableEffect(Unit) { onDispose { onStopPreview() } }
        SoundscapeSheet(
            selectedTrack, onTrackSelected,
            onDismiss = { musicSheet = false },
            previewTrack = previewTrack,
            onPreviewTrack = onPreviewTrack,
        )
    }
}

internal fun formatDuration(seconds: Int): String =
    String.format(Locale.US, "%02d:%02d", seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

internal fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(Date(timestamp))

internal fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(timestamp))