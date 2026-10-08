package com.ntorqnav.bridge.tvs

import com.ntorqnav.bridge.logging.AppLogger
import com.ntorqnav.bridge.navigation.NavigationUpdate

interface TvsNavigationProtocol {
    suspend fun startNavigation()
    suspend fun sendNavigationUpdate(update: NavigationUpdate): ProtectedTransmissionResult
    suspend fun stopNavigation(): ProtectedTransmissionResult
}

class TvsNavigationProtocolImpl(
    private val protectedTransport: TvsProtectedTransport
) : TvsNavigationProtocol {

    override suspend fun startNavigation() {
        AppLogger.protocol("Starting navigation session on TVS Protocol")
    }

    override suspend fun sendNavigationUpdate(update: NavigationUpdate): ProtectedTransmissionResult {
        AppLogger.protocol("Encoding navigation update: ${update.toSummary()}")
        val (frameA, frameB) = TvsPacketEncoder.encodeNavigationFrames(update, isNavigationStopped = false)
        return protectedTransport.transmitNavigationFrames(frameA, frameB)
    }

    override suspend fun stopNavigation(): ProtectedTransmissionResult {
        AppLogger.protocol("Encoding stop navigation frame")
        val dummyUpdate = NavigationUpdate(
            maneuver = com.ntorqnav.bridge.navigation.Maneuver.DESTINATION,
            distanceMeters = 0,
            roadName = "Nav Ended",
            etaMinutes = 0,
            timestamp = System.currentTimeMillis(),
            instruction = "Navigation Ended",
            totalDistanceMeters = 0,
            source = "internal_stop"
        )
        val (frameA, frameB) = TvsPacketEncoder.encodeNavigationFrames(dummyUpdate, isNavigationStopped = true)
        return protectedTransport.transmitNavigationFrames(frameA, frameB)
    }
}
