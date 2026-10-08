package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameClassifierTest {

    private fun frameA(): ByteArray = ByteArray(20).apply {
        this[0] = TvsConstants.START_BYTE_5A
        this[1] = TvsConstants.DATA_ID_NAVIGATION_CONTROL
        this[19] = TvsConstants.POSTFIX_TRAILER
    }

    private fun frameB(): ByteArray = ByteArray(20).apply {
        this[0] = TvsConstants.START_BYTE_5B
        this[1] = TvsConstants.DATA_ID_NAVIGATION_DATA1
        this[19] = TvsConstants.POSTFIX_TRAILER
    }

    @Test
    fun classifiesPlaintextNavControl() {
        val c = FrameClassifier.classify(frameA())
        assertEquals(FrameClassifier.Category.NAV_CONTROL_PLAINTEXT, c.category)
        assertEquals(FrameClassifier.Confidence.HIGH, c.confidence)
    }

    @Test
    fun classifiesPlaintextNavText() {
        val c = FrameClassifier.classify(frameB())
        assertEquals(FrameClassifier.Category.NAV_TEXT_PLAINTEXT, c.category)
    }

    @Test
    fun classifiesTelemetry() {
        val telemetry = byteArrayOf(TvsConstants.START_BYTE_5A, TvsConstants.DATA_ID_SPEEDOMETER_1, 0x2A, 0x00)
        val c = FrameClassifier.classify(telemetry)
        assertEquals(FrameClassifier.Category.TELEMETRY_PLAINTEXT, c.category)
    }

    @Test
    fun opaquePayloadIsProtectedUnclassified() {
        // High-entropy, no recognizable start/trailer — consistent with the protection layer.
        val opaque = byteArrayOf(0x37, 0xC1.toByte(), 0x9A.toByte(), 0x04, 0xEE.toByte(), 0x71, 0x22, 0xB8.toByte())
        val c = FrameClassifier.classify(opaque)
        assertEquals(FrameClassifier.Category.PROTECTED_UNCLASSIFIED, c.category)
    }

    @Test
    fun tooShortIsInsufficient() {
        assertEquals(FrameClassifier.Category.INSUFFICIENT, FrameClassifier.classify(byteArrayOf(0x5A)).category)
    }

    @Test
    fun detectsApprox400msSequencing() {
        val notifs = listOf(
            DiagnosticNotification(1000, TvsConstants.CHAR_NOTIFY_READ_UUID.toString(), 20, "5B 4F", 0),
            DiagnosticNotification(1400, TvsConstants.CHAR_NOTIFY_READ_UUID.toString(), 20, "5A 4E", 400),
        )
        val report = FrameClassifier.analyzeNavigationActivity(notifs)
        assertTrue(report.approx400msSequencingObserved)
        assertEquals(listOf(20), report.dominantLengths)
    }

    @Test
    fun emptyStreamReportsNotDetected() {
        val report = FrameClassifier.analyzeNavigationActivity(emptyList())
        assertFalse(report.detected)
        assertEquals(0, report.packetCount)
    }

    @Test
    fun countsBurstsAcrossIdleGaps() {
        val notifs = listOf(
            DiagnosticNotification(0, "x", 20, "5A 4E", 0),
            DiagnosticNotification(100, "x", 20, "5A 4E", 100),
            DiagnosticNotification(5000, "x", 20, "5A 4E", 4900), // idle gap -> new burst
        )
        val report = FrameClassifier.analyzeNavigationActivity(notifs)
        assertEquals(2, report.burstCount)
    }

    @Test
    fun hexRoundTrips() {
        val bytes = byteArrayOf(0x5A, 0x4E, 0xFF.toByte())
        val hex = com.ntorqnav.bridge.tvs.TvsPacketDecoder.toHexString(bytes)
        assertTrue(FrameClassifier.hexToBytes(hex).contentEquals(bytes))
    }
}
