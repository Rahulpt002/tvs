# Testing & Verification Strategy

## 1. Testing Modes

The application supports multiple testing layers to validate navigation behavior without requiring continuous access to the physical scooter:

```text
┌────────────────────────────────────────────────────────┐
│                   1. OFFLINE SIMULATION                │
│ FakeNavigationProvider ➔ Protocol ➔ Simulator Display │
└────────────────────────────────────────────────────────┘
                           │
┌────────────────────────────────────────────────────────┐
│                   2. INTERACTIVE UI TEST               │
│ Manual Maneuver/Distance chips ➔ Immediate Packet TX   │
└────────────────────────────────────────────────────────┘
                           │
┌────────────────────────────────────────────────────────┐
│                   3. CAPTURE & REPLAY                  │
│ Record live/synthetic session ➔ Save JSON ➔ Playback   │
└────────────────────────────────────────────────────────┘
                           │
┌────────────────────────────────────────────────────────┐
│                   4. AUTOMATED UNIT TESTS              │
│ JUnit test suite for encoders, decoders, and parsers   │
└────────────────────────────────────────────────────────┘
```

---

## 2. Interactive Testing Screen

The app contains a dedicated **Test Navigation Mode** screen featuring:
- **Maneuver Buttons**: `STRAIGHT`, `LEFT`, `RIGHT`, `SLIGHT_LEFT`, `SLIGHT_RIGHT`, `U_TURN`, `ROUNDABOUT`, `DESTINATION`.
- **Distance Controls**: `50m`, `100m`, `250m`, `500m`, `1km`.
- **Road Presets**: `"MG Road"`, `"Beach Road"`, `"NH 66"`, plus custom text input.
- **Auto-Cycle Simulation**: Automatically cycles through maneuvers every few seconds to test display transitions.

---

## 3. Automated Test Suite

Run all automated unit tests via Gradle:
```bash
./gradlew test
```

### Verified Test Classes:
- `TvsPacketEncoderDecoderTest`: Validates 20-byte Frame A and Frame B layouts, headers, trailers, distance saturation at 32767m, and round-trip decoding.
- `GoogleMapsNotificationParserTest`: Tests real-world notification titles, metric and imperial distances, ETA formats, and maneuver extraction.
- `TvsProtectedTransportSafetyTest`: Validates that the safety interlock properly gates physical transmission by default.
- `TvsProtocolSimulatorTest`: Confirms simulator state updates when central sends navigation packets and tests mock speedometer emission.
- `PacketReplaySystemTest`: Tests recording, JSON serialization, and deterministic restoration of packet sessions.
