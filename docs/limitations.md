# Known Constraints & Limitations

## 1. Safety & Vehicle Integrity Boundaries

This project strictly adheres to vehicle safety standards:
- **No Firmware Modifications**: The TFT instrument cluster firmware is untouched.
- **No Vehicle Control Operations**: The application contains no routines for engine control, throttle, brakes, ECU diagnostics, immobilizer, or battery management.
- **Protected Transport Isolation**: Handcrafted frames are never transmitted blindly to the vehicle without verified supported behavior.

---

## 2. Technical Limitations

1. **Android Notification Access Requirement**:
   - Google Maps navigation data acquisition requires the user to grant "Device & app notifications" permission in Android Settings. Without this permission, the app automatically falls back to manual and simulated navigation modes.
2. **Notification String Formats Across Android / Maps Versions**:
   - Google Maps may alter notification text structure across app updates or locales. The `GoogleMapsNotificationParser` incorporates defensive regular expressions, but non-Latin localization may require extending keyword dictionaries.
3. **Bluetooth Classic vs BLE**:
   - Static analysis confirms BLE GATT operation for NTORQ navigation display (`5456534D-5647-5341-5342-454E544F5251`), while classic audio/calling uses Bluetooth Classic HFP/AVRCP profiles. This application focuses specifically on the BLE navigation profile.
4. **Transport Protection Layer**:
   - In accordance with safety constraints, static obfuscation keys embedded in the official app were neither extracted nor bypassed. Physical writes remain gated by `DiagnosticSafeProtectedTransport` until supported behavior is verified.
