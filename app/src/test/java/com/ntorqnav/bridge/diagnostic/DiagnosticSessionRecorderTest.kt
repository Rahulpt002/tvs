package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.bluetooth.DiscoveredGattCharacteristic
import com.ntorqnav.bridge.bluetooth.DiscoveredGattService
import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticSessionRecorderTest {

    /** Deterministic monotonic clock for reproducible timestamps/deltas. */
    private class FakeClock(start: Long = 0) {
        private var t = start
        fun advance(ms: Long) { t += ms }
        fun now(): Long = t
    }

    private fun fullProfile(): List<DiscoveredGattService> = listOf(
        DiscoveredGattService(
            uuid = TvsConstants.SERVICE_UUID_NTORQ,
            isPrimary = true,
            characteristics = listOf(
                DiscoveredGattCharacteristic(TvsConstants.CHAR_WRITE_UUID, listOf("WRITE"), false, true, false),
                DiscoveredGattCharacteristic(TvsConstants.CHAR_NOTIFY_READ_UUID, listOf("NOTIFY"), false, false, true),
            ),
        ),
    )

    @Test
    fun recordsConnectionMetadataAndNotifications() {
        val clock = FakeClock()
        val rec = DiagnosticSessionRecorder(clock = clock::now)

        rec.startRecording("test_session")
        assertTrue(rec.isRecording.value)

        rec.setDevice(DiagnosticDeviceInfo(name = "U577", address = "AA:BB:CC:DD:EE:FF", rssi = -55, tvsServiceAdvertised = true))
        rec.setDiscoveredServices(fullProfile())
        clock.advance(10); rec.setMtu(247)
        clock.advance(10); rec.recordNotification(TvsConstants.CHAR_NOTIFY_READ_UUID, byteArrayOf(0x5A, 0x10, 0x2A))
        clock.advance(400); rec.recordNotification(TvsConstants.CHAR_NOTIFY_READ_UUID, byteArrayOf(0x5A, 0x10, 0x2B))

        val session = rec.stopRecording()
        assertFalse(rec.isRecording.value)

        assertEquals("test_session", session.sessionId)
        assertEquals(247, session.negotiatedMtu)
        assertEquals("U577", session.device?.name)
        assertEquals(2, session.notifications.size)
        assertEquals(400, session.notifications[1].deltaMsFromPrev)
        assertEquals(1, session.services.size)
        assertEquals(2, session.characteristics.size)
        // timestamps collected and sorted ascending
        assertEquals(session.timestamps.sorted(), session.timestamps)
    }

    @Test
    fun doesNotRecordWhenStopped() {
        val rec = DiagnosticSessionRecorder()
        // No startRecording called.
        rec.recordNotification(UUID.randomUUID(), byteArrayOf(1, 2, 3))
        assertEquals(null, rec.draft.value)
    }

    @Test
    fun serializationRoundTripsPreservingRawBytes() {
        val rec = DiagnosticSessionRecorder()
        rec.startRecording("roundtrip")
        rec.recordNotification(TvsConstants.CHAR_NOTIFY_READ_UUID, byteArrayOf(0x5B, 0x4F, 0x4D, 0x47))
        val session = rec.stopRecording()

        val json = rec.export(session)
        assertTrue(json.contains("roundtrip"))

        val restored = rec.import(json)
        assertEquals(session.sessionId, restored.sessionId)
        assertEquals(session.notifications.size, restored.notifications.size)
        assertEquals(session.notifications[0].hexBytes, restored.notifications[0].hexBytes)
    }

    @Test
    fun replayReEmitsAllRecordedNotifications() {
        val rec = DiagnosticSessionRecorder()
        rec.startRecording("replay")
        // Same timestamp => zero wait => fast deterministic replay.
        repeat(3) { rec.recordNotification(TvsConstants.CHAR_NOTIFY_READ_UUID, byteArrayOf(0x5A, 0x4E, it.toByte())) }
        val session = rec.stopRecording()

        val received = mutableListOf<ByteArray>()
        val latch = CountDownLatch(session.notifications.size)
        rec.replay(session, speedMultiplier = 1000.0) { _, bytes ->
            synchronized(received) { received.add(bytes) }
            latch.countDown()
        }
        assertTrue("replay did not emit all packets", latch.await(5, TimeUnit.SECONDS))
        assertEquals(3, received.size)
    }

    @Test
    fun identityDefaultsToUnknownAndIsNeverInferred() {
        val rec = DiagnosticSessionRecorder()
        rec.startRecording("identity")
        rec.setDiscoveredServices(fullProfile()) // presence of service must NOT set vehicle type
        val session = rec.stopRecording()
        assertEquals(UNKNOWN, session.identity.vehicleType)
        assertEquals(UNKNOWN, session.identity.firmwareVersion)
    }
}
