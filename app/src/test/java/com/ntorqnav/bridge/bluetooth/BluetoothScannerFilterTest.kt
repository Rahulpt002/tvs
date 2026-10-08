package com.ntorqnav.bridge.bluetooth

import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BluetoothScannerFilterTest {

    @Test
    fun prioritisesByAdvertisedTvsServiceUuid() {
        assertTrue(BluetoothScanner.isNtorqCandidate(null, listOf(TvsConstants.SERVICE_UUID_NTORQ)))
        assertTrue(BluetoothScanner.isNtorqCandidate(null, listOf(TvsConstants.SERVICE_UUID_NTORQ_ALT)))
    }

    @Test
    fun matchesKnownNamePrefixesCaseInsensitively() {
        assertTrue(BluetoothScanner.isNtorqCandidate("NTORQ_1234", emptyList()))
        assertTrue(BluetoothScanner.isNtorqCandidate("tvsm-device", emptyList()))
        assertTrue(BluetoothScanner.isNtorqCandidate("My TVS Connect", emptyList()))
    }

    @Test
    fun doesNotHardcodeASingleDeviceName() {
        // A TVS-service advertiser with an unexpected/absent name must still be a candidate,
        // so device-name assumptions never gate discovery.
        assertTrue(BluetoothScanner.isNtorqCandidate("WeirdUnbrandedName", listOf(TvsConstants.SERVICE_UUID_NTORQ)))
    }

    @Test
    fun unrelatedDeviceIsNotCandidate() {
        val battery = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        assertFalse(BluetoothScanner.isNtorqCandidate("Mi Band", listOf(battery)))
        assertFalse(BluetoothScanner.isNtorqCandidate(null, emptyList()))
    }
}
