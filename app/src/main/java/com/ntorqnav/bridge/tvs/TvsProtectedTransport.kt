package com.ntorqnav.bridge.tvs

import com.ntorqnav.bridge.bluetooth.BluetoothTransport
import com.ntorqnav.bridge.logging.AppLogger
import kotlinx.coroutines.delay
import java.util.UUID

sealed interface ProtectedTransmissionResult {
    data class Success(val framesTransmitted: Int, val bytesTotal: Int) : ProtectedTransmissionResult
    data class BlockedBySafetyInterlock(val reason: String, val diagnosticDump: String) : ProtectedTransmissionResult
    data class Error(val error: Throwable) : ProtectedTransmissionResult
}

/**
 * Transport abstraction isolating the rest of the application from the underlying
 * protection/obfuscation implementation.
 *
 * Implements strict safety guardrails:
 * - Characteristic UUID whitelisting (only ALLOWED_WRITE_CHARACTERISTICS)
 * - Safe interlock prohibiting unverified transmissions to the physical vehicle
 * - Unimpeded diagnostic & simulation operation
 */
interface TvsProtectedTransport {
    val transportName: String
    val isSafetyInterlockEngaged: Boolean

    suspend fun transmitNavigationFrames(
        frameA: ByteArray,
        frameB: ByteArray,
        delayMs: Long = TvsConstants.INTER_FRAME_DELAY_MS
    ): ProtectedTransmissionResult
}

/**
 * Default safe diagnostic transport.
 * Operates in diagnostic mode: validates frames, verifies characteristic whitelist, logs hex dumps,
 * and maintains the safety interlock preventing unverified vehicle writes.
 */
class DiagnosticSafeProtectedTransport(
    private val underlyingTransport: BluetoothTransport? = null,
    val allowPhysicalTransmission: Boolean = false
) : TvsProtectedTransport {

    override val transportName: String = "DiagnosticSafeProtectedTransport"
    override val isSafetyInterlockEngaged: Boolean = !allowPhysicalTransmission

    override suspend fun transmitNavigationFrames(
        frameA: ByteArray,
        frameB: ByteArray,
        delayMs: Long
    ): ProtectedTransmissionResult {
        // Step 1: Whitelist check
        val writeCharUuid = TvsConstants.CHAR_WRITE_UUID
        if (!TvsConstants.ALLOWED_WRITE_CHARACTERISTICS.contains(writeCharUuid)) {
            val err = "Blocked: Characteristic $writeCharUuid is not in the safety whitelist"
            AppLogger.safety(err)
            return ProtectedTransmissionResult.BlockedBySafetyInterlock(err, "")
        }

        // Step 2: Diagnostic hex logging
        val dumpA = TvsPacketDecoder.toHexString(frameA)
        val dumpB = TvsPacketDecoder.toHexString(frameB)
        AppLogger.protocol("Diagnostic Frame A [${frameA.size}B]: $dumpA")
        AppLogger.protocol("Diagnostic Frame B [${frameB.size}B]: $dumpB")

        // Step 3: Safety Interlock Enforcement
        if (isSafetyInterlockEngaged) {
            val notice = "Safety Interlock Engaged: Frame transmission to physical vehicle is gated until supported protocol requirements are established."
            AppLogger.safety(notice)
            return ProtectedTransmissionResult.BlockedBySafetyInterlock(
                reason = notice,
                diagnosticDump = "Frame A: $dumpA\nFrame B: $dumpB"
            )
        }

        // Step 4: Physical transmission if explicitly unlocked and underlying transport is connected
        return try {
            val transport = underlyingTransport
                ?: return ProtectedTransmissionResult.Error(IllegalStateException("No underlying Bluetooth transport configured"))

            // Transmit Frame B first, then delay, then Frame A (matches TVS Connect order)
            transport.write(writeCharUuid, frameB)
            delay(delayMs)
            transport.write(writeCharUuid, frameA)

            AppLogger.protocol("Transmitted 2 navigation frames to physical cluster successfully")
            ProtectedTransmissionResult.Success(2, frameA.size + frameB.size)
        } catch (t: Throwable) {
            AppLogger.error("Failed to transmit frames over BluetoothTransport", t)
            ProtectedTransmissionResult.Error(t)
        }
    }
}
