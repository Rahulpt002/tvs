package com.ntorqnav.bridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.tvs.TvsConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BluetoothScanner(context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var leScanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result == null || result.device == null) return

            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName
            val address = device.address
            val rssi = result.rssi

            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
            val isNtorq = isNtorqCandidate(name, serviceUuids)

            val item = DiscoveredBluetoothDevice(
                name = name,
                address = address,
                rssi = rssi,
                serviceUuids = serviceUuids,
                isNtorqCandidate = isNtorq
            )

            _discoveredDevices.update { list ->
                val existingIndex = list.indexOfFirst { it.address == address }
                if (existingIndex >= 0) {
                    list.toMutableList().apply { set(existingIndex, item) }
                } else {
                    (list + item).sortedWith(
                        compareByDescending<DiscoveredBluetoothDevice> { it.isNtorqCandidate }
                            .thenByDescending { it.rssi }
                    )
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _isScanning.value = false
            AppLogger.error("BLE Scan failed with errorCode: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            AppLogger.error("Bluetooth adapter is null or not enabled")
            return
        }

        leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            AppLogger.error("BluetoothLeScanner is unavailable")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            _discoveredDevices.value = emptyList()
            _isScanning.value = true
            AppLogger.ble("Starting BLE Scan...")
            leScanner?.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            _isScanning.value = false
            AppLogger.error("Permission denied when starting BLE scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            AppLogger.ble("Stopping BLE Scan")
            leScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            AppLogger.error("Error stopping BLE scan", e)
        } finally {
            _isScanning.value = false
        }
    }

    companion object {
        fun isNtorqCandidate(name: String?, serviceUuids: List<UUID>): Boolean {
            val upperName = name?.uppercase() ?: ""
            if (upperName.contains("NTORQ") || upperName.contains("TVS") || upperName.contains("TVSM") || upperName.contains("CONNECT")) {
                return true
            }
            if (serviceUuids.contains(TvsConstants.SERVICE_UUID_NTORQ) || serviceUuids.contains(TvsConstants.SERVICE_UUID_NTORQ_ALT)) {
                return true
            }
            return false
        }
    }
}
