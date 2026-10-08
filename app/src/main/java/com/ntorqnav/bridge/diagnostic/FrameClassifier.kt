package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.tvs.TvsConstants
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Sections 7 & 8 — navigation-session identification and frame classification.
 *
 * Classification uses only *observable* properties: length, timing, direction, characteristic,
 * frequency and sequence. It never decrypts a frame. When a payload shows no recognizable
 * plaintext structure (i.e. the protection layer prevents meaningful classification), it is
 * reported as [Category.PROTECTED_UNCLASSIFIED] rather than guessed at.
 */
object FrameClassifier {

    @Serializable
    enum class Category {
        /** Plaintext Frame A navigation-control structure recognised (0x5A 0x4E ... 0xFF). */
        NAV_CONTROL_PLAINTEXT,
        /** Plaintext Frame B navigation-text structure recognised (0x5B 0x4F ... 0xFF). */
        NAV_TEXT_PLAINTEXT,
        /** Plaintext cluster speedometer/telemetry frame recognised (0x5A + telemetry id). */
        TELEMETRY_PLAINTEXT,
        /** 20-byte frame with a plausible start/trailer but no known data id. */
        STRUCTURED_UNKNOWN,
        /** No recognizable plaintext structure — consistent with the protection layer. */
        PROTECTED_UNCLASSIFIED,
        /** Too short / empty to classify. */
        INSUFFICIENT,
    }

    enum class Confidence { HIGH, MEDIUM, LOW }

    @Serializable
    data class FrameClassification(
        val timestampMs: Long,
        val characteristicUuid: String,
        val direction: String,
        val length: Int,
        val deltaMsFromPrevMs: Long,
        val category: Category,
        val confidence: Confidence,
        val firstByteHex: String,
        val secondByteHex: String,
    )

    private val NAV_FRAME_LENGTH = 20

    fun classify(
        bytes: ByteArray,
        timestampMs: Long = 0,
        characteristicUuid: String = UNKNOWN,
        direction: String = "RX",
        deltaMsFromPrev: Long = 0,
    ): FrameClassification {
        val category: Category
        val confidence: Confidence

        if (bytes.size < 2) {
            category = Category.INSUFFICIENT
            confidence = Confidence.HIGH
        } else {
            val header = bytes[0]
            val dataId = bytes[1]
            val hasTrailer = bytes.last() == TvsConstants.POSTFIX_TRAILER
            when {
                header == TvsConstants.START_BYTE_5A && dataId == TvsConstants.DATA_ID_NAVIGATION_CONTROL -> {
                    category = Category.NAV_CONTROL_PLAINTEXT
                    confidence = if (hasTrailer && bytes.size == NAV_FRAME_LENGTH) Confidence.HIGH else Confidence.MEDIUM
                }
                header == TvsConstants.START_BYTE_5B && dataId == TvsConstants.DATA_ID_NAVIGATION_DATA1 -> {
                    category = Category.NAV_TEXT_PLAINTEXT
                    confidence = if (hasTrailer && bytes.size == NAV_FRAME_LENGTH) Confidence.HIGH else Confidence.MEDIUM
                }
                header == TvsConstants.START_BYTE_5A && isTelemetryId(dataId) -> {
                    category = Category.TELEMETRY_PLAINTEXT
                    confidence = Confidence.MEDIUM
                }
                (header == TvsConstants.START_BYTE_5A || header == TvsConstants.START_BYTE_5B) &&
                    hasTrailer && bytes.size == NAV_FRAME_LENGTH -> {
                    category = Category.STRUCTURED_UNKNOWN
                    confidence = Confidence.LOW
                }
                else -> {
                    category = Category.PROTECTED_UNCLASSIFIED
                    confidence = Confidence.LOW
                }
            }
        }

        return FrameClassification(
            timestampMs = timestampMs,
            characteristicUuid = characteristicUuid,
            direction = direction,
            length = bytes.size,
            deltaMsFromPrevMs = deltaMsFromPrev,
            category = category,
            confidence = confidence,
            firstByteHex = bytes.getOrNull(0)?.let { "%02X".format(it) } ?: "--",
            secondByteHex = bytes.getOrNull(1)?.let { "%02X".format(it) } ?: "--",
        )
    }

