package com.ntorqnav.bridge.replay

import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.*
import org.junit.Test

class PacketReplaySystemTest {

    @Test
    fun testRecordSerializeAndDeserializeSession() {
        val replaySystem = PacketReplaySystem()

        replaySystem.startRecording()
        replaySystem.recordPacket(
            direction = PacketDirection.TX,
            characteristicUuid = TvsConstants.CHAR_WRITE_UUID,
            payload = byteArrayOf(0x5A, 0x4E, 0x00, 0xFA.toByte()),
            desc = "Frame A Test"
        )
        replaySystem.recordPacket(
            direction = PacketDirection.RX,
            characteristicUuid = TvsConstants.CHAR_NOTIFY_READ_UUID,
            payload = byteArrayOf(0x5A, 0x10, 0x2A),
            desc = "Speedo Test"
        )

        val session = replaySystem.stopRecording()
        assertEquals(2, session.packets.size)
        assertEquals("Frame A Test", session.packets[0].description)
        assertEquals(PacketDirection.TX, session.packets[0].direction)

        val json = replaySystem.serializeSession(session)
        assertTrue("JSON must contain sessionId", json.contains(session.sessionId))

        val restored = replaySystem.deserializeSession(json)
        assertEquals(session.sessionId, restored.sessionId)
        assertEquals(2, restored.packets.size)
        assertEquals(session.packets[0].hexPayload, restored.packets[0].hexPayload)
    }
}
