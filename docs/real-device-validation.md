<!--
  TEMPLATE — this file is the exact output of ValidationReportGenerator for an EMPTY session.
  It is committed as the Phase 2 deliverable shape. Every field is NOT OBSERVED / UNKNOWN
  because no real NTORQ 150 capture has been recorded yet.

  To produce the real report: run the app, open the "Validate" tab, Start Recording, perform
  the on-vehicle procedure in docs/phase2-real-device-validation.md, Stop, then Export. The app
  writes real-device-validation-<sessionId>.md to the app external files dir; replace this file
  with that output.
-->

# Real NTORQ 150 TFT — BLE Validation Report

Session: `TEMPLATE` · schema v1 · generated from observed data only.

> This report records only naturally-exposed BLE behaviour. No frame was decrypted,
> no static key was extracted, and no protection layer was bypassed. Values that were
> not genuinely observed are reported as UNKNOWN / NOT OBSERVED.

# Device

- Name: UNKNOWN
- Address: UNKNOWN
- RSSI: UNKNOWN
- TVS service advertised: UNKNOWN

# GATT

- Expected TVS service: NOT OBSERVED
- Write characteristic (0x5352): NOT OBSERVED
- Notify characteristic (0x5354): NOT OBSERVED

# Connection

- MTU: NOT OBSERVED
- Bonding: UNKNOWN
- Connection sequence:
  - NOT OBSERVED

# Device identity

- Vehicle type: UNKNOWN
- Firmware version: UNKNOWN
- Cluster model: UNKNOWN
- Protocol variant: UNKNOWN

_Identity is only ever set from naturally-exposed session data and is never inferred
from the presence of the service UUID._

# Navigation

- Navigation traffic detected: Unknown / NOT OBSERVED
- Notifications recorded: 0
- Burst clusters: 0
- Dominant packet lengths: -
- ~400 ms Frame B→A sequencing: NOT OBSERVED

> No notifications recorded.

# Frame classification

No frames to classify.

# Static analysis comparison

| Prediction | Expected | Observed | Status | Confidence |
|---|---|---|---|---|
| Primary service | 5456534d-5647-5341-5342-454e544f5251 | NOT OBSERVED | NOT_OBSERVED | HIGH |
| Alternate service | 5456534d-5647-5555-5342-454e544f5251 | NOT OBSERVED | NOT_OBSERVED | MEDIUM |
| Write characteristic (0x5352) | 00005352-0000-1000-8000-00805f9b34fb | NOT OBSERVED | NOT_OBSERVED | HIGH |
| Notify characteristic (0x5354) | 00005354-0000-1000-8000-00805f9b34fb | NOT OBSERVED | NOT_OBSERVED | HIGH |
| Negotiated MTU | App requests 512 (actual value unknown from static analysis) | NOT OBSERVED | NOT_OBSERVED | HIGH |
| Bonding requirement | UNKNOWN (not determined by static analysis) | UNKNOWN | NOT_OBSERVED | MEDIUM |
| ~400 ms Frame B→A sequencing | Frame B, ~400 ms, Frame A (phone→cluster writes) | NOT OBSERVED (writes not visible to passive observer) | NOT_OBSERVED | LOW |
| Vehicle type / cluster model | U577 base/premium is a MEDIUM-confidence guess (unverified) | UNKNOWN | NOT_OBSERVED | MEDIUM |

# Unknowns

- Primary service: NOT OBSERVED
- Alternate service: NOT OBSERVED
- Write characteristic (0x5352): NOT OBSERVED
- Notify characteristic (0x5354): NOT OBSERVED
- Negotiated MTU: NOT OBSERVED
- Bonding requirement: NOT OBSERVED
- ~400 ms Frame B→A sequencing: NOT OBSERVED
- Vehicle type / cluster model: NOT OBSERVED
- Vehicle type, firmware, cluster model, protocol variant: UNKNOWN

# Confidence

LOW

_Confidence reflects how many core GATT-profile predictions (service, write, notify)
were confirmed against the real device in this session._
