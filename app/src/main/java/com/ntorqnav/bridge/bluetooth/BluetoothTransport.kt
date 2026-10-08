package com.ntorqnav.bridge.bluetooth

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface BluetoothTransport {
    val isConnected: Boolean
    val connectedAddress: String?

    suspend fun connect(deviceAddress: String): Boolean

    suspend fun disconnect()

    suspend fun write(characteristic: UUID, data: ByteArray)

    fun observeNotifications(): Flow<ByteArray>
}
