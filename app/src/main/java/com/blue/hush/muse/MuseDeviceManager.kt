package com.blue.hush.muse

import android.content.Context
import android.os.SystemClock
import com.choosemuse.libmuse.Accelerometer
import com.choosemuse.libmuse.ConnectionState
import com.choosemuse.libmuse.Eeg
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
import com.choosemuse.libmuse.Ppg

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
        val type: MuseDataPacketType,
        val values: List<Double>,
        val receivedAtMillis: Long,
    )

    private val manager = MuseManagerAndroid.getInstance()
    private var connectedMuse: Muse? = null

    private val museListener = DeviceListBridge()
    private val connectionListener = ConnectionBridge()
    private val dataListener = DataBridge()

    init {
        // LibMuse requires the context to be set before any other SDK operation.
        manager.setContext(context.applicationContext)
        manager.setMuseListener(museListener)
    }

    override fun close() {
        disconnect()
        stopScanning()
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

        muse.setPreset(MusePreset.PRESET_50)

        connectedMuse = muse

        val currentState = muse.getConnectionState()
        if (currentState == ConnectionState.CONNECTED) {
            listener.onConnectionStateChanged(device, currentState, currentState)
        } else {
            muse.runAsynchronously()
        }
    }

    fun disconnect() {
        connectedMuse?.disconnect()
        connectedMuse = null
    }

    private fun publishDevices() {
        listener.onDevicesChanged(manager.getMuses().map(::deviceFor))
    }

    private fun deviceFor(muse: Muse): MuseDevice = MuseDevice(
        nativeMuse = muse,
        name = muse.getName(),
        macAddress = muse.getMacAddress(),
    )

    private inner class DeviceListBridge : MuseListener() {
        override fun museListChanged() {
            publishDevices()
        }
    }

    private inner class ConnectionBridge : MuseConnectionListener() {
        override fun receiveMuseConnectionPacket(packet: MuseConnectionPacket, muse: Muse) {
            val current = packet.getCurrentConnectionState()

            listener.onConnectionStateChanged(
                device = deviceFor(muse),
                previous = packet.getPreviousConnectionState(),
                current = current,
            )

            if (current == ConnectionState.DISCONNECTED &&
                connectedMuse?.getMacAddress() == muse.getMacAddress()
            ) {
                connectedMuse = null
            }
        }
    }

    private inner class DataBridge : MuseDataListener() {
        override fun receiveMuseDataPacket(packet: MuseDataPacket, muse: Muse) {
            listener.onDataPacket(
                MusePacket(
                    type = packet.packetType(),
                    values = translateValues(packet),
                    receivedAtMillis = SystemClock.elapsedRealtime(),
                ),
            )
        }

        override fun receiveMuseArtifactPacket(packet: MuseArtifactPacket, muse: Muse) = Unit

        private fun translateValues(packet: MuseDataPacket): List<Double> =
            when (packet.packetType()) {
                MuseDataPacketType.EEG,
                MuseDataPacketType.ALPHA_RELATIVE,
                MuseDataPacketType.THETA_RELATIVE,
                MuseDataPacketType.BETA_RELATIVE,
                MuseDataPacketType.HSI_PRECISION ->
                    listOf(Eeg.EEG1, Eeg.EEG2, Eeg.EEG3, Eeg.EEG4)
                        .map(packet::getEegChannelValue)

                MuseDataPacketType.ACCELEROMETER ->
                    listOf(Accelerometer.X, Accelerometer.Y, Accelerometer.Z)
                        .map(packet::getAccelerometerValue)

                MuseDataPacketType.PPG ->
                    listOf(Ppg.IR, Ppg.RED)
                        .map(packet::getPpgChannelValue)

                else ->
                    packet.values().map { it.toDouble() }
            }
    }


    private companion object {
        val DATA_PACKET_TYPES = listOf(
            MuseDataPacketType.EEG,
            MuseDataPacketType.ALPHA_RELATIVE,
            MuseDataPacketType.BETA_RELATIVE,
            MuseDataPacketType.THETA_RELATIVE,
            MuseDataPacketType.ACCELEROMETER,
            MuseDataPacketType.PPG,
            MuseDataPacketType.IS_PPG_GOOD,
            MuseDataPacketType.IS_HEART_GOOD,
            MuseDataPacketType.IS_GOOD,
            MuseDataPacketType.HSI_PRECISION,
        )
    }
}