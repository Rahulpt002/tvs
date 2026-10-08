package com.ntorqnav.bridge.bluetooth

import android.content.Context
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.tvs.TvsConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow

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

    var transport: AndroidBleTransport? = null
        private set

    private var reconnectJob: Job? = null
    private var retryAttempt = 0
    private var autoReconnectEnabled = false

    fun selectDevice(device: DiscoveredBluetoothDevice) {
        _selectedDevice.value = device
        AppLogger.ble("Selected device: ${device.displayName} (${device.address})")
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
                        _connectionState.value = ConnectionState.DISCONNECTED
                        handleUnexpectedDisconnection(deviceAddress)
                    }
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
