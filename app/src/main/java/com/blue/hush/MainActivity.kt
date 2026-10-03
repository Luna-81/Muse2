@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.blue.hush

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.blue.hush.audio.AmbientAudioEngine
import com.blue.hush.muse.MuseConnectionRuntime
import com.blue.hush.muse.MuseDeviceManager
import com.blue.hush.muse.AutoConnectPolicy
import com.blue.hush.replay.MuseReplaySource
import com.blue.hush.service.MeditationService
import com.blue.hush.session.MusicTrack
import com.blue.hush.session.SessionDuration
import com.blue.hush.session.SessionPhase
import com.blue.hush.session.SessionRuntime
import com.blue.hush.session.SessionSummary
import com.blue.hush.session.StateSample
import com.blue.hush.storage.HushDatabase
import com.blue.hush.ui.*
import com.blue.hush.ui.theme.HushTheme
import com.choosemuse.libmuse.ConnectionState
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    companion object {
        private var automaticConnectionPaused = false
        private var bundledHistoryRestored = false
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private lateinit var database: HushDatabase
    private var sessionState by mutableStateOf(SessionRuntime.current)
    private var history by mutableStateOf<List<SessionSummary>>(emptyList())
    private var activeTab by mutableStateOf(AppTab.MEDITATE)
    private var selectedDurationSeconds by mutableIntStateOf(SessionDuration.DEFAULT_SECONDS)
    private var selectedTrack by mutableStateOf(MusicTrack.RAIN)
    private var detailSummary by mutableStateOf<SessionSummary?>(null)
    private var detailSamples by mutableStateOf<List<StateSample>>(emptyList())
    private var replayProgress by mutableFloatStateOf(0f)
    private var connectionStateUi by mutableStateOf(ConnectionUiState())
    private var simulationDataAvailable by mutableStateOf(false)
    private var museManager: MuseDeviceManager? = null
    private val previewEngine by lazy { AmbientAudioEngine(applicationContext) }
    private var previewTrack by mutableStateOf<MusicTrack?>(null)
    private var removeSessionListener: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (requiredBluetoothPermissions().all { granted[it] == true || hasPermission(it) }) {
            syncDiscovery()
        } else {
            connectionStateUi = connectionStateUi.copy(
                hasBluetoothPermission = false,
                errorMessage = "Bluetooth permission is required to scan for Muse 2. Allow it in Settings and try again.",
            )
        }
    }

    private var idleListener: MuseDeviceManager.Listener? = null
    private var managerGeneration = 0
    private fun museListener(generation: Int) = object : MuseDeviceManager.Listener {
        override fun onDevicesChanged(devices: List<MuseDeviceManager.MuseDevice>) {
            mainHandler.post {
                if (generation != managerGeneration || museManager == null || handoffPending) return@post
                connectionStateUi = connectionStateUi.copy(devices = devices.distinctBy { it.macAddress })
                if (canDiscover() && connectionStateUi.isScanning && !selectionPending) {
                    selectionPending = true
                    mainHandler.postDelayed(selectDevice, 1500L)
                }
            }
        }

        override fun onConnectionStateChanged(
            device: MuseDeviceManager.MuseDevice,
            previous: ConnectionState,
            current: ConnectionState,
        ) {
            mainHandler.post {
                if (generation != managerGeneration || museManager == null || (connectionStateUi.connectedDeviceAddress != null && connectionStateUi.connectedDeviceAddress != device.macAddress)) return@post
                mainHandler.removeCallbacks(connectionTimeout)
                connectionStateUi = connectionStateUi.copy(
                    connectionState = current,
                    connectedDeviceAddress = if (current == ConnectionState.DISCONNECTED) null else device.macAddress,
                    isScanning = false,
                    errorMessage = null,
                )
                if (handoffPending || sessionState.phase == SessionPhase.RUNNING || sessionState.phase == SessionPhase.PAUSED) return@post
                when (current) {
                    ConnectionState.CONNECTED -> {
                        museManager?.stopScanning()
                        retries = 0
                        preferences.edit().putString("last_device", device.macAddress).apply()
                    }
                    ConnectionState.CONNECTING -> mainHandler.postDelayed(connectionTimeout, 20_000L)
                    ConnectionState.NEEDS_UPDATE, ConnectionState.NEEDS_LICENSE -> {
                        closeIdleManager()
                        automaticConnectionPaused = true
                        connectionStateUi = connectionStateUi.copy(
                            automaticConnectionPaused = true,
                            errorMessage = if (current == ConnectionState.NEEDS_UPDATE)
                                "Muse requires a firmware update before connecting."
                            else "Muse requires a valid SDK license before connecting.",
                        )
                    }
                    else -> {
                        closeIdleManager()
                        scheduleRetry()
                    }
                }
            }
        }

        override fun onDataPacket(packet: MuseDeviceManager.MusePacket) = Unit

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeTab = savedInstanceState?.getString("tab")?.let { runCatching { AppTab.valueOf(it) }.getOrNull() } ?: AppTab.MEDITATE
        selectedDurationSeconds = (savedInstanceState?.getInt("duration", SessionDuration.DEFAULT_SECONDS)
            ?: SessionDuration.DEFAULT_SECONDS).coerceIn(SessionDuration.MIN_MINUTES * 60, SessionDuration.MAX_MINUTES * 60)
        selectedTrack = savedInstanceState?.getString("track")?.let { runCatching { MusicTrack.valueOf(it) }.getOrNull() }
            ?: MusicTrack.RAIN
        connectionStateUi = connectionStateUi.copy(simulationMode = savedInstanceState?.getBoolean("simulation") ?: false)
        database = HushDatabase(applicationContext)
        // Queue before history reads; activity recreation must not undo a deletion in this process.
        ioExecutor.execute {
            synchronized(Companion) {
                if (!bundledHistoryRestored) {
                    runCatching { database.restoreBundledHistory(applicationContext) }
                        .onSuccess { bundledHistoryRestored = true }
                        .onFailure { android.util.Log.e("Hush", "Could not restore bundled history", it) }
                }
            }
        }
        ContextCompat.registerReceiver(this, bluetoothReceiver, android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        removeSessionListener = SessionRuntime.subscribe { state ->
            mainHandler.post {
                if (isDestroyed) return@post
                sessionState = state
                if (state.phase == SessionPhase.IDLE && state.message != null) {
                    handoffPending = false
                    connectionStateUi = connectionStateUi.copy(errorMessage = state.message)
                }
                if (state.phase == SessionPhase.FINISHED) {
                    handoffPending = false
                    refreshHistory()
                }
                syncDiscovery()
            }
        }
        refreshSimulationData()
        setContent {
            HushTheme {
                HushApp(
                    sessionState = sessionState,
                    history = history,
                    activeTab = activeTab,
                    selectedDurationSeconds = selectedDurationSeconds,
                    selectedTrack = selectedTrack,
                    detailSummary = detailSummary,
                    detailSamples = detailSamples,
                    replayProgress = replayProgress,
                    connectionState = connectionStateUi,
                    previewTrack = previewTrack,
                    onTabSelected = { activeTab = it; syncDiscovery() },
                    onDurationSelected = { selectedDurationSeconds = it },
                    onTrackSelected = {
                        selectedTrack = it
                        previewTrack = null
                        previewEngine.stop()
                        if (sessionState.phase in listOf(SessionPhase.RUNNING, SessionPhase.PAUSED)) {
                            MeditationService.setTrack(this, it)
                        }
                    },
                    onStartScanning = ::startScanning,
                    onConnect = ::connectMuse,
                    onDisconnect = ::disconnectMuse,
                    onStartSession = { startSession(connectionStateUi.simulationMode) },
                    onSimulationModeChanged = { enabled ->
                        connectionStateUi = connectionStateUi.copy(
                            simulationMode = enabled,
                            simulationDataAvailable = simulationDataAvailable,
                            errorMessage = null,
                        )
                        if (enabled) {
                            selectedDurationSeconds = MuseReplaySource.DURATION_SECONDS
                        }
                        syncDiscovery()
                    },
                    onPause = { MeditationService.command(this, MeditationService.ACTION_PAUSE) },
                    onResume = { MeditationService.command(this, MeditationService.ACTION_RESUME) },
                    onFinish = { MeditationService.command(this, MeditationService.ACTION_FINISH) },
                    onStartNewSession = ::resetCompletedSession,
                    onVolumeChanged = {
                        MeditationService.setVolume(this, it)
                        sessionState = sessionState.copy(volume = it)
                    },
                    onOpenDetail = ::openDetail,
                    onDeleteSession = ::deleteSession,
                    onCloseDetail = { detailSummary = null; syncDiscovery() },
                    onReplayProgressChanged = { replayProgress = it },
                    onPreviewTrack = ::togglePreview,
                    onStopPreview = ::stopPreview,
                )
            }
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tab", activeTab.name)
        outState.putInt("duration", selectedDurationSeconds)
        outState.putString("track", selectedTrack.name)
        outState.putBoolean("simulation", connectionStateUi.simulationMode)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        removeSessionListener?.invoke()
        mainHandler.removeCallbacksAndMessages(null)
        previewEngine.stop()
        idleListener?.let(MuseConnectionRuntime::detach)
        idleListener = null
        museManager = null
        unregisterReceiver(bluetoothReceiver)
        ioExecutor.execute { database.close() }
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private var foreground = false
    private var handoffPending = false
    private var retries = 0
    private val preferences by lazy { getSharedPreferences("muse_preferences", MODE_PRIVATE) }
    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) { syncDiscovery() }
    }
    private var retryPending = false
    private val retryDiscovery = Runnable { retryPending = false; syncDiscovery() }
    private var selectionPending = false
    private val selectDevice = Runnable {
        selectionPending = false
        if (canDiscover() && connectionStateUi.isScanning) {
            val address = AutoConnectPolicy.choose(connectionStateUi.devices.map { it.macAddress }, preferences.getString("last_device", null))
            connectionStateUi.devices.firstOrNull { it.macAddress == address }?.let(::connectMuse)
        }
    }
    private val connectionTimeout = Runnable {
        if (connectionStateUi.connectionState == ConnectionState.CONNECTING) {
            closeIdleManager()
            connectionStateUi = connectionStateUi.copy(errorMessage = "Connection timed out. Retrying…")
            scheduleRetry()
        }
    }
    override fun onStart() { super.onStart(); foreground = true; syncDiscovery() }
    override fun onStop() {
        foreground = false
        stopPreview()
        syncDiscovery()
        super.onStop()
    }
    private fun bluetoothEnabled(): Boolean = runCatching {
        getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)
    private fun canDiscover() = AutoConnectPolicy.eligible(
        foreground,
        sessionState.phase != SessionPhase.RUNNING && sessionState.phase != SessionPhase.PAUSED && !handoffPending,
        hasBluetoothPermission(), bluetoothEnabled(), connectionStateUi.simulationMode, automaticConnectionPaused)
    private fun syncDiscovery() {
        connectionStateUi = connectionStateUi.copy(hasBluetoothPermission = hasBluetoothPermission(),
            bluetoothEnabled = bluetoothEnabled(), automaticConnectionPaused = automaticConnectionPaused)
        if (handoffPending || sessionState.phase == SessionPhase.RUNNING || sessionState.phase == SessionPhase.PAUSED) {
            // The session service controls reconnection scanning while it is active.
            cancelDiscoveryTasks()
            return
        }
        if (!canDiscover()) {
            cancelDiscoveryTasks()
            if (museManager != null) runCatching { museManager?.stopScanning() }
            connectionStateUi = connectionStateUi.copy(isScanning = false)
            if (!connectionStateUi.hasBluetoothPermission || !connectionStateUi.bluetoothEnabled) closeIdleManager()
            else if (connectionStateUi.connectionState == ConnectionState.CONNECTING) mainHandler.postDelayed(connectionTimeout, 20_000L)
            return
        }
        if (museManager == null) {
            runCatching { initializeMuseManager() }.onFailure {
                connectionStateUi = connectionStateUi.copy(errorMessage = "Could not initialize Muse. Check Bluetooth.")
                scheduleRetry()
                return
            }
        }
        if (connectionStateUi.connectionState != ConnectionState.DISCONNECTED || connectionStateUi.isScanning || retryPending) return
        runCatching {
            connectionStateUi = connectionStateUi.copy(isScanning = true, errorMessage = null)
            museManager?.startScanning()
        }.onFailure {
            connectionStateUi = connectionStateUi.copy(isScanning = false, errorMessage = "Could not search for Muse. Check Bluetooth.")
            scheduleRetry()
        }
    }
    private fun scheduleRetry() {
        if (!canDiscover()) return
        mainHandler.removeCallbacks(retryDiscovery)
        retryPending = true
        mainHandler.postDelayed(retryDiscovery, AutoConnectPolicy.retryDelay(retries++))
    }
    private fun cancelDiscoveryTasks() {
        retryPending = false
        selectionPending = false
        mainHandler.removeCallbacks(retryDiscovery)
        mainHandler.removeCallbacks(selectDevice)
        mainHandler.removeCallbacks(connectionTimeout)
    }
    private fun closeIdleManager() {
        val manager = museManager
        managerGeneration++
        museManager = null
        idleListener?.let(MuseConnectionRuntime::detach)
        idleListener = null
        if (manager != null) runCatching { MuseConnectionRuntime.disconnect() }
        connectionStateUi = connectionStateUi.copy(connectionState = ConnectionState.DISCONNECTED,
            connectedDeviceAddress = null, isScanning = false, devices = emptyList())
    }
    private fun disconnectMuse() {
        automaticConnectionPaused = true
        cancelDiscoveryTasks()
        closeIdleManager()
        syncDiscovery()
    }
    private fun stopPreview() { previewTrack = null; previewEngine.stop() }

    private fun requestBluetoothPermission() {
        permissionLauncher.launch(requiredRequestPermissions())
    }

    private fun initializeMuseManager() {
        if (museManager != null) return
        val listener = museListener(++managerGeneration)
        idleListener = listener
        museManager = MuseConnectionRuntime.attach(applicationContext, listener)
        connectionStateUi = connectionStateUi.copy(
            hasBluetoothPermission = true,
            connectionState = MuseConnectionRuntime.connectionState,
            connectedDeviceAddress = MuseConnectionRuntime.device?.macAddress,
            errorMessage = null,
        )
    }

    private fun startScanning() {
        automaticConnectionPaused = false
        if (!hasBluetoothPermission()) { requestBluetoothPermission(); return }
        if (!bluetoothEnabled()) {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            return
        }
        syncDiscovery()
    }

    private fun connectMuse(device: MuseDeviceManager.MuseDevice) {
        automaticConnectionPaused = false
        if (!canDiscover() || connectionStateUi.connectionState == ConnectionState.CONNECTING) return
        cancelDiscoveryTasks()
        initializeMuseManager()
        connectionStateUi = connectionStateUi.copy(connectionState = ConnectionState.CONNECTING,
            connectedDeviceAddress = device.macAddress, isScanning = false, errorMessage = null)
        runCatching { museManager?.connect(device) }.onFailure {
            closeIdleManager()
            connectionStateUi = connectionStateUi.copy(connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceAddress = null, errorMessage = "Could not connect. Retrying…")
            scheduleRetry()
        }.onSuccess { mainHandler.postDelayed(connectionTimeout, 20_000L) }
    }

    private fun startSession(simulationMode: Boolean) {
        if (handoffPending || sessionState.phase != SessionPhase.IDLE) return
        stopPreview()
        cancelDiscoveryTasks()
        if (simulationMode) {
            if (!simulationDataAvailable) {
                connectionStateUi = connectionStateUi.copy(errorMessage = "The saved 10-minute simulation data is unavailable.")
                return
            }
            handoffPending = true
            runCatching { MeditationService.startSimulation(this, selectedTrack, sessionState.volume) }
                .onFailure { sessionStartFailed() }
            return
        }
        val address = connectionStateUi.connectedDeviceAddress
        if (address == null || connectionStateUi.connectionState != ConnectionState.CONNECTED) {
            connectionStateUi = connectionStateUi.copy(errorMessage = "Connect Muse 2 before starting meditation.")
            return
        }
        val deviceName = connectionStateUi.devices.firstOrNull { it.macAddress == address }?.name ?: "Muse 2"
        // The service subscribes to the same native connection without reconnecting.
        handoffPending = true
        museManager?.stopScanning()
        runCatching { MeditationService.start(this, address, deviceName, selectedDurationSeconds, selectedTrack, sessionState.volume) }
            .onFailure { sessionStartFailed() }
    }

    private fun sessionStartFailed() {
        handoffPending = false
        connectionStateUi = connectionStateUi.copy(errorMessage = "Could not start the session. Please reconnect and try again.")
        syncDiscovery()
    }

    private fun resetCompletedSession() {
        activeTab = AppTab.MEDITATE
        SessionRuntime.resetToIdle(
            plannedSeconds = selectedDurationSeconds,
            track = selectedTrack,
            volume = sessionState.volume,
        )
    }

    private fun openDetail(summary: SessionSummary) {
        ioExecutor.execute {
            val samples = database.loadSamples(summary.id)
            mainHandler.post {
                if (isDestroyed) return@post
                detailSummary = summary
                detailSamples = samples
                replayProgress = 0f
                syncDiscovery()
            }
        }
    }

    private fun deleteSession(summary: SessionSummary) {
        ioExecutor.execute {
            val result = runCatching {
                database.deleteSession(summary.id)
                database.loadSummaries()
            }
            mainHandler.post {
                if (isDestroyed) return@post
                result.onSuccess { history = it }.onFailure {
                    android.widget.Toast.makeText(this, "Could not delete session. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshHistory() {
        ioExecutor.execute {
            val summaries = database.loadSummaries()
            mainHandler.post { if (!isDestroyed) history = summaries }
        }
    }

    private fun refreshSimulationData() {
        ioExecutor.execute {
            val samples = MuseReplaySource.load(applicationContext)
            val available = MuseReplaySource.isUsable(samples)
            val summaries = database.loadSummaries()
            mainHandler.post {
                if (isDestroyed) return@post
                simulationDataAvailable = available
                connectionStateUi = connectionStateUi.copy(simulationDataAvailable = available)
                history = summaries
            }
        }
    }

    private fun togglePreview(track: MusicTrack) {
        if (previewTrack == track) {
            previewTrack = null
            previewEngine.stop()
        } else {
            previewTrack = track
            previewEngine.play(track)
        }
    }

    private fun hasBluetoothPermission(): Boolean = requiredBluetoothPermissions().all(::hasPermission)

    private fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requiredBluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun requiredRequestPermissions(): Array<String> = buildList {
        addAll(requiredBluetoothPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}
