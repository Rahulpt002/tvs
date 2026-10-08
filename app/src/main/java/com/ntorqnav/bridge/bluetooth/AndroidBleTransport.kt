package com.ntorqnav.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.tvs.TvsConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class AndroidBleTransport(
    private val context: Context,
    private val onServicesDiscovered: ((List<DiscoveredGattService>) -> Unit)? = null,
    private val onConnectionStateChanged: ((Boolean) -> Unit)? = null
) : BluetoothTransport {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private val writeMutex = Mutex()
    private var writeCompletion = CompletableDeferred<Boolean>()

    private val _notificationsFlow = MutableSharedFlow<ByteArray>(replay = 10, extraBufferCapacity = 64)
    override fun observeNotifications(): Flow<ByteArray> = _notificationsFlow.asSharedFlow()

    @Volatile
    override var isConnected: Boolean = false
        private set

    @Volatile
    override var connectedAddress: String? = null
        private set

    private var connectionDeferred = CompletableDeferred<Boolean>()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(g, status, newState)
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                connectedAddress = g.device.address
                AppLogger.ble("GATT Connected to ${g.device.address}. Requesting MTU 512 & discovering services...")
                onConnectionStateChanged?.invoke(true)
                g.requestMtu(512)
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                connectedAddress = null
                AppLogger.ble("GATT Disconnected with status $status")
                onConnectionStateChanged?.invoke(false)
                if (connectionDeferred.isActive) {
                    connectionDeferred.complete(false)
                }
                closeGatt()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(g, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                AppLogger.ble("GATT Services Discovered: ${g.services.size} services found")
                val parsedServices = g.services.map { s ->
                    DiscoveredGattService(
                        uuid = s.uuid,
                        isPrimary = s.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY,
                        characteristics = s.characteristics.map { c ->
                            val props = mutableListOf<String>()
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.add("READ")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.add("WRITE")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) props.add("WRITE_NO_RESP")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.add("NOTIFY")
                            if ((c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.add("INDICATE")

                            DiscoveredGattCharacteristic(
                                uuid = c.uuid,
                                properties = props,
                                canRead = props.contains("READ"),
                                canWrite = props.contains("WRITE") || props.contains("WRITE_NO_RESP"),
                                canNotify = props.contains("NOTIFY") || props.contains("INDICATE"),
                                descriptors = c.descriptors.map { it.uuid }
                            )
                        }
                    )
                }

                onServicesDiscovered?.invoke(parsedServices)

                // Subscribe to NTORQ notification characteristic (00005354)
                val notifyChar = g.getService(TvsConstants.SERVICE_UUID_NTORQ)
                    ?.getCharacteristic(TvsConstants.CHAR_NOTIFY_READ_UUID)
                    ?: g.services.flatMap { it.characteristics }.find { it.uuid == TvsConstants.CHAR_NOTIFY_READ_UUID }

                if (notifyChar != null) {
                    enableNotification(g, notifyChar)
                } else {
                    AppLogger.ble("Notice: NTORQ Notification characteristic ${TvsConstants.CHAR_NOTIFY_READ_UUID} not found yet")
                }

                if (connectionDeferred.isActive) {
                    connectionDeferred.complete(true)
                }
            } else {
                AppLogger.error("GATT service discovery failed with status $status")
                if (connectionDeferred.isActive) {
                    connectionDeferred.complete(false)
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun enableNotification(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val success = g.setCharacteristicNotification(characteristic, true)
            AppLogger.ble("Setting characteristic notification on ${characteristic.uuid}: success=$success")
            val descriptor = characteristic.getDescriptor(TvsConstants.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(descriptor)
                AppLogger.ble("Wrote ENABLE_NOTIFICATION to CCCD descriptor")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(g, characteristic)
            val data = characteristic.value ?: return
            AppLogger.protocol("RX Notification from ${characteristic.uuid} [${data.size}B]")
            _notificationsFlow.tryEmit(data)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(g, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeCompletion.complete(true)
            } else {
                AppLogger.error("Characteristic write failed with status $status")
                writeCompletion.complete(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String): Boolean {
        disconnect()
        val adapter = bluetoothAdapter ?: return false
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            AppLogger.error("Invalid Bluetooth address: $deviceAddress", e)
            return false
        }

        connectionDeferred = CompletableDeferred()
        AppLogger.ble("Connecting to $deviceAddress (TRANSPORT_LE)...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        val result = withTimeoutOrNull(15000L) {
            connectionDeferred.await()
        } ?: false

        if (!result) {
            AppLogger.error("Connection attempt timed out for $deviceAddress")
            disconnect()
        }
        return result
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        try {
            gatt?.disconnect()
            closeGatt()
        } catch (e: Exception) {
            AppLogger.error("Error during disconnect", e)
        } finally {
            isConnected = false
            connectedAddress = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            gatt = null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(characteristic: UUID, data: ByteArray) {
        val currentGatt = gatt ?: throw IllegalStateException("Not connected")
        if (!TvsConstants.ALLOWED_WRITE_CHARACTERISTICS.contains(characteristic)) {
            throw SecurityException("Characteristic $characteristic is not in safety whitelist")
        }

        val charObj = currentGatt.services.flatMap { it.characteristics }.find { it.uuid == characteristic }
            ?: throw IllegalArgumentException("Characteristic $characteristic not found on peripheral")

        writeMutex.withLock {
            writeCompletion = CompletableDeferred()
            charObj.value = data
            val writeType = if ((charObj.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            charObj.writeType = writeType

            val initiated = currentGatt.writeCharacteristic(charObj)
            if (!initiated) {
                throw IllegalStateException("Failed to initiate characteristic write")
            }

            if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                withTimeoutOrNull(2000L) {
                    writeCompletion.await()
                }
            }
        }
    }
}
