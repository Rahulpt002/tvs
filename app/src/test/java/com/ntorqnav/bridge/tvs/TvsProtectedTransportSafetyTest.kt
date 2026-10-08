package com.ntorqnav.bridge.tvs

import com.ntorqnav.bridge.navigation.Maneuver
import com.ntorqnav.bridge.navigation.NavigationUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TvsProtectedTransportSafetyTest {

    @Test
    fun testSafetyInterlockBlocksPhysicalTransmissionByDefault() = runBlocking {
        val transport = DiagnosticSafeProtectedTransport(underlyingTransport = null, allowPhysicalTransmission = false)
        assertTrue("Safety interlock must be engaged by default", transport.isSafetyInterlockEngaged)

        val update = NavigationUpdate(
            maneuver = Maneuver.RIGHT,
            distanceMeters = 100,
            roadName = "Beach Road",
            etaMinutes = 5,
            timestamp = 1000L
        )
        val (frameA, frameB) = TvsPacketEncoder.encodeNavigationFrames(update)

        val result = transport.transmitNavigationFrames(frameA, frameB)
        assertTrue("Result must be BlockedBySafetyInterlock", result is ProtectedTransmissionResult.BlockedBySafetyInterlock)

        val blocked = result as ProtectedTransmissionResult.BlockedBySafetyInterlock
        assertTrue("Reason must mention safety interlock", blocked.reason.contains("Safety Interlock Engaged"))
        assertTrue("Diagnostic dump must include hex data", blocked.diagnosticDump.contains("Frame A"))
    }
}
