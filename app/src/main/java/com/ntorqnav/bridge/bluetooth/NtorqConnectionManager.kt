package com.ntorqnav.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.tvs.TvsConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

/** A raw BLE notification observed on a characteristic (passive; never decrypted). */
data class RawNotification(val characteristicUuid: UUID, val bytes: ByteArray, val atMs: Long) {
    override fun equals(other: Any?): Boolean = this === other ||
        (other is RawNotification && characteristicUuid == other.characteristicUuid &&
            bytes.contentEquals(other.bytes) && atMs == other.atMs)

    override fun hashCode(): Int = (characteristicUuid.hashCode() * 31 + bytes.contentHashCode()) * 31 + atMs.hashCode()
}

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READY,
    ERROR
}

class NtorqConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    val scanner = BluetoothScanner(context)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DiscoveredBluetoothDevice?>(null)
    val selectedDevice: StateFlow<DiscoveredBluetoothDevice?> = _selectedDevice.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<DiscoveredGattService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredGattService>> = _discoveredServices.asStateFlow()

    private val _isTvsProtocolValidated = MutableStateFlow(false)
    val isTvsProtocolValidated: StateFlow<Boolean> = _isTvsProtocolValidated.asStateFlow()

    private val _negotiatedMtu = MutableStateFlow<Int?>(null)
    val negotiatedMtu: StateFlow<Int?> = _negotiatedMtu.asStateFlow()

    /** Raw observed notifications, surfaced for the diagnostic recorder. */
    private val _rawNotifications = MutableSharedFlow<RawNotification>(replay = 0, extraBufferCapacity = 256)
    val rawNotifications: SharedFlow<RawNotification> = _rawNotifications.asSharedFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    var transport: AndroidBleTransport? = null
        private set

    private var reconnectJob: Job? = null
    private var retryAttempt = 0
    private var autoReconnectEnabled = false

    fun selectDevice(device: DiscoveredBluetoothDevice) {
        _selectedDevice.value = device
        AppLogger.ble("Selected device: ${device.displayName} (${device.address})")
    }

    /**
     * Reads the current Android bond state for a device address, mapped to a stable label.
     * "UNKNOWN" when the adapter or device is unavailable. Read-only; never initiates bonding.
     */
    @SuppressLint("MissingPermission")
    fun readBondState(deviceAddress: String? = _selectedDevice.value?.address): String {
        val adapter = bluetoothManager?.adapter ?: return "UNKNOWN"
        val address = deviceAddress ?: return "UNKNOWN"
        return try {
            when (adapter.getRemoteDevice(address).bondState) {
                BluetoothDevice.BOND_NONE -> "NONE"
                BluetoothDevice.BOND_BONDING -> "BONDING"
                BluetoothDevice.BOND_BONDED -> "BONDED"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    fun startScanning() {
        _connectionState.value = ConnectionState.SCANNING
        scanner.startScan()
    }

    fun stopScanning() {
        scanner.stopScan()
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun connect(deviceAddress: String? = _selectedDevice.value?.address) {
        if (deviceAddress == null) {
            AppLogger.error("Cannot connect: no device selected")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        stopScanning()
        reconnectJob?.cancel()
        autoReconnectEnabled = true

        scope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            AppLogger.ble("Initiating connection to $deviceAddress (attempt ${retryAttempt + 1})")

            val bleTransport = AndroidBleTransport(
                context = context,
                onServicesDiscovered = { services ->
                    _discoveredServices.value = services
                    validateTvsGattProfile(services)
                },
                onConnectionStateChanged = { connected ->
                    if (connected) {
                        retryAttempt = 0
                        _connectionState.value = ConnectionState.CONNECTED
                    } else {
                        _isTvsProtocolValidated.value = false
                        _discoveredServices.value = emptyList()
                        _negotiatedMtu.value = null
                        _connectionState.value = ConnectionState.DISCONNECTED
                        handleUnexpectedDisconnection(deviceAddress)
                    }
                },
                onMtuChanged = { mtu -> _negotiatedMtu.value = mtu },
                onNotification = { uuid, bytes ->
                    _rawNotifications.tryEmit(RawNotification(uuid, bytes, System.currentTimeMillis()))
                }
            )
            transport = bleTransport

            val ok = bleTransport.connect(deviceAddress)
            if (!ok) {
                _connectionState.value = ConnectionState.ERROR
                handleUnexpectedDisconnection(deviceAddress)
            }
        }
    }

    fun disconnect() {
        autoReconnectEnabled = false
        reconnectJob?.cancel()
        reconnectJob = null
        retryAttempt = 0

        scope.launch {
            AppLogger.ble("Disconnecting explicitly from NTORQ")
            transport?.disconnect()
            transport = null
            _connectionState.value = ConnectionState.DISCONNECTED
            _isTvsProtocolValidated.value = false
            _discoveredServices.value = emptyList()
        }
    }

    private fun validateTvsGattProfile(services: List<DiscoveredGattService>) {
        val hasNtorqService = services.any { it.uuid == TvsConstants.SERVICE_UUID_NTORQ || it.uuid == TvsConstants.SERVICE_UUID_NTORQ_ALT }
        val characteristics = services.flatMap { it.characteristics }
        val hasWriteChar = characteristics.any { it.uuid == TvsConstants.CHAR_WRITE_UUID }
        val hasNotifyChar = characteristics.any { it.uuid == TvsConstants.CHAR_NOTIFY_READ_UUID }

        if (hasNtorqService && hasWriteChar && hasNotifyChar) {
            AppLogger.ble("TVM NTORQ GATT Profile VALIDATED! Ready for navigation data.")
            _isTvsProtocolValidated.value = true
            _connectionState.value = ConnectionState.READY
        } else {
            AppLogger.ble("GATT Profile check: hasService=$hasNtorqService, hasWrite=$hasWriteChar, hasNotify=$hasNotifyChar")
            _isTvsProtocolValidated.value = false
            _connectionState.value = ConnectionState.CONNECTED
        }
    }

    private fun handleUnexpectedDisconnection(deviceAddress: String) {
        if (!autoReconnectEnabled) return

        retryAttempt++
        // Exponential backoff: 2s, 4s, 8s, 16s, max 30s
        val delaySec = min(30.0, 2.0.pow(retryAttempt.coerceAtMost(5))).toLong()
        AppLogger.ble("Disconnected. Reconnecting with exponential backoff in ${delaySec}s (attempt $retryAttempt)...")

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delaySec * 1000L)
            if (autoReconnectEnabled) {
                connect(deviceAddress)
            }
        }
    }
}
