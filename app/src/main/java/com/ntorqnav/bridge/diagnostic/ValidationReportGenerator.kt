package com.ntorqnav.bridge.diagnostic

/**
 * Section 9 — generate `real-device-validation.md` from a recorded [DiagnosticSession].
 *
 * The report states only what was observed and compares it against the static-analysis model.
 * Unknowns are reported as UNKNOWN / NOT OBSERVED and never filled in with guesses.
 */
object ValidationReportGenerator {

    fun generate(session: DiagnosticSession): String {
        val comparisons = StaticAnalysisComparator.compare(session)
        val nav = FrameClassifier.analyzeNavigationActivity(session.notifications)
        val classifications = FrameClassifier.classifyNotifications(session.notifications)
        val overall = StaticAnalysisComparator.overallConfidence(comparisons)

        val device = session.device
        val writeChar = session.characteristics.firstOrNull { it.role == GattVerifier.ROLE_WRITE }
        val notifyChar = session.characteristics.firstOrNull { it.role == GattVerifier.ROLE_NOTIFY }
        val tvsService = session.services.firstOrNull { it.isExpectedTvsService }

        val sb = StringBuilder()

        sb.appendLine("# Real NTORQ 150 TFT — BLE Validation Report")
        sb.appendLine()
        sb.appendLine("Session: `${session.sessionId}` · schema v${session.schemaVersion} · generated from observed data only.")
        sb.appendLine()
        sb.appendLine("> This report records only naturally-exposed BLE behaviour. No frame was decrypted,")
        sb.appendLine("> no static key was extracted, and no protection layer was bypassed. Values that were")
        sb.appendLine("> not genuinely observed are reported as UNKNOWN / NOT OBSERVED.")
        sb.appendLine()

        // # Device
        sb.appendLine("# Device")
        sb.appendLine()
        sb.appendLine("- Name: ${device?.name ?: UNKNOWN}")
        sb.appendLine("- Address: ${device?.address ?: UNKNOWN}")
        sb.appendLine("- RSSI: ${device?.rssi?.let { "$it dBm" } ?: UNKNOWN}")
        sb.appendLine("- TVS service advertised: ${device?.tvsServiceAdvertised?.let { yesNo(it) } ?: UNKNOWN}")
        if (!device?.advertisedServiceUuids.isNullOrEmpty()) {
            sb.appendLine("- Advertised service UUIDs:")
            device!!.advertisedServiceUuids.forEach { sb.appendLine("  - `$it`") }
        }
        if (!device?.manufacturerData.isNullOrEmpty()) {
            sb.appendLine("- Manufacturer data:")
            device!!.manufacturerData.forEach { (id, payload) -> sb.appendLine("  - companyId `$id`: `$payload`") }
        }
        sb.appendLine()

        // # GATT
        sb.appendLine("# GATT")
        sb.appendLine()
        sb.appendLine("- Expected TVS service: ${presence(tvsService != null)}${tvsService?.let { " (`${it.uuid}`)" } ?: ""}")
        sb.appendLine("- Write characteristic (0x5352): ${presence(writeChar != null)}${propsSuffix(writeChar)}")
        sb.appendLine("- Notify characteristic (0x5354): ${presence(notifyChar != null)}${propsSuffix(notifyChar)}")
        sb.appendLine()
        if (session.services.isNotEmpty()) {
            sb.appendLine("Discovered services (${session.services.size}):")
            sb.appendLine()
            sb.appendLine("| Service UUID | Primary | Expected TVS |")
            sb.appendLine("|---|---|---|")
            session.services.forEach {
                sb.appendLine("| `${it.uuid}` | ${yesNo(it.isPrimary)} | ${yesNo(it.isExpectedTvsService)} |")
            }
            sb.appendLine()
        }
        if (session.characteristics.isNotEmpty()) {
            sb.appendLine("Discovered characteristics (${session.characteristics.size}):")
            sb.appendLine()
            sb.appendLine("| Characteristic UUID | Properties | Role |")
            sb.appendLine("|---|---|---|")
            session.characteristics.forEach {
                val props = it.properties.joinToString(", ").ifEmpty { "-" }
                sb.appendLine("| `${it.uuid}` | $props | ${it.role} |")
            }
            sb.appendLine()
        }

        // # Connection
        sb.appendLine("# Connection")
        sb.appendLine()
        sb.appendLine("- MTU: ${session.negotiatedMtu?.toString() ?: "NOT OBSERVED"}")
        sb.appendLine("- Bonding: ${session.bondingState.name}")
        sb.appendLine("- Connection sequence:")
        if (session.events.isEmpty()) {
            sb.appendLine("  - NOT OBSERVED")
        } else {
            session.events.forEach {
                sb.appendLine("  - +${relativeMs(it.timestampMs, session)} ms  ${it.type}  [${it.connectionState}]${detailSuffix(it.detail)}")
            }
        }
        sb.appendLine()

        // # Device identity
        sb.appendLine("# Device identity")
        sb.appendLine()
        sb.appendLine("- Vehicle type: ${session.identity.vehicleType}")
        sb.appendLine("- Firmware version: ${session.identity.firmwareVersion}")
        sb.appendLine("- Cluster model: ${session.identity.clusterModel}")
        sb.appendLine("- Protocol variant: ${session.identity.protocolVariant}")
        sb.appendLine()
        sb.appendLine("_Identity is only ever set from naturally-exposed session data and is never inferred")
        sb.appendLine("from the presence of the service UUID._")
        sb.appendLine()

        // # Navigation
        sb.appendLine("# Navigation")
        sb.appendLine()
        sb.appendLine("- Navigation traffic detected: ${if (nav.detected) "Observed" else "Unknown / NOT OBSERVED"}")
        sb.appendLine("- Notifications recorded: ${nav.packetCount}")
        sb.appendLine("- Burst clusters: ${nav.burstCount}")
        sb.appendLine("- Dominant packet lengths: ${nav.dominantLengths.joinToString(", ").ifEmpty { "-" }}")
        sb.appendLine("- ~400 ms Frame B→A sequencing: ${if (nav.approx400msSequencingObserved) "Observed" else "NOT OBSERVED"}")
        sb.appendLine()
        sb.appendLine("> ${nav.notes}")
        sb.appendLine()

        // # Frame classification
        sb.appendLine("# Frame classification")
        sb.appendLine()
        if (classifications.isEmpty()) {
            sb.appendLine("No frames to classify.")
        } else {
            val byCategory = classifications.groupingBy { it.category }.eachCount()
            sb.appendLine("| Category | Count |")
            sb.appendLine("|---|---|")
            byCategory.entries.sortedByDescending { it.value }.forEach {
                sb.appendLine("| ${it.key} | ${it.value} |")
            }
            sb.appendLine()
            sb.appendLine("Classification uses observable properties only (length, timing, direction,")
            sb.appendLine("characteristic, frequency, sequence). Frames with no recognizable plaintext")
            sb.appendLine("structure are reported as PROTECTED_UNCLASSIFIED, not guessed at.")
        }
        sb.appendLine()

        // # Static analysis comparison
        sb.appendLine("# Static analysis comparison")
        sb.appendLine()
        sb.appendLine("| Prediction | Expected | Observed | Status | Confidence |")
        sb.appendLine("|---|---|---|---|---|")
        comparisons.forEach {
            sb.appendLine("| ${it.item} | ${it.expected} | ${it.observed} | ${it.status} | ${it.confidence} |")
        }
        sb.appendLine()

        // # Unknowns
        sb.appendLine("# Unknowns")
        sb.appendLine()
        val unknowns = comparisons.filter { it.status == ComparisonStatus.NOT_OBSERVED }
        if (unknowns.isEmpty()) {
            sb.appendLine("- None outstanding in the compared set.")
        } else {
            unknowns.forEach { sb.appendLine("- ${it.item}: NOT OBSERVED") }
        }
        if (session.identity == DeviceIdentity.UNKNOWN_IDENTITY) {
            sb.appendLine("- Vehicle type, firmware, cluster model, protocol variant: UNKNOWN")
        }
        sb.appendLine()

        // # Confidence
        sb.appendLine("# Confidence")
        sb.appendLine()
        sb.appendLine(overall)
        sb.appendLine()
        sb.appendLine("_Confidence reflects how many core GATT-profile predictions (service, write, notify)")
        sb.appendLine("were confirmed against the real device in this session._")

        return sb.toString()
    }

    private fun yesNo(b: Boolean) = if (b) "Yes" else "No"
    private fun presence(b: Boolean) = if (b) "PRESENT" else "NOT OBSERVED"
    private fun detailSuffix(detail: String) = if (detail.isBlank()) "" else "  — $detail"
    private fun propsSuffix(c: DiagnosticCharacteristicInfo?): String =
        if (c == null) "" else " [${c.properties.joinToString(", ").ifEmpty { "-" }}]"

    private fun relativeMs(ts: Long, session: DiagnosticSession): Long {
        val base = session.timestamps.minOrNull() ?: session.createdAtMs
        return (ts - base).coerceAtLeast(0)
    }
}
