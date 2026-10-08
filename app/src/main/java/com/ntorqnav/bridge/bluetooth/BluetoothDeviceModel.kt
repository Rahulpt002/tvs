package com.ntorqnav.bridge.bluetooth

import java.util.UUID

data class DiscoveredBluetoothDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<UUID> = emptyList(),
    val isNtorqCandidate: Boolean = false,
    /** True only when the expected TVS service UUID is actually in the advertisement. */
    val advertisesTvsService: Boolean = false,
    /** Manufacturer-specific advertising data: companyId (hex) -> payload (hex). */
    val manufacturerData: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = name?.ifBlank { null } ?: "Unknown ($address)"
}

data class DiscoveredGattCharacteristic(
    val uuid: UUID,
    val properties: List<String>,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean,
    val descriptors: List<UUID> = emptyList()
)

data class DiscoveredGattService(
    val uuid: UUID,
    val isPrimary: Boolean,
    val characteristics: List<DiscoveredGattCharacteristic>
)
