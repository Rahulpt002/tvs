package com.ntorqnav.bridge.diagnostic

import com.ntorqnav.bridge.tvs.TvsConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationReportGeneratorTest {

    private fun confirmedSession() = DiagnosticSession(
        sessionId = "report_test",
        createdAtMs = 0,
        device = DiagnosticDeviceInfo("U577", "AA:BB:CC:DD:EE:FF", -50, tvsServiceAdvertised = true),
        negotiatedMtu = 247,
        bondingState = BondingState.NONE,
        services = listOf(DiagnosticServiceInfo(TvsConstants.SERVICE_UUID_NTORQ.toString(), true, true)),
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
    fun reportContainsAllRequiredSections() {
        val md = ValidationReportGenerator.generate(confirmedSession())
        listOf("# Device", "# GATT", "# Connection", "# Navigation", "# Static analysis comparison", "# Unknowns", "# Confidence")
            .forEach { assertTrue("missing section: $it", md.contains(it)) }
    }

    @Test
    fun confirmedProfileReportsHighConfidence() {
        val md = ValidationReportGenerator.generate(confirmedSession())
        assertTrue(md.trimEnd().contains("HIGH"))
    }

    @Test
    fun emptySessionNeverClaimsAMatch() {
        val md = ValidationReportGenerator.generate(DiagnosticSession("empty", 0))
        assertTrue(md.contains("NOT OBSERVED"))
        // An empty session must not print a MATCH row anywhere.
        assertFalse(md.lines().any { it.contains("| MATCH ") })
    }

    @Test
    fun unobservedMtuPrintsNotObserved() {
        val md = ValidationReportGenerator.generate(DiagnosticSession("x", 0))
        assertTrue(md.contains("MTU: NOT OBSERVED"))
    }
}
