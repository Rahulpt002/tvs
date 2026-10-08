package com.ntorqnav.bridge.replay

import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.tvs.TvsPacketDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class PacketDirection { TX, RX }

@Serializable
data class ReplayPacket(
    val timestampMs: Long,
    val direction: PacketDirection,
    val characteristicUuid: String,
    val hexPayload: String,
    val description: String = ""
)

@Serializable
data class ReplaySession(
    val sessionId: String,
    val description: String,
    val createdAt: Long,
    val packets: List<ReplayPacket>
)

class PacketReplaySystem {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _recordedPackets = MutableStateFlow<List<ReplayPacket>>(emptyList())
    val recordedPackets: StateFlow<List<ReplayPacket>> = _recordedPackets.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    private var replayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startRecording() {
        _recordedPackets.value = emptyList()
        _isRecording.value = true
        AppLogger.replay("Packet recording started")
    }

    fun stopRecording(): ReplaySession {
        _isRecording.value = false
        val session = ReplaySession(
            sessionId = "session_${System.currentTimeMillis()}",
            description = "TVS NTORQ Navigation Diagnostic Session",
            createdAt = System.currentTimeMillis(),
            packets = _recordedPackets.value
        )
        AppLogger.replay("Packet recording stopped. Captured ${session.packets.size} packets.")
        return session
    }

    fun recordPacket(direction: PacketDirection, characteristicUuid: UUID, payload: ByteArray, desc: String = "") {
        if (!_isRecording.value) return
        val packet = ReplayPacket(
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            characteristicUuid = characteristicUuid.toString(),
            hexPayload = TvsPacketDecoder.toHexString(payload),
            description = desc
        )
        _recordedPackets.update { it + packet }
    }

    fun replaySession(
        session: ReplaySession,
        speedMultiplier: Double = 1.0,
        onPacketDispatch: (UUID, ByteArray) -> Unit
    ) {
        if (session.packets.isEmpty()) return
        replayJob?.cancel()
        _isReplaying.value = true

        replayJob = scope.launch {
            AppLogger.replay("Starting replay of session '${session.sessionId}' (${session.packets.size} packets, ${speedMultiplier}x speed)")
            val startTime = session.packets.first().timestampMs

            for (packet in session.packets) {
                val delayTime = ((packet.timestampMs - startTime) / speedMultiplier).toLong().coerceAtLeast(0)
                delay(delayTime.coerceAtMost(2000L)) // smooth capped delay

                val rawBytes = parseHex(packet.hexPayload)
                val uuid = UUID.fromString(packet.characteristicUuid)
                AppLogger.replay("Replaying packet [${packet.direction}] -> ${packet.description}")
                onPacketDispatch(uuid, rawBytes)
            }

            _isReplaying.value = false
            AppLogger.replay("Session replay completed.")
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        _isReplaying.value = false
    }

    fun serializeSession(session: ReplaySession): String = json.encodeToString(session)

    fun deserializeSession(jsonString: String): ReplaySession = json.decodeFromString(jsonString)

    private fun parseHex(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
