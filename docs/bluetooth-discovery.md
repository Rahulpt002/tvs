# Bluetooth / BLE Discovery & GATT Specification

## 1. Device Identification

From static analysis of the official TVS Connect Android application (v8.12.4) and BLE protocol structures:

- **Transport Type**: Bluetooth Low Energy (BLE GATT).
- **Advertised Device Names**: Typically starts with or contains:
  - `NTORQ` (e.g. `NTORQ_XXXX`)
  - `TVS` or `TVSM`
- **Primary Service UUID**:
  `5456534D-5647-5341-5342-454E544F5251` (ASCII decode: `TVSMVGSASBENTORQ`)
- **Secondary / Alternative Service UUID**:
  `5456534D-5647-5555-5342-454E544F5251`

---

## 2. GATT Services & Characteristics

| UUID | Type | Properties | Role | Safety Status |
|---|---|---|---|---|
| `5456534D-5647-5341-5342-454E544F5251` | Primary Service | - | NTORQ Central Service | Whitelisted |
| `00005352-0000-1000-8000-00805F9B34FB` | Characteristic | WRITE, WRITE_NO_RESP | Phone ➔ Cluster writes (nav, time, caller) | **Whitelisted** |
| `00005354-0000-1000-8000-00805F9B34FB` | Characteristic | READ, NOTIFY, INDICATE | Cluster ➔ Phone notifications (telemetry) | **Whitelisted** (Read/Notify only) |
| `00002902-0000-1000-8000-00805F9B34FB` | Descriptor (CCCD) | READ, WRITE | Notification Enable Flag | Whitelisted |

**Note**: All other characteristics discovered during service enumeration are strictly ignored and protected by the write safety whitelist.

---

## 3. Connection & Subscription Sequence

1. **Scan**: Android `BluetoothLeScanner` searches for devices advertising the NTORQ service UUID or name prefix.
2. **GATT Connect**: `device.connectGatt(context, false, callback, TRANSPORT_LE)`
3. **MTU Negotiation**: Request MTU of `512` bytes upon successful connection.
4. **Service Discovery**: `gatt.discoverServices()`
5. **GATT Profile Verification**:
   - Verify primary service `5456534D-...` is present.
   - Verify write characteristic `00005352-...` is present.
   - Verify notify characteristic `00005354-...` is present.
6. **CCCD Subscription**:
   - Call `gatt.setCharacteristicNotification(notifyChar, true)`
   - Write `ENABLE_NOTIFICATION_VALUE` (`0x0001`) to descriptor `00002902-...`.
7. **Connection Maintenance**:
   - If disconnect event occurs, connection manager executes exponential backoff (2s, 4s, 8s, 16s, up to 30s) avoiding aggressive flooding.
