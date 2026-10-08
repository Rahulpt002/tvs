# System Architecture — NTORQ 150 TFT Navigation Bridge

## 1. High-Level Architecture Overview

The system bridges turn-by-turn navigation data from Android phone navigation providers (e.g. Google Maps notifications or interactive test providers) to the TVS NTORQ 150 TFT instrument cluster **without modifying, rooting, flashing, or compromising the scooter's TFT firmware**.

```text
[ Google Maps ]
      │ (NotificationListenerService)
      ▼
[ GoogleMapsNotificationProvider ]  ──or──  [ FakeNavigationProvider ]
      │                                                │
      └──────────────────────┬─────────────────────────┘
                             │ NavigationUpdate Flow
                             ▼
                    [ NavigationBridge ]
                             │
                             ▼
                  [ TvsNavigationProtocol ]
                             │
                             ▼
                    [ TvsPacketEncoder ]
                      (Frame A & Frame B)
                             │
                             ▼
                 [ TvsProtectedTransport ]
                             │
               ┌─────────────┴─────────────┐
               ▼                           ▼
    [ Diagnostic Safety Interlock ]  [ Simulated Transport ]
               │                           │
               ▼                           ▼
     [ AndroidBleTransport ]      [ TvsProtocolSimulator ]
               │
               ▼
       [ NTORQ 150 TFT ]
```

---

## 2. Protected Transport Layer & Safety Interlock

Per the project requirements, **no attempt is made to extract, recover, reproduce, or bypass the static vehicle key**. 

Instead, the architecture decouples protocol composition from transport security through the **`TvsProtectedTransport`** interface:

```kotlin
interface TvsProtectedTransport {
    val transportName: String
    val isSafetyInterlockEngaged: Boolean

    suspend fun transmitNavigationFrames(
        frameA: ByteArray,
        frameB: ByteArray,
        delayMs: Long
    ): ProtectedTransmissionResult
}
```

### Safety Interlock Guarantees:
1. **Characteristic Whitelist**: Writes are strictly checked against `ALLOWED_WRITE_CHARACTERISTICS` (`00005352-0000-1000-8000-00805f9b34fb`). Any attempt to address unverified characteristics is rejected immediately.
2. **Physical Transmission Interlock**: Handcrafted or unverified frames are **never transmitted to the live vehicle** without established supported communication. Frames are logged diagnostically with full byte dumps, while live BLE writes remain safely gated.
3. **Simulation Mode**: Full offline simulation is supported via `TvsProtocolSimulator` and `FakeBluetoothTransport`, allowing end-to-end testing without vehicle contact.

---

## 3. Navigation State Machine

The bridge coordinates lifecycle and hardware connectivity using a deterministic state machine:

```text
       [ IDLE ]
          │
          │ connect()
          ▼
    [ CONNECTING ]
          │
          │ GATT discovered & validated
          ▼
       [ READY ]
          │
          │ startBridge()
          ▼
[ NAVIGATION_STARTING ]
          │
          │ protocol initialized
          ▼
    [ NAVIGATING ] ◄───┐
          │            │ Next instruction
          │ (update)   │
          ▼            │
[ NAVIGATION_UPDATING ]─┘
          │
          │ stopBridge() or route completed
          ▼
[ NAVIGATION_FINISHED ]
          │
          ▼
       [ READY ]
```

### Fault Handling:
- **BLE Disconnect**: If Bluetooth disconnects during active navigation, the bridge transitions to `ERROR`, stops transmission safely, and triggers exponential backoff reconnection (2s, 4s, 8s, 16s, up to 30s).
- **Notification Removed**: When Google Maps navigation ends, the provider emits route completion, sending a clean stop frame before idling.
- **Screen Locked / Background**: Services run with appropriate Android foreground/listener lifecycles to maintain connection.
