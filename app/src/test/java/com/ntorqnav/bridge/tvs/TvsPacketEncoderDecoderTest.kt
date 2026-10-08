package com.ntorqnav.bridge.tvs

import com.ntorqnav.bridge.navigation.Maneuver
import com.ntorqnav.bridge.navigation.NavigationUpdate
import org.junit.Assert.*
import org.junit.Test

class TvsPacketEncoderDecoderTest {

    @Test
    fun testFrameAEncodingAndDecoding() {
        val update = NavigationUpdate(
            maneuver = Maneuver.LEFT,
            distanceMeters = 250,
            roadName = "MG Road",
            etaMinutes = 12,
            timestamp = 1000L,
            instruction = "Turn left onto MG Road",
            totalDistanceMeters = 4500
        )

        val frameA = TvsPacketEncoder.encodeFrameA(update)

        assertEquals("Frame A must be exactly 20 bytes", 20, frameA.size)
        assertEquals("Byte 0 must be 0x5A", TvsConstants.START_BYTE_5A, frameA[0])
        assertEquals("Byte 1 must be 0x4E (DATA_ID_NAVIGATION_CONTROL)", TvsConstants.DATA_ID_NAVIGATION_CONTROL, frameA[1])
        assertEquals("Byte 19 must be 0xFF trailer", TvsConstants.POSTFIX_TRAILER, frameA[19])

        // Decode Frame A
        val decoded = TvsPacketDecoder.decode(frameA)
        assertTrue("Decoded packet should be FrameANavigationControl", decoded is DecodedTvsPacket.FrameANavigationControl)

        val navControl = decoded as DecodedTvsPacket.FrameANavigationControl
        assertEquals("Distance should match", 250, navControl.distanceMeters)
        assertEquals("ETA should match", 12, navControl.etaMinutes)
        assertEquals("Total distance should match", 4500, navControl.totalDistanceMeters)
        assertEquals("Pictogram should match mapped LEFT picto (2)", 2, navControl.pictogramId)
        assertFalse("Navigation should not be stopped", navControl.isStopped)
    }

    @Test
    fun testFrameBEncodingAndDecoding() {
        val text = "MG Road"
        val frameB = TvsPacketEncoder.encodeFrameB(text)

        assertEquals("Frame B must be exactly 20 bytes", 20, frameB.size)
        assertEquals("Byte 0 must be 0x5B", TvsConstants.START_BYTE_5B, frameB[0])
        assertEquals("Byte 1 must be 0x4F (DATA_ID_NAVIGATION_DATA1)", TvsConstants.DATA_ID_NAVIGATION_DATA1, frameB[1])
        assertEquals("Byte 19 must be 0xFF trailer", TvsConstants.POSTFIX_TRAILER, frameB[19])

        // Decode Frame B
        val decoded = TvsPacketDecoder.decode(frameB)
        assertTrue("Decoded packet should be FrameBNavigationText", decoded is DecodedTvsPacket.FrameBNavigationText)

        val navText = decoded as DecodedTvsPacket.FrameBNavigationText
        assertEquals("Decoded text should match", text, navText.text)
    }

    @Test
    fun testDistanceSaturationAt32767() {
        val update = NavigationUpdate(
            maneuver = Maneuver.STRAIGHT,
            distanceMeters = 50000, // > 32767
            roadName = "NH 44",
            etaMinutes = 60,
            timestamp = 1000L
        )

        val frameA = TvsPacketEncoder.encodeFrameA(update)
        assertEquals("Byte 2 must be 0xFF on saturation", 0xFF.toByte(), frameA[2])
        assertEquals("Byte 3 must be 0xFF on saturation", 0xFF.toByte(), frameA[3])
    }

    @Test
    fun testNavigationStoppedFlag() {
        val update = NavigationUpdate(
            maneuver = Maneuver.DESTINATION,
            distanceMeters = 0,
            roadName = null,
            etaMinutes = 0,
            timestamp = 1000L
        )

        val frameA = TvsPacketEncoder.encodeFrameA(update, isNavigationStopped = true)
        val decoded = TvsPacketDecoder.decode(frameA) as DecodedTvsPacket.FrameANavigationControl
        assertTrue("Navigation stopped flag must be true", decoded.isStopped)
    }
}
