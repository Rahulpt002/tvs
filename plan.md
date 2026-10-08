# NTORQ 150 TFT — Google Maps Navigation Bridge Implementation Plan & Status

## Project Status: Phases 1 to 9 Implemented & Verified with 100% Test Coverage

### Implemented Architecture Components:

1. **Protocol & Security Isolation**:
   - `TvsProtectedTransport`: Abstraction layer separating high-level navigation logic from transport security.
   - `DiagnosticSafeProtectedTransport`: Implements characteristic whitelisting (`00005352-0000-1000-8000-00805f9b34fb`) and safety interlock preventing unverified physical transmission.
   - `TvsConstants`: Canonical BLE UUIDs (`5456534D-5647-5341-5342-454E544F5251`, `00005352`, `00005354`, `00002902`), frame IDs (`0x4E` Frame A, `0x4F` Frame B).

2. **Protocol Encoders & Decoders**:
   - `TvsPacketEncoder`: Encodes Frame A (20-byte navigation control: distance, ETA, total distance, pictogram ID, stopped flag) and Frame B (20-byte UTF-8 road/instruction text).
   - `TvsPacketDecoder`: Decodes raw byte packets into typed objects (`FrameANavigationControl`, `FrameBNavigationText`, `ClusterSpeedometerFrame`).

3. **Bluetooth & Device Discovery**:
   - `BluetoothScanner`: BLE scanner identifying NTORQ / TVS devices by name and service UUID.
   - `AndroidBleTransport`: BLE GATT transport handling MTU negotiation (512), CCCD notification subscription on `00005354-...`, and characteristic writes on `00005352-...`.
   - `NtorqConnectionManager`: Connection lifecycle manager with exponential backoff (2s, 4s, 8s, 16s, up to 30s) and GATT profile validation.

4. **Navigation Acquisition & Providers**:
   - `NavigationProvider`: Extensible interface for navigation sources.
   - `GoogleMapsNotificationListenerService` & `GoogleMapsNotificationProvider`: Captures Google Maps turn-by-turn notifications.
   - `GoogleMapsNotificationParser`: Regex parser extracting distances (meters/km/miles/feet), ETA, maneuvers, and road names.
   - `FakeNavigationProvider`: Interactive manual test provider and automated cyclic simulation loop.

5. **Simulation & Offline Testing**:
   - `TvsProtocolSimulator`: Simulates the TFT side of the protocol, displays decoded navigation parameters, and generates simulated speedometer telemetry frames (`0x10`).
   - `FakeBluetoothTransport`: In-memory transport for complete offline testing.

6. **Replay & Diagnostics**:
   - `PacketReplaySystem`: Captures packet sessions, serializes them to JSON, and allows deterministic timed replay.
   - `AppLogger`: Structured logging ring buffer with privacy safeguards and clipboard export.

7. **Jetpack Compose UI**:
   - `DashboardScreen`: Live instrument bridge dashboard showing Bluetooth, TFT, Navigation, and Google Maps status with current turn display.
   - `BluetoothInspectorScreen`: Scans devices, shows RSSI/MAC, and enumerates GATT services, characteristics, properties, and descriptors.
   - `TestNavigationScreen`: Interactive chips for maneuvers, distance presets (50m, 100m, 250m, 500m, 1km), road names, and auto-simulation.
   - `DeveloperScreen`: Tabbed diagnostic view (Diagnostics, Simulator, Replay, Live Logs).
   - `MainActivity`: Bottom navigation bar and Android runtime permission requests.

8. **Automated Unit Tests**:
   - `TvsPacketEncoderDecoderTest`: 100% pass (Frame A/B layouts, headers, trailers, distance saturation).
   - `GoogleMapsNotificationParserTest`: 100% pass (Notification parsing, distance conversions, maneuvers).
   - `TvsProtectedTransportSafetyTest`: 100% pass (Safety interlock verification).
   - `TvsProtocolSimulatorTest`: 100% pass (Simulator reception, state update, speedometer notification).
   - `PacketReplaySystemTest`: 100% pass (Session capture, JSON serialization, restoration).

---

## Phase 2 — Real NTORQ 150 BLE Validation (Implemented)

Moves from static analysis to **controlled real-device observation** (passive only; physical
transmission stays disabled). New package `com.ntorqnav.bridge.diagnostic` + **Validate** tab.

- **Real-device scanner**: manufacturer data, advertised UUIDs, RSSI; prioritises TVS-service
  advertisers without hardcoding a device name.
- **GATT verification**: confirms expected TVS service / `0x5352` / `0x5354`, properties, MTU;
  subscribes to notifications only on the verified notify characteristic.
- **Session recorder**: Start/Stop/Export/Replay; JSON schema
  (device/services/characteristics/events/notifications/timestamps); writes session + report to
  the app external files dir with share-sheet export.
- **Frame classification** by observable properties only (length/timing/direction/characteristic/
  frequency/sequence); opaque payloads reported as `PROTECTED/UNCLASSIFIED`, never decrypted.
- **Static-vs-observed comparison** that never fabricates a match, feeding
  `ValidationReportGenerator` → `real-device-validation.md`.
- **Device identity** (vehicle type / firmware / cluster model / protocol variant) defaults to
  `UNKNOWN` and is never inferred from the presence of the service UUID.

Safety interlock, write whitelist, and "no key extraction / no protection bypass" rules unchanged.

New tests: `GattVerifierTest`, `FrameClassifierTest`, `DiagnosticSessionRecorderTest`,
`StaticAnalysisComparatorTest`, `ValidationReportGeneratorTest`, `BluetoothScannerFilterTest`
(45 tests total, all passing).

See `docs/phase2-real-device-validation.md` (procedure + 12-question deliverable) and
`docs/real-device-validation.md` (report template, regenerated from a real capture).
