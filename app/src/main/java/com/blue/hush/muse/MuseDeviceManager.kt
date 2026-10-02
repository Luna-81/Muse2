package com.blue.hush.muse

import android.content.Context
import android.os.SystemClock
import com.choosemuse.libmuse.Accelerometer
import com.choosemuse.libmuse.Eeg
import com.choosemuse.libmuse.Ppg
import com.choosemuse.libmuse.ConnectionState
import com.choosemuse.libmuse.Muse
import com.choosemuse.libmuse.MuseArtifactPacket
import com.choosemuse.libmuse.MuseConnectionListener
import com.choosemuse.libmuse.MuseConnectionPacket
import com.choosemuse.libmuse.MuseDataListener
import com.choosemuse.libmuse.MuseDataPacket
import com.choosemuse.libmuse.MuseDataPacketType
import com.choosemuse.libmuse.MuseListener
import com.choosemuse.libmuse.MuseManagerAndroid
import com.choosemuse.libmuse.MusePreset

/**
 * Small application-facing adapter around LibMuse's Android manager.
 *
 * LibMuse invokes callbacks from its own worker threads. Consumers should move
 * callbacks to the UI thread when updating Compose state or views.
 */
class MuseDeviceManager(
    context: Context,
    private val listener: Listener,
) : AutoCloseable {

    interface Listener {
        fun onDevicesChanged(devices: List<MuseDevice>)

        fun onConnectionStateChanged(
            device: MuseDevice,
            previous: ConnectionState,
            current: ConnectionState,
        )

        fun onDataPacket(packet: MusePacket)

    }

    data class MuseDevice(
        internal val nativeMuse: Muse,
        val name: String,
        val macAddress: String,
    )

    data class MusePacket(
        val device: MuseDevice,
        val type: MuseDataPacketType,
        val values: List<Double>,
        val timestamp: Long,
        val receivedAtMillis: Long,
    )

    private val manager = MuseManagerAndroid.getInstance()
    private var connectedMuse: Muse? = null

    private val museListener = object : MuseListener() {
        override fun museListChanged() {
            publishDevices()
        }
    }

    private val connectionListener = object : MuseConnectionListener() {
        override fun receiveMuseConnectionPacket(packet: MuseConnectionPacket, muse: Muse) {
            val device = deviceFor(muse)
            listener.onConnectionStateChanged(
                device = device,
                previous = packet.getPreviousConnectionState(),
                current = packet.getCurrentConnectionState(),
            )
            if (packet.getCurrentConnectionState() == ConnectionState.DISCONNECTED &&
                connectedMuse?.getMacAddress() == muse.getMacAddress()
            ) {
                connectedMuse = null
            }
        }
    }

    private val dataListener = object : MuseDataListener() {
        override fun receiveMuseDataPacket(packet: MuseDataPacket, muse: Muse) {
            listener.onDataPacket(
                MusePacket(
                    device = deviceFor(muse),
                    type = packet.packetType(),
                    values = when (packet.packetType()) {
                        MuseDataPacketType.EEG,
                        MuseDataPacketType.ALPHA_RELATIVE,
                        MuseDataPacketType.THETA_RELATIVE,
                        MuseDataPacketType.BETA_RELATIVE,
                        MuseDataPacketType.HSI_PRECISION -> listOf(Eeg.EEG1, Eeg.EEG2, Eeg.EEG3, Eeg.EEG4).map(packet::getEegChannelValue)
                        MuseDataPacketType.ACCELEROMETER -> listOf(Accelerometer.X, Accelerometer.Y, Accelerometer.Z).map(packet::getAccelerometerValue)
                        MuseDataPacketType.PPG -> listOf(Ppg.IR, Ppg.RED).map(packet::getPpgChannelValue)
                        else -> packet.values().map { it.toDouble() }
                    },
                    timestamp = packet.timestamp(),
                    receivedAtMillis = SystemClock.elapsedRealtime(),
                ),
            )
        }

        override fun receiveMuseArtifactPacket(packet: MuseArtifactPacket, muse: Muse) = Unit
    }

    init {
        // LibMuse requires the context to be set before any other SDK operation.
        manager.setContext(context.applicationContext)
        manager.setMuseListener(museListener)
    }

    fun startScanning() {
        manager.stopListening()
        manager.startListening()
        publishDevices()
    }

    fun stopScanning() {
        manager.stopListening()
    }

    fun connect(device: MuseDevice) {
        manager.stopListening()
        connectedMuse?.disconnect()

        val muse = device.nativeMuse
        muse.unregisterAllListeners()
        muse.registerConnectionListener(connectionListener)
        DATA_PACKET_TYPES.forEach { type ->
            muse.registerDataListener(dataListener, type)
        }
        // Muse 2's p50 preset enables the sensor streams used by the app.
        // The p21 EEG-only preset can connect successfully without emitting
        // the derived bands needed to produce a valid meditation sample.
        muse.setPreset(MusePreset.PRESET_50)
        connectedMuse = muse
        val currentState = muse.getConnectionState()
        if (currentState == ConnectionState.CONNECTED) {
            // Reuse an already-running native instance without restarting it.
            listener.onConnectionStateChanged(device, currentState, currentState)
        } else {
            muse.runAsynchronously()
        }
    }

    fun disconnect() {
        connectedMuse?.disconnect()
        connectedMuse = null
    }

    override fun close() {
        disconnect()
        stopScanning()
    }

    private fun publishDevices() {
        listener.onDevicesChanged(manager.getMuses().map(::deviceFor))
    }

    private fun deviceFor(muse: Muse): MuseDevice = MuseDevice(
        nativeMuse = muse,
        name = muse.getName(),
        macAddress = muse.getMacAddress(),
    )

    private companion object {
        val DATA_PACKET_TYPES = listOf(
            MuseDataPacketType.EEG,
            MuseDataPacketType.GYRO,
            MuseDataPacketType.ALPHA_RELATIVE,
            MuseDataPacketType.BETA_RELATIVE,
            MuseDataPacketType.DELTA_RELATIVE,
            MuseDataPacketType.THETA_RELATIVE,
            MuseDataPacketType.GAMMA_RELATIVE,
            MuseDataPacketType.ALPHA_ABSOLUTE,
            MuseDataPacketType.BETA_ABSOLUTE,
            MuseDataPacketType.DELTA_ABSOLUTE,
            MuseDataPacketType.THETA_ABSOLUTE,
            MuseDataPacketType.GAMMA_ABSOLUTE,
            MuseDataPacketType.ALPHA_SCORE,
            MuseDataPacketType.BETA_SCORE,
            MuseDataPacketType.DELTA_SCORE,
            MuseDataPacketType.THETA_SCORE,
            MuseDataPacketType.GAMMA_SCORE,
            MuseDataPacketType.ACCELEROMETER,
            MuseDataPacketType.PPG,
            MuseDataPacketType.IS_PPG_GOOD,
            MuseDataPacketType.IS_HEART_GOOD,
            MuseDataPacketType.IS_GOOD,
            MuseDataPacketType.HSI,
            MuseDataPacketType.HSI_PRECISION,
            MuseDataPacketType.BATTERY,
        )
    }
}
