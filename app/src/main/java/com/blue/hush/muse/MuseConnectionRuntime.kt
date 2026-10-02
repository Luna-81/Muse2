package com.blue.hush.muse

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.choosemuse.libmuse.ConnectionState
import java.util.concurrent.CopyOnWriteArraySet

/** Keeps the native connection alive independently of screens and session lifetime. */
object MuseConnectionRuntime : MuseDeviceManager.Listener {
    private val listeners = CopyOnWriteArraySet<MuseDeviceManager.Listener>()
    private val handler = Handler(Looper.getMainLooper())
    private var manager: MuseDeviceManager? = null
    @Volatile private var generation = 0
    @Volatile private var devices: List<MuseDeviceManager.MuseDevice> = emptyList()
    @Volatile private var connectedDevice: MuseDeviceManager.MuseDevice? = null
    @Volatile var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    val device: MuseDeviceManager.MuseDevice? get() = connectedDevice

    fun attach(context: Context, listener: MuseDeviceManager.Listener): MuseDeviceManager {
        val shared = manager ?: createManager(context).also { manager = it }
        listeners += listener
        // Restore connection state first so discovery consumers do not reconnect it.
        connectedDevice?.let { listener.onConnectionStateChanged(it, connectionState, connectionState) }
        listener.onDevicesChanged(devices)
        return shared
    }

    fun detach(listener: MuseDeviceManager.Listener) {
        listeners -= listener
    }

    fun disconnect() {
        generation++
        manager?.close()
        manager = null
        connectedDevice = null
        connectionState = ConnectionState.DISCONNECTED
        devices = emptyList()
    }

    private fun createManager(context: Context): MuseDeviceManager {
        val ownerGeneration = ++generation
        return MuseDeviceManager(context.applicationContext, object : MuseDeviceManager.Listener {
            override fun onDevicesChanged(devices: List<MuseDeviceManager.MuseDevice>) {
                handler.post {
                    if (ownerGeneration == generation) this@MuseConnectionRuntime.onDevicesChanged(devices)
                }
            }

            override fun onConnectionStateChanged(
                device: MuseDeviceManager.MuseDevice,
                previous: ConnectionState,
                current: ConnectionState,
            ) {
                handler.post {
                    if (ownerGeneration == generation) this@MuseConnectionRuntime.onConnectionStateChanged(device, previous, current)
                }
            }

            override fun onDataPacket(packet: MuseDeviceManager.MusePacket) {
                if (ownerGeneration == generation) this@MuseConnectionRuntime.onDataPacket(packet)
            }
        })
    }

    override fun onDevicesChanged(devices: List<MuseDeviceManager.MuseDevice>) {
        this.devices = devices
        listeners.forEach { it.onDevicesChanged(devices) }
    }

    override fun onConnectionStateChanged(
        device: MuseDeviceManager.MuseDevice,
        previous: ConnectionState,
        current: ConnectionState,
    ) {
        connectedDevice = if (current == ConnectionState.DISCONNECTED) null else device
        connectionState = current
        listeners.forEach { it.onConnectionStateChanged(device, previous, current) }
    }

    override fun onDataPacket(packet: MuseDeviceManager.MusePacket) {
        listeners.forEach { it.onDataPacket(packet) }
    }
}
