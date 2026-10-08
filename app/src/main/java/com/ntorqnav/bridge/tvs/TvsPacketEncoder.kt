package com.ntorqnav.bridge.tvs

import com.ntorqnav.bridge.navigation.Maneuver
import com.ntorqnav.bridge.navigation.NavigationUpdate
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Diagnostic Unprotected Protocol Encoder.
 *
 * Implements Frame A and Frame B generation as documented in the TVS static analysis.
 * Note: These generate UNPROTECTED diagnostic byte representations. Per project safety constraints,
 * handcrafted frames are never transmitted blindly to real vehicle hardware without establishing
 * supported behavior.
 */
object TvsPacketEncoder {

    /**
     * Map logical Maneuver to TVS cluster pictogram ID.
     * Based on NTORQ/Jupiter pictogram mapping table.
     */
    fun mapManeuverToPictogramId(maneuver: Maneuver): Int = when (maneuver) {
        Maneuver.STRAIGHT -> 1
        Maneuver.LEFT -> 2
        Maneuver.RIGHT -> 12
        Maneuver.SLIGHT_LEFT -> 10
        Maneuver.SLIGHT_RIGHT -> 11
        Maneuver.U_TURN -> 22
        Maneuver.ROUNDABOUT -> 9
        Maneuver.DESTINATION -> 62
        Maneuver.UNKNOWN -> 0
    }

    /**
     * Frame A — NAVIGATION_CONTROL (20 bytes)
     * Format:
     * Byte 0: 0x5A (Header)
     * Byte 1: 0x4E (DATA_ID_NAVIGATION_CONTROL)
     * Bytes 2-3: Distance to next instruction in meters (short, big-endian; 0xFFFF if >= 32767)
     * Bytes 4-5: Remaining time in minutes (short, big-endian)
     * Bytes 6-8: Total distance left in meters (3 bytes, big-endian)
     * Byte 9: Cluster pictogram ID
     * Byte 10: Instruction length flag (1 if <= 17 chars, 2 if longer)
     * Byte 11: Navigation on/off (0x00 = active, 0xFF = stopped)
     * Bytes 12-18: Zero padding
     * Byte 19: 0xFF (Trailer)
     */
    fun encodeFrameA(
        update: NavigationUpdate,
        pictogramId: Int = mapManeuverToPictogramId(update.maneuver),
        isNavigationStopped: Boolean = false
    ): ByteArray {
        val frame = ByteArray(20)
        frame[0] = TvsConstants.START_BYTE_5A
        frame[1] = TvsConstants.DATA_ID_NAVIGATION_CONTROL

        // Distance to next maneuver (Bytes 2-3)
        val distMeters = (update.distanceMeters ?: 0).coerceAtLeast(0)
        if (distMeters >= 32767) {
            frame[2] = 0xFF.toByte()
            frame[3] = 0xFF.toByte()
        } else {
            frame[2] = ((distMeters shr 8) and 0xFF).toByte()
            frame[3] = (distMeters and 0xFF).toByte()
        }

        // Remaining time in minutes (Bytes 4-5)
        val etaMinutes = (update.etaMinutes ?: 0).coerceAtLeast(0)
        frame[4] = ((etaMinutes shr 8) and 0xFF).toByte()
        frame[5] = (etaMinutes and 0xFF).toByte()

        // Total distance left in meters (Bytes 6-8, 3 bytes big-endian)
        val totalDist = (update.totalDistanceMeters ?: (distMeters + 1000)).coerceAtLeast(0)
        frame[6] = ((totalDist shr 16) and 0xFF).toByte()
        frame[7] = ((totalDist shr 8) and 0xFF).toByte()
        frame[8] = (totalDist and 0xFF).toByte()

        // Cluster Pictogram ID (Byte 9)
        frame[9] = (pictogramId and 0xFF).toByte()

        // Instruction length flag (Byte 10)
        val instructionLen = (update.instruction ?: update.roadName ?: "").length
        frame[10] = if (instructionLen > 17) 2 else 1

        // Navigation On/Off flag (Byte 11: 0 = active, -1 = stopped)
        frame[11] = if (isNavigationStopped) 0xFF.toByte() else 0x00

        // Zero-padding for bytes 12..18
        for (i in 12..18) {
            frame[i] = 0x00
        }

        // Trailer byte (Byte 19)
        frame[19] = TvsConstants.POSTFIX_TRAILER

        return frame
    }

    /**
     * Frame B — NAVIGATION_DATA1 (20 bytes)
     * Format:
     * Byte 0: 0x5B (Header)
     * Byte 1: 0x4F (DATA_ID_NAVIGATION_DATA1)
     * Bytes 2-18: UTF-8 instruction text (up to 17 bytes, 0-padded)
     * Byte 19: 0xFF (Trailer)
     */
    fun encodeFrameB(instructionText: String): ByteArray {
        val frame = ByteArray(20)
        frame[0] = TvsConstants.START_BYTE_5B
        frame[1] = TvsConstants.DATA_ID_NAVIGATION_DATA1

        val textBytes = instructionText.toByteArray(StandardCharsets.UTF_8)
        val copyLen = textBytes.size.coerceAtMost(17)

        System.arraycopy(textBytes, 0, frame, 2, copyLen)
        // Ensure remaining bytes up to index 18 are 0
        for (i in (2 + copyLen)..18) {
            frame[i] = 0x00
        }

        frame[19] = TvsConstants.POSTFIX_TRAILER
        return frame
    }

    /**
     * Creates both Frame A and Frame B for a navigation update.
     */
    fun encodeNavigationFrames(
        update: NavigationUpdate,
        isNavigationStopped: Boolean = false
    ): Pair<ByteArray, ByteArray> {
        val text = update.roadName ?: update.instruction ?: update.maneuver.name
        val frameA = encodeFrameA(update, isNavigationStopped = isNavigationStopped)
        val frameB = encodeFrameB(text)
        return Pair(frameA, frameB)
    }
}
