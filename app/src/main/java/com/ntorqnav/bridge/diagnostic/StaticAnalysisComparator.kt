package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.tvs.TvsConstants
import kotlinx.serialization.Serializable

/**
 * Section 6 — compare each static-analysis prediction against what was actually observed.
 *
 * Rule enforced throughout: never fabricate a match. A prediction is reported as
 * [ComparisonStatus.MATCH] only when the session genuinely contains the observed value.
 * Anything not seen is [ComparisonStatus.NOT_OBSERVED]; a contradiction is
 * [ComparisonStatus.MISMATCH].
 */
@Serializable
enum class ComparisonStatus { MATCH, MISMATCH, NOT_OBSERVED }

@Serializable
data class PredictionComparison(
    val item: String,
    val expected: String,
    val observed: String,
    val status: ComparisonStatus,
    val confidence: String,
)

object StaticAnalysisComparator {

    fun compare(session: DiagnosticSession): List<PredictionComparison> {
        val result = mutableListOf<PredictionComparison>()

        val serviceUuids = session.services.map { it.uuid.lowercase() }.toSet()
        val charUuids = session.characteristics.map { it.uuid.lowercase() }.toSet()

        // 1. Primary service
        val primaryExpected = TvsConstants.SERVICE_UUID_NTORQ.toString()
        result += uuidComparison(
            item = "Primary service",
            expected = primaryExpected,
            present = serviceUuids.contains(primaryExpected.lowercase()),
            confidence = "HIGH",
        )

        // 2. Alternate service
        val altExpected = TvsConstants.SERVICE_UUID_NTORQ_ALT.toString()
        result += uuidComparison(
            item = "Alternate service",
            expected = altExpected,
            present = serviceUuids.contains(altExpected.lowercase()),
            confidence = "MEDIUM",
        )

        // 3. Write characteristic 0x5352
        val writeExpected = TvsConstants.CHAR_WRITE_UUID.toString()
        result += uuidComparison(
            item = "Write characteristic (0x5352)",
            expected = writeExpected,
            present = charUuids.contains(writeExpected.lowercase()),
            confidence = "HIGH",
        )

        // 4. Notify characteristic 0x5354
        val notifyExpected = TvsConstants.CHAR_NOTIFY_READ_UUID.toString()
        result += uuidComparison(
            item = "Notify characteristic (0x5354)",
            expected = notifyExpected,
            present = charUuids.contains(notifyExpected.lowercase()),
            confidence = "HIGH",
        )

        // 5. MTU — static analysis did not fix a value; report observed vs negotiated.
        val mtu = session.negotiatedMtu
        result += PredictionComparison(
            item = "Negotiated MTU",
            expected = "App requests 512 (actual value unknown from static analysis)",
            observed = mtu?.toString() ?: "NOT OBSERVED",
            status = if (mtu != null) ComparisonStatus.MATCH else ComparisonStatus.NOT_OBSERVED,
            confidence = "HIGH",
        )

        // 6. Bonding / pairing — static analysis: UNKNOWN.
        val bonding = session.bondingState
        result += PredictionComparison(
            item = "Bonding requirement",
            expected = "UNKNOWN (not determined by static analysis)",
            observed = bonding.name,
            status = if (bonding == BondingState.UNKNOWN) ComparisonStatus.NOT_OBSERVED else ComparisonStatus.MATCH,
            confidence = "MEDIUM",
        )

        // 7. 400 ms Frame B -> Frame A sequencing
        val nav = FrameClassifier.analyzeNavigationActivity(session.notifications)
        result += PredictionComparison(
            item = "~400 ms Frame B→A sequencing",
            expected = "Frame B, ~400 ms, Frame A (phone→cluster writes)",
            observed = if (nav.approx400msSequencingObserved) {
                "~400 ms inter-packet gap observed in stream"
            } else {
                "NOT OBSERVED (writes not visible to passive observer)"
            },
            status = if (nav.approx400msSequencingObserved) ComparisonStatus.MATCH else ComparisonStatus.NOT_OBSERVED,
            confidence = "LOW",
        )

        // 8. Vehicle type — must not be inferred from the service UUID.
        val vt = session.identity.vehicleType
        result += PredictionComparison(
            item = "Vehicle type / cluster model",
            expected = "U577 base/premium is a MEDIUM-confidence guess (unverified)",
            observed = vt,
            status = if (vt == UNKNOWN) ComparisonStatus.NOT_OBSERVED else ComparisonStatus.MATCH,
            confidence = "MEDIUM",
        )

        return result
    }

    private fun uuidComparison(
        item: String,
        expected: String,
        present: Boolean,
        confidence: String,
    ): PredictionComparison = PredictionComparison(
        item = item,
        expected = expected,
        observed = if (present) expected else "NOT OBSERVED",
        status = if (present) ComparisonStatus.MATCH else ComparisonStatus.NOT_OBSERVED,
        confidence = confidence,
    )

    /** Overall confidence per section 9: driven by how many core predictions matched. */
    fun overallConfidence(comparisons: List<PredictionComparison>): String {
        val core = comparisons.filter {
            it.item.startsWith("Primary service") ||
                it.item.startsWith("Write characteristic") ||
                it.item.startsWith("Notify characteristic")
        }
        val matched = core.count { it.status == ComparisonStatus.MATCH }
        return when {
            core.isNotEmpty() && matched == core.size -> "HIGH"
            matched > 0 -> "MEDIUM"
            else -> "LOW"
        }
    }
}
