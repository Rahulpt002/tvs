# Phase 2 — Real NTORQ 150 BLE Validation

Moves the project from **static analysis** (Phase 1) to **controlled real-device observation**.
The goal of this phase is *not* to transmit handcrafted navigation packets. The goal is to
document exactly how the real NTORQ 150 TFT behaves during a normal connection, and to compare
that behaviour against the Phase 1 static-analysis model.

Physical transmission of handcrafted frames remains disabled (the Phase 1 safety interlock is
unchanged). This phase only **observes**.

---

## 1. What was built

All new code lives in `com.ntorqnav.bridge.diagnostic` plus a new **Validate** tab
(`ui/DiagnosticScreen.kt`). It is fully passive: it never writes to unknown characteristics,
never decrypts a frame, and never extracts a key.

| Component | File | Spec section |
|---|---|---|
| Session data model (device/services/characteristics/events/notifications/timestamps) | `diagnostic/DiagnosticSession.kt` | 5 |
| GATT verifier — confirms service / 0x5352 / 0x5354 presence + properties | `diagnostic/GattVerifier.kt` | 2 |
| Frame classifier — observable-property classification, PROTECTED/UNCLASSIFIED | `diagnostic/FrameClassifier.kt` | 7, 8 |
| Static-vs-observed comparator — never fabricates a match | `diagnostic/StaticAnalysisComparator.kt` | 6 |
| Report generator — produces `real-device-validation.md` | `diagnostic/ValidationReportGenerator.kt` | 9 |
| Session recorder — Start/Stop/Export/Replay | `diagnostic/DiagnosticSessionRecorder.kt` | 4, 5 |
| File exporter — writes JSON + report, share sheet | `diagnostic/DiagnosticExporter.kt` | 5 |
| Real-device scanner + GATT + recorder UI | `ui/DiagnosticScreen.kt` | 1, 2, 4 |

Supporting BLE changes (non-breaking):
- `BluetoothScanner` / `DiscoveredBluetoothDevice`: capture manufacturer data and the advertised
  TVS-service flag; prioritise (never hardcode a name) devices advertising the TVS service UUID.
- `AndroidBleTransport`: expose the negotiated MTU (`onMtuChanged`) and per-characteristic raw
  notifications (`onNotification`).
- `NtorqConnectionManager`: expose `negotiatedMtu`, a `rawNotifications` flow, and a read-only
  `readBondState()` (never initiates bonding).

---

## 2. On-vehicle procedure (hardware)

Prerequisites: an Android 10+ phone with the diagnostic app installed, Bluetooth + Location (or
Nearby devices) permissions granted, and a real NTORQ 150 within range.

### A. Observe our own connection

1. Open the app → **Validate** tab.
2. Tap scan. Devices advertising the TVS service UUID are shown first and tagged `TVS SVC`;
   name-based candidates are tagged `CANDIDATE`. Note name, RSSI, advertised UUIDs, manufacturer data.
3. **Start Recording.**
4. Select the NTORQ device → **Connect & Verify.**
5. The GATT Verification card fills in: expected TVS service, 0x5352, 0x5354, negotiated MTU,
   connection state. Subscription happens only on the verified notify characteristic (0x5354).
6. Let it sit; raw notifications stream into the live log, each classified by observable properties.
7. **Stop** → **Export.** The app writes `rtval_<ts>.json` and `real-device-validation-<ts>.md`
   to `Android/data/com.ntorqnav.bridge/files/diagnostic-sessions/` and opens the share sheet.

### B. Observe the official TVS Connect navigation (passive)

1. Close/disconnect our app from the NTORQ (leave the app installed).
2. Open official **TVS Connect**, connect to the NTORQ normally, start navigation via the
   official supported flow.
3. Where the platform allows a second observer, return to our app and record connection metadata
   and any notifications visible without interfering with the official connection.

> **Honest limitation.** A third-party app observes **RX notifications on 0x5354** only. The
> official app's phone→cluster **writes on 0x5352** (where the predicted 400 ms Frame B→A cadence
> lives) are not over-the-air visible to another app on the same phone. A `NOT OBSERVED` result
> for the 400 ms cadence therefore does **not** disprove the static model — it is simply outside
> what passive observation can see. True visibility of the official writes needs an external BLE
> sniffer (e.g. an nRF sniffer), which is out of scope for this app.

---

## 3. Deliverable — the 12 questions

Status as of this phase. Values marked **PENDING CAPTURE** become real once the procedure above
has been run and `docs/real-device-validation.md` is regenerated from the exported session.

| # | Question | Answer |
|---|---|---|
| 1 | Can the phone discover the NTORQ 150 TFT? | PENDING CAPTURE (scanner + filtering implemented) |
| 2 | What device name does it advertise? | PENDING CAPTURE (not hardcoded; recorded as-is) |
| 3 | Does it expose the expected TVS service? | PENDING CAPTURE (verifier implemented) |
| 4 | Are 0x5352 and 0x5354 present? | PENDING CAPTURE (verifier implemented) |
| 5 | What MTU does it negotiate? | PENDING CAPTURE (MTU capture implemented) |
| 6 | Is bonding required? | PENDING CAPTURE (read-only bond state implemented) |
| 7 | What is the connection sequence? | PENDING CAPTURE (event timeline recorded) |
| 8 | Can notifications be observed? | PENDING CAPTURE (notify-only subscription implemented) |
| 9 | Can navigation traffic be correlated with official navigation? | PENDING CAPTURE (bounded by the passive-observer limitation above) |
| 10 | Which static-analysis assumptions are confirmed? | PENDING CAPTURE (comparator + report implemented) |
| 11 | Which assumptions are disproven? | PENDING CAPTURE |
| 12 | What remains unknown? | Vehicle type, firmware, cluster model, protocol variant, checksum algorithm (never inferred) |

Success criterion for Phase 2: **real NTORQ 150 TFT BLE behaviour is experimentally documented
and compared against the static-analysis model.** The tooling and the report pipeline are complete
and tested; the final values are filled in by running one real capture.

---

## 4. Safety (unchanged, still enforced)

No TFT firmware modification · no rooting · no flashing · no ECU communication · no engine /
throttle / brake / immobilizer interaction · no diagnostic commands · no arbitrary characteristic
writes (whitelist enforced) · no extraction or recovery of protected keys · no bypassing the TVS
protection layer. Physical transmission stays disabled; this phase observes only.

---

## 5. Tests

New automated tests (run with `./gradlew test`):

- `diagnostic/GattVerifierTest` — service + characteristic detection, role tagging, honest absence.
- `diagnostic/FrameClassifierTest` — plaintext vs PROTECTED/UNCLASSIFIED, 400 ms + burst detection.
- `diagnostic/DiagnosticSessionRecorderTest` — recording, serialization round-trip, replay, identity defaults.
- `diagnostic/StaticAnalysisComparatorTest` — match/not-observed, never fabricates a match.
- `diagnostic/ValidationReportGeneratorTest` — required sections, no fabricated match, confidence.
- `bluetooth/BluetoothScannerFilterTest` — device filtering / prioritisation without name hardcoding.
