package com.ntorqnav.bridge.simulator

import com.ntorqnav.bridge.navigation.Maneuver
import com.ntorqnav.bridge.navigation.NavigationUpdate
import com.ntorqnav.bridge.tvs.TvsConstants
import com.ntorqnav.bridge.tvs.TvsPacketEncoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TvsProtocolSimulatorTest {

    @Test
    fun testSimulatorReceivesAndDecodesFrames() = runBlocking {
        val simulator = TvsProtocolSimulator()

        val update = NavigationUpdate(
            maneuver = Maneuver.LEFT,
            distanceMeters = 300,
            roadName = "100ft Road",
            etaMinutes = 14,
            timestamp = 2000L,
            instruction = "Turn left onto 100ft Road",
            totalDistanceMeters = 6000
        )

        val (frameA, frameB) = TvsPacketEncoder.encodeNavigationFrames(update)

        // Simulate Central writing Frame B (text) then Frame A (nav control) to 00005352
        simulator.processIncomingCentralPacket(TvsConstants.CHAR_WRITE_UUID, frameB)
        simulator.processIncomingCentralPacket(TvsConstants.CHAR_WRITE_UUID, frameA)

        val state = simulator.displayState.value
        assertEquals("TURN LEFT", state.maneuverName)
        assertEquals(300, state.distanceMeters)
        assertEquals(14, state.etaMinutes)
        assertEquals(6000, state.totalDistanceMeters)
        assertEquals("100ft Road", state.roadName)
        assertTrue(state.isNavigationActive)
        assertEquals(2, state.rxPacketCount)
    }

    @Test
    fun testSimulatorSpeedometerNotification() = runBlocking {
        val simulator = TvsProtocolSimulator()
        var receivedNotification: ByteArray? = null

        val job = launch {
            simulator.fakeTransport.observeNotifications().collect {
                receivedNotification = it
            }
        }

        simulator.emitMockSpeedometerFrame(speedKmph = 55, fuelPercent = 80, odoKm = 2100)
        delay(100)

        assertNotNull(receivedNotification)
        assertEquals(20, receivedNotification!!.size)
        assertEquals(TvsConstants.START_BYTE_5A, receivedNotification!![0])
        assertEquals(TvsConstants.DATA_ID_SPEEDOMETER_1, receivedNotification!![1])
        assertEquals(55.toByte(), receivedNotification!![2])

        job.cancel()
    }
}
