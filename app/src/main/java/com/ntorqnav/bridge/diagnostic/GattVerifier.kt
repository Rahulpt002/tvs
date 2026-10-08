package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.bluetooth.DiscoveredGattCharacteristic
import com.ntorqnav.bridge.bluetooth.DiscoveredGattService
import com.ntorqnav.bridge.tvs.TvsConstants
import java.util.UUID

/**
 * Section 2 — GATT verification.
 *
 * Takes the services/characteristics actually discovered over GATT and reports, factually,
 * whether the statically-predicted TVS profile is present. It never writes anything and never
 * assumes the predicted profile exists — every flag here is derived from real discovery data.
 */
data class GattVerificationResult(
    val expectedServicePresent: Boolean,
    val alternateServicePresent: Boolean,
    val writeChar5352Present: Boolean,
    val notifyChar5354Present: Boolean,
    /** Properties of the notify characteristic, if present (empty otherwise). */
    val notifyCharProperties: List<String>,
    /** True only if the notify characteristic genuinely advertises NOTIFY or INDICATE. */
    val notifyCharSubscribable: Boolean,
    val services: List<DiagnosticServiceInfo>,
    val characteristics: List<DiagnosticCharacteristicInfo>,
) {
    /** The whole statically-predicted profile is confirmed present. */
    val fullProfileConfirmed: Boolean
        get() = (expectedServicePresent || alternateServicePresent) &&
            writeChar5352Present && notifyChar5354Present
}

object GattVerifier {

    const val ROLE_WRITE = "WRITE_0x5352"
    const val ROLE_NOTIFY = "NOTIFY_0x5354"
    const val ROLE_OTHER = "OTHER"

    fun roleFor(uuid: UUID): String = when (uuid) {
        TvsConstants.CHAR_WRITE_UUID -> ROLE_WRITE
        TvsConstants.CHAR_NOTIFY_READ_UUID -> ROLE_NOTIFY
        else -> ROLE_OTHER
    }

    fun isExpectedTvsService(uuid: UUID): Boolean =
        uuid == TvsConstants.SERVICE_UUID_NTORQ || uuid == TvsConstants.SERVICE_UUID_NTORQ_ALT

    fun mapServices(services: List<DiscoveredGattService>): List<DiagnosticServiceInfo> =
        services.map {
            DiagnosticServiceInfo(
                uuid = it.uuid.toString(),
                isPrimary = it.isPrimary,
                isExpectedTvsService = isExpectedTvsService(it.uuid),
            )
        }

    fun mapCharacteristics(services: List<DiscoveredGattService>): List<DiagnosticCharacteristicInfo> =
        services.flatMap { service ->
            service.characteristics.map { c -> mapCharacteristic(service.uuid, c) }
        }

    private fun mapCharacteristic(
        serviceUuid: UUID,
        c: DiscoveredGattCharacteristic,
    ): DiagnosticCharacteristicInfo = DiagnosticCharacteristicInfo(
        serviceUuid = serviceUuid.toString(),
        uuid = c.uuid.toString(),
        properties = c.properties,
        canRead = c.canRead,
        canWrite = c.canWrite,
        canNotify = c.canNotify,
        descriptors = c.descriptors.map { it.toString() },
        role = roleFor(c.uuid),
    )

    fun verify(services: List<DiscoveredGattService>): GattVerificationResult {
        val allChars = services.flatMap { s -> s.characteristics.map { s.uuid to it } }

        val expectedServicePresent = services.any { it.uuid == TvsConstants.SERVICE_UUID_NTORQ }
        val alternateServicePresent = services.any { it.uuid == TvsConstants.SERVICE_UUID_NTORQ_ALT }

        val writeChar = allChars.firstOrNull { it.second.uuid == TvsConstants.CHAR_WRITE_UUID }?.second
        val notifyChar = allChars.firstOrNull { it.second.uuid == TvsConstants.CHAR_NOTIFY_READ_UUID }?.second

        return GattVerificationResult(
            expectedServicePresent = expectedServicePresent,
            alternateServicePresent = alternateServicePresent,
            writeChar5352Present = writeChar != null,
            notifyChar5354Present = notifyChar != null,
            notifyCharProperties = notifyChar?.properties ?: emptyList(),
            notifyCharSubscribable = notifyChar?.canNotify == true,
            services = mapServices(services),
            characteristics = mapCharacteristics(services),
        )
    }
}
