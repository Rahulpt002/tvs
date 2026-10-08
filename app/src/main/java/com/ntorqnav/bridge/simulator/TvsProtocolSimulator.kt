package com.ntorqnav.bridge.simulator

import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.tvs.DecodedTvsPacket
import com.ntorqnav.bridge.tvs.TvsConstants
import com.ntorqnav.bridge.tvs.TvsPacketDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class SimulatedClusterDisplay(
    val pictogramId: Int = 0,
    val maneuverName: String = "IDLE",
    val distanceMeters: Int = 0,
    val roadName: String = "",
    val etaMinutes: Int = 0,
    val totalDistanceMeters: Int = 0,
    val isNavigationActive: Boolean = false,
    val rxPacketCount: Int = 0,
    val lastPacketTimestamp: Long = 0L,
    val lastPacketHex: String = ""
)

class TvsProtocolSimulator {

    private val _displayState = MutableStateFlow(SimulatedClusterDisplay())
    val displayState: StateFlow<SimulatedClusterDisplay> = _displayState.asStateFlow()

    val fakeTransport: FakeBluetoothTransport = FakeBluetoothTransport(
        onPacketReceived = { charUuid, data ->
            processIncomingCentralPacket(charUuid, data)
        }
    )

    fun processIncomingCentralPacket(characteristicUuid: UUID, data: ByteArray) {
        val hexDump = TvsPacketDecoder.toHexString(data)
        val decoded = TvsPacketDecoder.decode(data)

        _displayState.update { current ->
            when (decoded) {
                is DecodedTvsPacket.FrameANavigationControl -> {
                    AppLogger.protocol("SIMULATOR: Decoded Frame A - dist=${decoded.distanceMeters}m, picto=${decoded.pictogramId}, eta=${decoded.etaMinutes}m, stopped=${decoded.isStopped}")
                    current.copy(
                        pictogramId = decoded.pictogramId,
                        maneuverName = mapPictoToManeuverName(decoded.pictogramId),
                        distanceMeters = decoded.distanceMeters,
                        etaMinutes = decoded.etaMinutes,
                        totalDistanceMeters = decoded.totalDistanceMeters,
                        isNavigationActive = !decoded.isStopped,
                        rxPacketCount = current.rxPacketCount + 1,
                        lastPacketTimestamp = System.currentTimeMillis(),
                        lastPacketHex = hexDump
                    )
                }
                is DecodedTvsPacket.FrameBNavigationText -> {
                    AppLogger.protocol("SIMULATOR: Decoded Frame B - road/text='${decoded.text}'")
                    current.copy(
                        roadName = decoded.text,
                        rxPacketCount = current.rxPacketCount + 1,
                        lastPacketTimestamp = System.currentTimeMillis(),
                        lastPacketHex = hexDump
                    )
                }
                else -> {
                    AppLogger.protocol("SIMULATOR: Received frame [${data.size}B]: $hexDump")
                    current.copy(
                        rxPacketCount = current.rxPacketCount + 1,
                        lastPacketTimestamp = System.currentTimeMillis(),
                        lastPacketHex = hexDump
                    )
                }
            }
        }
    }

    private fun mapPictoToManeuverName(pictoId: Int): String = when (pictoId) {
        1 -> "STRAIGHT"
        2 -> "TURN LEFT"
        12 -> "TURN RIGHT"
        10 -> "SLIGHT LEFT"
        11 -> "SLIGHT RIGHT"
        22 -> "U-TURN"
        9 -> "ROUNDABOUT"
        62 -> "DESTINATION"
        0 -> "NO MANEUVER"
        else -> "PICTO #$pictoId"
    }

    /**
     * Emulates periodic Speedometer frames from TFT back to App
     */
    fun emitMockSpeedometerFrame(speedKmph: Int, fuelPercent: Int, odoKm: Int) {
        val frame = ByteArray(20)
        frame[0] = TvsConstants.START_BYTE_5A
        frame[1] = TvsConstants.DATA_ID_SPEEDOMETER_1
        frame[2] = speedKmph.toByte()
        frame[3] = fuelPercent.toByte()
        frame[4] = ((odoKm shr 16) and 0xFF).toByte()
        frame[5] = ((odoKm shr 8) and 0xFF).toByte()
        frame[6] = (odoKm and 0xFF).toByte()
        frame[19] = TvsConstants.POSTFIX_TRAILER

        fakeTransport.emitSimulatedNotification(frame)
    }

    fun reset() {
        _displayState.value = SimulatedClusterDisplay()
    }
}
