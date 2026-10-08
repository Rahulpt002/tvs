package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticAnalysisComparatorTest {

    private fun sessionWithProfile(): DiagnosticSession = DiagnosticSession(
        sessionId = "s",
        createdAtMs = 0,
        negotiatedMtu = 247,
        bondingState = BondingState.NONE,
        services = listOf(
            DiagnosticServiceInfo(TvsConstants.SERVICE_UUID_NTORQ.toString(), true, isExpectedTvsService = true),
        ),
        characteristics = listOf(
            DiagnosticCharacteristicInfo(
                TvsConstants.SERVICE_UUID_NTORQ.toString(), TvsConstants.CHAR_WRITE_UUID.toString(),
                listOf("WRITE"), false, true, false, role = GattVerifier.ROLE_WRITE,
            ),
            DiagnosticCharacteristicInfo(
                TvsConstants.SERVICE_UUID_NTORQ.toString(), TvsConstants.CHAR_NOTIFY_READ_UUID.toString(),
                listOf("NOTIFY"), false, false, true, role = GattVerifier.ROLE_NOTIFY,
            ),
        ),
    )

    @Test
    fun matchesConfirmedProfile() {
        val comparisons = StaticAnalysisComparator.compare(sessionWithProfile())
        val primary = comparisons.single { it.item == "Primary service" }
        assertEquals(ComparisonStatus.MATCH, primary.status)
        val write = comparisons.single { it.item.startsWith("Write characteristic") }
        assertEquals(ComparisonStatus.MATCH, write.status)
        val notify = comparisons.single { it.item.startsWith("Notify characteristic") }
        assertEquals(ComparisonStatus.MATCH, notify.status)
        assertEquals("HIGH", StaticAnalysisComparator.overallConfidence(comparisons))
    }

    @Test
    fun neverFabricatesMatchForEmptySession() {
        val empty = DiagnosticSession(sessionId = "e", createdAtMs = 0)
        val comparisons = StaticAnalysisComparator.compare(empty)
        assertTrue(comparisons.none { it.status == ComparisonStatus.MATCH })
        comparisons.filter { it.item.contains("characteristic") || it.item.contains("service") }
            .forEach { assertEquals(ComparisonStatus.NOT_OBSERVED, it.status) }
        assertEquals("LOW", StaticAnalysisComparator.overallConfidence(comparisons))
    }

    @Test
    fun vehicleTypeNotObservedWhenUnknown() {
        val comparisons = StaticAnalysisComparator.compare(sessionWithProfile())
        val vt = comparisons.single { it.item.startsWith("Vehicle type") }
        assertEquals(ComparisonStatus.NOT_OBSERVED, vt.status)
        assertEquals(UNKNOWN, vt.observed)
    }

    @Test
    fun mtuComparedWhenObserved() {
        val comparisons = StaticAnalysisComparator.compare(sessionWithProfile())
        val mtu = comparisons.single { it.item == "Negotiated MTU" }
        assertEquals(ComparisonStatus.MATCH, mtu.status)
        assertEquals("247", mtu.observed)
    }

    @Test
    fun missingMtuIsNotObserved() {
        val comparisons = StaticAnalysisComparator.compare(DiagnosticSession("x", 0))
        val mtu = comparisons.single { it.item == "Negotiated MTU" }
        assertEquals(ComparisonStatus.NOT_OBSERVED, mtu.status)
    }
}
