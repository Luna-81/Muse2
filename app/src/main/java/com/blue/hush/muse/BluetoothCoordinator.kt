package com.blue.hush.muse

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.blue.hush.ui.ConnectionUiState
import com.choosemuse.libmuse.ConnectionState
import androidx.core.content.edit


class BluetoothCoordinator(
    private val context: Context,
    private val readState: () -> ConnectionUiState,
    private val onState: (ConnectionUiState) -> Unit,
) {


    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences: SharedPreferences =
        context.getSharedPreferences("muse_preferences", Context.MODE_PRIVATE)

    private var museManager: MuseDeviceManager? = null
    private var idleListener: MuseDeviceManager.Listener? = null
    private var managerGeneration = 0



    private var foreground = false
    private var sessionActive = false
    private var retries = 0
    private var retryPending = false
    private var selectionPending = false
    private var automaticPaused = false



    private var hasPermission: () -> Boolean = { false }
    private var bluetoothEnabledFn: () -> Boolean = { false }



    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }



    private val retryDiscovery = Runnable {
        retryPending = false
        refresh()
    }

    private val selectDevice = Runnable {
        selectionPending = false
        val s = readState()
        if (canDiscover() && s.isScanning) {
            val address = AutoConnectPolicy.choose(
                s.devices.map { it.macAddress },
                preferences.getString("last_device", null),
            )
            s.devices.firstOrNull { it.macAddress == address }?.let(::connect)
        }
    }

    private val connectionTimeout = Runnable {
        if (readState().connectionState == ConnectionState.CONNECTING) {
            closeIdleManager()
            pushError("Connection timed out. Retrying…")
            scheduleRetry()
        }
    }


    init {
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun updatePrerequisites(
        hasPermission: () -> Boolean,
        bluetoothEnabled: () -> Boolean,
    ) {
        this.hasPermission = hasPermission
        this.bluetoothEnabledFn = bluetoothEnabled
    }

    fun onForeground() {
        foreground = true
        refresh()
    }

    fun onBackground() {
        foreground = false
        refresh()
    }

    fun setSessionActive(active: Boolean) {
        sessionActive = active
        refresh()
    }

    fun startScanning() {
        automaticPaused = false
        if (!hasPermission()) return
        if (!bluetoothEnabledFn()) return
        refresh()
    }

    fun connect(device: MuseDeviceManager.MuseDevice) {
        automaticPaused = false
        val s = readState()
        if (!canDiscover() || s.connectionState == ConnectionState.CONNECTING) return

        cancelDiscoveryTasks()
        initializeMuseManager()

        onState(
            readState().copy(
                connectionState = ConnectionState.CONNECTING,
                connectedDeviceAddress = device.macAddress,
                isScanning = false,
                errorMessage = null,
            ),
        )

        runCatching { museManager?.connect(device) }
            .onFailure {
                closeIdleManager()
                onState(
                    readState().copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        connectedDeviceAddress = null,
                        errorMessage = "Could not connect. Retrying…",
                    ),
                )
                scheduleRetry()
            }
            .onSuccess {
                mainHandler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
            }
    }

    fun disconnect() {
        automaticPaused = true
        cancelDiscoveryTasks()
        closeIdleManager()
        refresh()
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        idleListener?.let(MuseConnectionRuntime::detach)
        idleListener = null
        museManager = null
        runCatching { context.unregisterReceiver(bluetoothReceiver) }
    }

    fun refresh() = syncDiscovery()

    private fun syncDiscovery() {
        syncBaseState()
        if (sessionActive) { cancelDiscoveryTasks(); return }
        if (!canDiscover()) { handleCannotDiscover(); return }
        if (!ensureManager()) return
        if (isAlreadyActive()) return
        beginScanning()
    }

    private fun syncBaseState() {
        val s = readState()
        onState(
            s.copy(
                hasBluetoothPermission = hasPermission(),
                bluetoothEnabled = bluetoothEnabledFn(),
                automaticConnectionPaused = automaticPaused,
            ),
        )
    }

    private fun handleCannotDiscover() {
        cancelDiscoveryTasks()
        if (museManager != null) runCatching { museManager?.stopScanning() }
        onState(readState().copy(isScanning = false))

        val s = readState()
        if (!s.hasBluetoothPermission || !s.bluetoothEnabled) {
            closeIdleManager()
        } else if (s.connectionState == ConnectionState.CONNECTING) {
            mainHandler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
        }
    }

    private fun ensureManager(): Boolean {
        if (museManager != null) return true
        return runCatching { initializeMuseManager() }
            .onFailure {
                pushError("Could not initialize Muse. Check Bluetooth.")
                scheduleRetry()
            }
            .isSuccess
    }

    private fun isAlreadyActive(): Boolean {
        val s = readState()
        return s.connectionState != ConnectionState.DISCONNECTED ||
                s.isScanning || retryPending
    }

    private fun beginScanning() {
        runCatching {
            onState(readState().copy(isScanning = true, errorMessage = null))
            museManager?.startScanning()
        }.onFailure {
            onState(
                readState().copy(
                    isScanning = false,
                    errorMessage = "Could not search for Muse. Check Bluetooth.",
                ),
            )
            scheduleRetry()
        }
    }

    private fun canDiscover(): Boolean {
        val s = readState()
        return AutoConnectPolicy.eligible(
            foreground,
            !sessionActive,
            hasPermission(),
            bluetoothEnabledFn(),
            s.simulationMode,
            automaticPaused,
        )
    }


    private fun scheduleRetry() {
        if (!canDiscover()) return
        mainHandler.removeCallbacks(retryDiscovery)
        retryPending = true
        mainHandler.postDelayed(retryDiscovery, AutoConnectPolicy.retryDelay(retries++))
    }

    private fun pushError(message: String) {
        onState(readState().copy(errorMessage = message))
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
        onState(
            readState().copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceAddress = null,
                isScanning = false,
                devices = emptyList(),
            ),
        )
    }

    private fun initializeMuseManager() {
        if (museManager != null) return
        val bridge = DeviceEventBridge(++managerGeneration)
        idleListener = bridge
        museManager = MuseConnectionRuntime.attach(context, bridge)
        onState(
            readState().copy(
                hasBluetoothPermission = true,
                connectionState = MuseConnectionRuntime.connectionState,
                connectedDeviceAddress = MuseConnectionRuntime.device?.macAddress,
                errorMessage = null,
            ),
        )
    }


    private inner class DeviceEventBridge(
        private val generation: Int,
    ) : MuseDeviceManager.Listener {

        override fun onDevicesChanged(devices: List<MuseDeviceManager.MuseDevice>) {
            mainHandler.post {
                if (generation != managerGeneration || museManager == null || sessionActive) {
                    return@post
                }
                onState(readState().copy(devices = devices.distinctBy { it.macAddress }))

                if (canDiscover() && readState().isScanning && !selectionPending) {
                    selectionPending = true
                    mainHandler.postDelayed(selectDevice, SELECTION_DELAY_MILLIS)
                }
            }
        }

        override fun onConnectionStateChanged(
            device: MuseDeviceManager.MuseDevice,
            previous: ConnectionState,
            current: ConnectionState,
        ) {
            mainHandler.post {
                if (!isValidCallback(device)) return@post
                mainHandler.removeCallbacks(connectionTimeout)
                updateConnectionState(device, current)
                if (sessionActive) return@post
                handleStateTransition(device, current)
            }
        }

        private fun isValidCallback(device: MuseDeviceManager.MuseDevice): Boolean {
            val s = readState()
            if (generation != managerGeneration || museManager == null) return false
            if (s.connectedDeviceAddress != null && s.connectedDeviceAddress != device.macAddress) return false
            return true
        }

        private fun updateConnectionState(
            device: MuseDeviceManager.MuseDevice,
            current: ConnectionState,
        ) {
            onState(
                readState().copy(
                    connectionState = current,
                    connectedDeviceAddress =
                        if (current == ConnectionState.DISCONNECTED) null else device.macAddress,
                    isScanning = false,
                    errorMessage = null,
                ),
            )
        }

        private fun handleStateTransition(
            device: MuseDeviceManager.MuseDevice,
            current: ConnectionState,
        ) {
            when (current) {
                ConnectionState.CONNECTED -> handleConnected(device)
                ConnectionState.CONNECTING -> handleConnecting()
                ConnectionState.NEEDS_UPDATE,
                ConnectionState.NEEDS_LICENSE -> handleNeedsUpdateOrLicense(current)
                else -> handleDisconnected()
            }
        }

        private fun handleConnected(device: MuseDeviceManager.MuseDevice) {
            museManager?.stopScanning()
            retries = 0
            preferences.edit { putString("last_device", device.macAddress) }
        }

        private fun handleConnecting() {
            mainHandler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
        }

        private fun handleNeedsUpdateOrLicense(current: ConnectionState) {
            closeIdleManager()
            automaticPaused = true
            onState(
                readState().copy(
                    automaticConnectionPaused = true,
                    errorMessage = if (current == ConnectionState.NEEDS_UPDATE) {
                        "Muse requires a firmware update before connecting."
                    } else {
                        "Muse requires a valid SDK license before connecting."
                    },
                ),
            )
        }

        private fun handleDisconnected() {
            closeIdleManager()
            scheduleRetry()
        }

        override fun onDataPacket(packet: MuseDeviceManager.MusePacket) = Unit
    }


    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 20_000L
        const val SELECTION_DELAY_MILLIS = 1_500L
    }
}