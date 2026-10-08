package com.ntorqnav.bridge.navigation

import org.junit.Assert.*
import org.junit.Test

class GoogleMapsNotificationParserTest {

    @Test
    fun testParseStandardTurnNotification() {
        val title = "Turn left onto MG Road"
        val text = "250 m · 12 min"
        val subText = "ETA 5:45 PM"

        val update = GoogleMapsNotificationParser.parse(title, text, subText)
        assertNotNull(update)
        assertEquals(Maneuver.LEFT, update!!.maneuver)
        assertEquals(250, update.distanceMeters)
        assertEquals(12, update.etaMinutes)
        assertEquals("MG Road", update.roadName)
    }

    @Test
    fun testParseKilometerDistance() {
        val title = "Continue on Outer Ring Road"
        val text = "1.5 km · 8 min"

        val update = GoogleMapsNotificationParser.parse(title, text, null)
        assertNotNull(update)
        assertEquals(Maneuver.STRAIGHT, update!!.maneuver)
        assertEquals(1500, update.distanceMeters)
        assertEquals(8, update.etaMinutes)
    }

    @Test
    fun testParseUTurnNotification() {
        val title = "Make a U-turn"
        val text = "50 m"

        val update = GoogleMapsNotificationParser.parse(title, text, null)
        assertNotNull(update)
        assertEquals(Maneuver.U_TURN, update!!.maneuver)
        assertEquals(50, update.distanceMeters)
    }

    @Test
    fun testParseRoundaboutNotification() {
        val title = "At the roundabout, take the 2nd exit"
        val text = "100 m · 2 min"

        val update = GoogleMapsNotificationParser.parse(title, text, null)
        assertNotNull(update)
        assertEquals(Maneuver.ROUNDABOUT, update!!.maneuver)
        assertEquals(100, update.distanceMeters)
    }

    @Test
    fun testParseDestinationArrived() {
        val title = "You have arrived at your destination"
        val text = "MG Road"

        val update = GoogleMapsNotificationParser.parse(title, text, null)
        assertNotNull(update)
        assertEquals(Maneuver.DESTINATION, update!!.maneuver)
    }
}
