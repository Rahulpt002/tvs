package com.ntorqnav.bridge.simulator

import com.ntorqnav.bridge.bluetooth.BluetoothTransport
import com.ntorqnav.bridge.logging.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

class FakeBluetoothTransport(
    private val onPacketReceived: ((UUID, ByteArray) -> Unit)? = null
) : BluetoothTransport {

    @Volatile
    override var isConnected: Boolean = true
        private set

    @Volatile
    override var connectedAddress: String? = "00:11:22:33:44:55 (SIMULATED)"
        private set

    private val _notifications = MutableSharedFlow<ByteArray>(replay = 5, extraBufferCapacity = 64)
    override fun observeNotifications(): Flow<ByteArray> = _notifications.asSharedFlow()

    override suspend fun connect(deviceAddress: String): Boolean {
        isConnected = true
        connectedAddress = deviceAddress
        AppLogger.ble("FakeBluetoothTransport connected to $deviceAddress")
        return true
    }

    override suspend fun disconnect() {
        isConnected = false
        connectedAddress = null
        AppLogger.ble("FakeBluetoothTransport disconnected")
    }

    override suspend fun write(characteristic: UUID, data: ByteArray) {
        if (!isConnected) throw IllegalStateException("FakeBluetoothTransport is disconnected")
        AppLogger.protocol("FakeBluetoothTransport write [${data.size}B] to $characteristic")
        onPacketReceived?.invoke(characteristic, data)
    }

    fun emitSimulatedNotification(data: ByteArray) {
        _notifications.tryEmit(data)
    }
}
