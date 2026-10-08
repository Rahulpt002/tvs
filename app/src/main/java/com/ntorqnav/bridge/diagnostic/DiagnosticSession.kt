package com.ntorqnav.bridge.diagnostic

import kotlinx.serialization.Serializable

/**
 * Phase 2 — Real NTORQ 150 BLE validation data model.
 *
 * This is the on-disk / export schema for a controlled real-device observation session.
 * Everything here is recorded from *naturally exposed* BLE behaviour only. No value is
 * ever inferred or fabricated: fields that were not genuinely observed stay [UNKNOWN] or
 * [ComparisonStatus.NOT_OBSERVED]. Nothing in this model decrypts, defeats, recovers, or
 * bypasses the TVS protection layer, and no static key is extracted.
 */

const val UNKNOWN = "UNKNOWN"

@Serializable
data class DiagnosticDeviceInfo(
    val name: String?,
    val address: String,
    val rssi: Int?,
    /** Service UUIDs seen in the advertisement/scan record (not from GATT discovery). */
    val advertisedServiceUuids: List<String> = emptyList(),
    /** Manufacturer-specific data: companyId (hex) -> payload (hex), as advertised. */
    val manufacturerData: Map<String, String> = emptyMap(),
    /** True only when the expected TVS service UUID was actually present in the advert. */
    val tvsServiceAdvertised: Boolean = false,
)

@Serializable
data class DiagnosticServiceInfo(
    val uuid: String,
    val isPrimary: Boolean,
    /** True only when this UUID equals the expected TVS NTORQ service (primary or alt). */
    val isExpectedTvsService: Boolean = false,
)

@Serializable
data class DiagnosticCharacteristicInfo(
    val serviceUuid: String,
    val uuid: String,
    val properties: List<String>,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean,
    val descriptors: List<String> = emptyList(),
    /** Role tag derived purely from UUID match: WRITE_0x5352 / NOTIFY_0x5354 / OTHER. */
    val role: String = "OTHER",
)

@Serializable
enum class DiagnosticEventType {
    SCAN_RESULT,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    MTU_NEGOTIATED,
    SERVICES_DISCOVERED,
    NOTIFICATION_SUBSCRIBED,
    RECORDING_STARTED,
    RECORDING_STOPPED,
    NOTE,
}

@Serializable
data class DiagnosticEvent(
    val timestampMs: Long,
    val type: DiagnosticEventType,
    /** Connection state at the moment of the event (free-form, e.g. ConnectionState name). */
    val connectionState: String = UNKNOWN,
    val detail: String = "",
)

@Serializable
data class DiagnosticNotification(
    val timestampMs: Long,
    val characteristicUuid: String,
    val length: Int,
    /** Raw bytes as space-separated uppercase hex. Recorded verbatim; never decrypted. */
    val hexBytes: String,
    /** Milliseconds since the previous recorded notification (0 for the first). */
    val deltaMsFromPrev: Long = 0,
)

/**
 * Section 3 — device identity. All fields default to [UNKNOWN]. They are only ever set
 * from information the device *naturally exposes* during a normal session, and never
 * inferred from the mere presence of the service UUID.
 */
@Serializable
data class DeviceIdentity(
    val vehicleType: String = UNKNOWN,
    val firmwareVersion: String = UNKNOWN,
    val clusterModel: String = UNKNOWN,
    val protocolVariant: String = UNKNOWN,
) {
    companion object {
        val UNKNOWN_IDENTITY = DeviceIdentity()
    }
}

@Serializable
enum class BondingState { UNKNOWN, NONE, BONDING, BONDED }

@Serializable
data class DiagnosticSession(
    val sessionId: String,
    val createdAtMs: Long,
    val schemaVersion: Int = SCHEMA_VERSION,
    val device: DiagnosticDeviceInfo? = null,
    val negotiatedMtu: Int? = null,
    val bondingState: BondingState = BondingState.UNKNOWN,
    val identity: DeviceIdentity = DeviceIdentity.UNKNOWN_IDENTITY,
    val services: List<DiagnosticServiceInfo> = emptyList(),
    val characteristics: List<DiagnosticCharacteristicInfo> = emptyList(),
    val events: List<DiagnosticEvent> = emptyList(),
    val notifications: List<DiagnosticNotification> = emptyList(),
    /** Flat list of every recorded timestamp (events + notifications), ascending. */
    val timestamps: List<Long> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
