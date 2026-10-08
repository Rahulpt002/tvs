package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.bluetooth.DiscoveredGattCharacteristic
import com.ntorqnav.bridge.bluetooth.DiscoveredGattService
import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GattVerifierTest {

    private fun char(uuid: UUID, props: List<String>) = DiscoveredGattCharacteristic(
        uuid = uuid,
        properties = props,
        canRead = props.contains("READ"),
        canWrite = props.contains("WRITE") || props.contains("WRITE_NO_RESP"),
        canNotify = props.contains("NOTIFY") || props.contains("INDICATE"),
        descriptors = if (props.contains("NOTIFY")) listOf(TvsConstants.CCCD_UUID) else emptyList(),
    )

    private fun fullTvsProfile(): List<DiscoveredGattService> = listOf(
        DiscoveredGattService(
            uuid = TvsConstants.SERVICE_UUID_NTORQ,
            isPrimary = true,
            characteristics = listOf(
                char(TvsConstants.CHAR_WRITE_UUID, listOf("WRITE", "WRITE_NO_RESP")),
                char(TvsConstants.CHAR_NOTIFY_READ_UUID, listOf("READ", "NOTIFY")),
            ),
        ),
    )

    @Test
    fun detectsFullExpectedProfile() {
        val result = GattVerifier.verify(fullTvsProfile())
        assertTrue(result.expectedServicePresent)
        assertTrue(result.writeChar5352Present)
        assertTrue(result.notifyChar5354Present)
        assertTrue(result.notifyCharSubscribable)
        assertTrue(result.fullProfileConfirmed)
    }

    @Test
    fun reportsMissingCharacteristicsHonestly() {
        val onlyService = listOf(
            DiscoveredGattService(TvsConstants.SERVICE_UUID_NTORQ, true, emptyList()),
        )
        val result = GattVerifier.verify(onlyService)
        assertTrue(result.expectedServicePresent)
        assertFalse(result.writeChar5352Present)
        assertFalse(result.notifyChar5354Present)
        assertFalse(result.fullProfileConfirmed)
    }

    @Test
    fun tagsCharacteristicRolesFromUuid() {
        assertEquals(GattVerifier.ROLE_WRITE, GattVerifier.roleFor(TvsConstants.CHAR_WRITE_UUID))
        assertEquals(GattVerifier.ROLE_NOTIFY, GattVerifier.roleFor(TvsConstants.CHAR_NOTIFY_READ_UUID))
        assertEquals(GattVerifier.ROLE_OTHER, GattVerifier.roleFor(UUID.randomUUID()))
    }

    @Test
    fun recognisesAlternateService() {
        val alt = listOf(DiscoveredGattService(TvsConstants.SERVICE_UUID_NTORQ_ALT, true, emptyList()))
        val result = GattVerifier.verify(alt)
        assertFalse(result.expectedServicePresent)
        assertTrue(result.alternateServicePresent)
    }

    @Test
    fun unrelatedDeviceConfirmsNothing() {
        val unrelated = listOf(
            DiscoveredGattService(
                uuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), // battery service
                isPrimary = true,
                characteristics = listOf(char(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"), listOf("READ"))),
            ),
        )
        val result = GattVerifier.verify(unrelated)
        assertFalse(result.fullProfileConfirmed)
        assertEquals(GattVerifier.ROLE_OTHER, result.characteristics.single().role)
    }
}
