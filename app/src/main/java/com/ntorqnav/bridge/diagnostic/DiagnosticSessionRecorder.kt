package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.bluetooth.DiscoveredGattService
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Sections 4 & 5 — the diagnostic session recorder.
 *
 * Records connection metadata, service discovery, and raw notification bytes exactly as
 * observed, with timestamps. It is passive: it never writes to the peripheral and only ever
 * stores what the device naturally exposes. Replay re-emits recorded notifications locally
 * (into the app's own UI/decoder) and never transmits anything to a vehicle.
 */
class DiagnosticSessionRecorder(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    private val _draft = MutableStateFlow<DiagnosticSession?>(null)
    /** Live view of the session being recorded (or the last completed one). */
    val draft: StateFlow<DiagnosticSession?> = _draft.asStateFlow()

    private var replayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startRecording(sessionId: String = "rtval_${clock()}"): DiagnosticSession {
        val session = DiagnosticSession(sessionId = sessionId, createdAtMs = clock())
        _draft.value = session
        _isRecording.value = true
        AppLogger.replay("Diagnostic recording started: $sessionId")
        recordEvent(DiagnosticEventType.RECORDING_STARTED, detail = sessionId)
        return session
    }

    fun setDevice(info: DiagnosticDeviceInfo) = mutate { it.copy(device = info) }

    fun setMtu(mtu: Int) {
        mutate { it.copy(negotiatedMtu = mtu) }
        recordEvent(DiagnosticEventType.MTU_NEGOTIATED, detail = "MTU=$mtu")
    }

    fun setBondingState(state: BondingState) = mutate { it.copy(bondingState = state) }

    fun setIdentity(identity: DeviceIdentity) = mutate { it.copy(identity = identity) }

    /** Record the GATT profile from real discovery data. */
    fun setDiscoveredServices(services: List<DiscoveredGattService>) {
        val mapped = GattVerifier.mapServices(services)
        val chars = GattVerifier.mapCharacteristics(services)
        mutate { it.copy(services = mapped, characteristics = chars) }
        recordEvent(
            DiagnosticEventType.SERVICES_DISCOVERED,
            detail = "${mapped.size} services, ${chars.size} characteristics",
        )
    }

    fun recordEvent(
        type: DiagnosticEventType,
        connectionState: String = UNKNOWN,
        detail: String = "",
    ) {
        if (!_isRecording.value) return
        val now = clock()
        val event = DiagnosticEvent(now, type, connectionState, detail)
        mutate { it.copy(events = it.events + event, timestamps = it.timestamps + now) }
    }

    /** Record a raw notification exactly as received. Bytes are never decrypted. */
    fun recordNotification(characteristicUuid: UUID, bytes: ByteArray) {
        if (!_isRecording.value) return
        val now = clock()
        _draft.update { current ->
            if (current == null) return@update null
            val prev = current.notifications.lastOrNull()?.timestampMs
            val delta = if (prev != null) (now - prev).coerceAtLeast(0) else 0L
            val notif = DiagnosticNotification(
                timestampMs = now,
                characteristicUuid = characteristicUuid.toString(),
                length = bytes.size,
                hexBytes = TvsPacketDecoder.toHexString(bytes),
                deltaMsFromPrev = delta,
            )
            current.copy(
                notifications = current.notifications + notif,
                timestamps = current.timestamps + now,
            )
        }
    }

    fun stopRecording(): DiagnosticSession {
        recordEvent(DiagnosticEventType.RECORDING_STOPPED)
        _isRecording.value = false
        val session = (_draft.value ?: DiagnosticSession("empty", clock()))
            .let { it.copy(timestamps = it.timestamps.sorted()) }
        _draft.value = session
        AppLogger.replay("Diagnostic recording stopped: ${session.notifications.size} notifications, ${session.events.size} events")
        return session
    }

    fun export(session: DiagnosticSession): String = json.encodeToString(session)

    fun import(jsonString: String): DiagnosticSession = json.decodeFromString(jsonString)

    fun generateReport(session: DiagnosticSession): String = ValidationReportGenerator.generate(session)

    /**
     * Replay a recorded session locally, re-emitting notifications with their original
     * relative timing (capped). This feeds the app's own decoder/UI; it never transmits.
     */
    fun replay(
        session: DiagnosticSession,
        speedMultiplier: Double = 1.0,
        onNotification: (UUID, ByteArray) -> Unit,
    ) {
        if (session.notifications.isEmpty()) return
        replayJob?.cancel()
        _isReplaying.value = true
        replayJob = scope.launch {
            val start = session.notifications.first().timestampMs
            for (n in session.notifications) {
                val wait = ((n.timestampMs - start) / speedMultiplier).toLong().coerceIn(0, 2000)
                delay(wait)
                onNotification(UUID.fromString(n.characteristicUuid), FrameClassifier.hexToBytes(n.hexBytes))
            }
            _isReplaying.value = false
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
        _isReplaying.value = false
    }

    private inline fun mutate(block: (DiagnosticSession) -> DiagnosticSession) {
        _draft.update { it?.let(block) }
    }
}