    private fun isTelemetryId(dataId: Byte): Boolean = dataId == TvsConstants.DATA_ID_SPEEDOMETER_1 ||
        dataId == TvsConstants.DATA_ID_SPEEDOMETER_2 ||
        dataId == TvsConstants.DATA_ID_SPEEDOMETER_3 ||
        dataId == TvsConstants.DATA_ID_SPEEDOMETER_4

    /** Classify a recorded notification stream in order, computing deltas from timestamps. */
    fun classifyNotifications(notifications: List<DiagnosticNotification>): List<FrameClassification> =
        notifications.map { n ->
            classify(
                bytes = hexToBytes(n.hexBytes),
                timestampMs = n.timestampMs,
                characteristicUuid = n.characteristicUuid,
                direction = "RX",
                deltaMsFromPrev = n.deltaMsFromPrev,
            )
        }

    // ---- Section 7: navigation-session / timing identification ----

    @Serializable
    data class NavigationActivityReport(
        val detected: Boolean,
        val packetCount: Int,
        /** Packet lengths ranked by how often they occur. */
        val dominantLengths: List<Int>,
        /** Number of burst clusters (groups of packets separated by a long idle gap). */
        val burstCount: Int,
        /** True if any adjacent pair is separated by ~[TvsConstants.INTER_FRAME_DELAY_MS]. */
        val approx400msSequencingObserved: Boolean,
        val interPacketDeltasMs: List<Long>,
        val notes: String,
    )

    /**
     * Section 7 — look for the predicted navigation signature in a recorded stream:
     * packet bursts, repeated packet lengths, and ~400 ms Frame-B→Frame-A sequencing.
     *
     * Honesty note surfaced in [NavigationActivityReport.notes]: a passive third-party app only
     * observes RX notifications on 0x5354. The official app's phone→cluster writes on 0x5352
     * are not over-the-air visible here, so the 400 ms write cadence may legitimately be
     * NOT OBSERVED even while official navigation is running normally.
     */
    fun analyzeNavigationActivity(
        notifications: List<DiagnosticNotification>,
        toleranceMs: Long = 150,
        burstIdleGapMs: Long = 1500,
    ): NavigationActivityReport {
        if (notifications.isEmpty()) {
            return NavigationActivityReport(
                detected = false,
                packetCount = 0,
                dominantLengths = emptyList(),
                burstCount = 0,
                approx400msSequencingObserved = false,
                interPacketDeltasMs = emptyList(),
                notes = "No notifications recorded.",
            )
        }

        val sorted = notifications.sortedBy { it.timestampMs }
        val deltas = sorted.zipWithNext { a, b -> b.timestampMs - a.timestampMs }

        val dominantLengths = sorted.groupingBy { it.length }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

        val burstCount = 1 + deltas.count { it > burstIdleGapMs }

        val target = TvsConstants.INTER_FRAME_DELAY_MS
        val approx400ms = deltas.any { abs(it - target) <= toleranceMs }

        val repeatedLengths = dominantLengths.isNotEmpty() &&
            sorted.groupingBy { it.length }.eachCount().values.max() >= 2
        val detected = sorted.size >= 2 && repeatedLengths

        val notes = buildString {
            append("Passive observation sees RX notifications on the notify characteristic only. ")
            append("Official phone→cluster writes (where the predicted 400 ms Frame B→A cadence lives) ")
            append("are not over-the-air visible to a third-party app, so a NOT OBSERVED result for the ")
            append("400 ms cadence does not disprove the static model.")
        }

        return NavigationActivityReport(
            detected = detected,
            packetCount = sorted.size,
            dominantLengths = dominantLengths,
            burstCount = burstCount,
            approx400msSequencingObserved = approx400ms,
            interPacketDeltasMs = deltas,
            notes = notes,
        )
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").trim()
        if (clean.isEmpty()) return ByteArray(0)
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val idx = i * 2
            result[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
        }
        return result
    }
}
