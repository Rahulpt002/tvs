# TVS Navigation Protocol Specification

## 1. Protocol Architecture

Navigation data is transmitted from the phone (Central) to the NTORQ TFT Instrument Cluster (Peripheral) on Characteristic `00005352-0000-1000-8000-00805F9B34FB`.

Each turn update is dispatched as a sequence of **two 20-byte frames**:
1. **Frame B (NAVIGATION_DATA1)**: Text instruction / road name
2. *Inter-frame delay (~400 ms)*
3. **Frame A (NAVIGATION_CONTROL)**: Turn direction, distance, ETA, and status flags

---

## 2. Frame A — NAVIGATION_CONTROL

- **Direction**: Central ➔ Peripheral (Phone ➔ TFT)
- **Characteristic**: `00005352-0000-1000-8000-00805F9B34FB`
- **Total Packet Length**: 20 bytes
- **Confidence**: HIGH

### Byte Layout:

| Byte Offset | Field Description | Type / Size | Example / Value | Confidence |
|---|---|---|---|---|
| `0` | Start / Header ID | `uint8` (1 byte) | `0x5A` (90) | HIGH |
| `1` | Data ID | `uint8` (1 byte) | `0x4E` (78) | HIGH |
| `2 - 3` | Distance to next maneuver (metres) | `uint16` (2 bytes, big-endian) | `0x00FA` (250m), saturated to `0xFFFF` if ≥ 32767 | HIGH |
| `4 - 5` | Remaining ETA (minutes) | `uint16` (2 bytes, big-endian) | `0x000C` (12 min) | HIGH |
| `6 - 8` | Total distance remaining (metres) | `uint24` (3 bytes, big-endian) | `0x001194` (4500m) | HIGH |
| `9` | Cluster Pictogram ID | `uint8` (1 byte) | `0x02` (Turn Left) | HIGH |
| `10` | Instruction Length Flag | `uint8` (1 byte) | `0x01` (≤ 17 chars), `0x02` (> 17 chars) | HIGH |
| `11` | Navigation On/Off Flag | `uint8` (1 byte) | `0x00` (Active), `0xFF` (Stopped) | HIGH |
| `12 - 18` | Reserved / Zero Padding | `byte[7]` (7 bytes) | `0x00 ... 0x00` | MEDIUM |
| `19` | Frame Trailer | `uint8` (1 byte) | `0xFF` | HIGH |

---

## 3. Frame B — NAVIGATION_DATA1

- **Direction**: Central ➔ Peripheral (Phone ➔ TFT)
- **Characteristic**: `00005352-0000-1000-8000-00805F9B34FB`
- **Total Packet Length**: 20 bytes
- **Confidence**: HIGH

### Byte Layout:

| Byte Offset | Field Description | Type / Size | Example / Value | Confidence |
|---|---|---|---|---|
| `0` | Start / Header ID | `uint8` (1 byte) | `0x5B` (91) | HIGH |
| `1` | Data ID | `uint8` (1 byte) | `0x4F` (79) | HIGH |
| `2 - 18` | Road name or primary instruction | UTF-8 String (up to 17 bytes, 0-padded) | `"MG Road\0\0\0..."` | HIGH |
| `19` | Frame Trailer | `uint8` (1 byte) | `0xFF` | HIGH |

---

## 4. Maneuver to Cluster Pictogram Mapping

The TVS Connect application maps MapmyIndia (Mappls) maneuvers to specific cluster pictogram IDs:

| Maneuver | Logical Name | Cluster Pictogram ID | Confidence |
|---|---|---|---|
| `STRAIGHT` | Continue straight | `1` | HIGH |
| `LEFT` | Turn left | `2` | HIGH |
| `RIGHT` | Turn right | `12` | HIGH |
| `SLIGHT_LEFT` | Keep / slight left | `10` | HIGH |
| `SLIGHT_RIGHT` | Keep / slight right | `11` | HIGH |
| `U_TURN` | U-turn | `22` | HIGH |
| `ROUNDABOUT` | Roundabout / Rotary | `9` | HIGH |
| `DESTINATION` | Arrived at destination | `62` | HIGH |
| `UNKNOWN` | No icon / unknown | `0` | HIGH |

---

## 5. Security & Protection Layer Documentation

- **Obfuscation Status**: In the official TVS Connect Android app, payload frames for several vehicle types are XOR-obfuscated with a static key prior to transmission.
- **Safety Interlock**: In accordance with user safety guidelines, **no attempt has been made to recover or bypass this key**. The application encapsulates all frame generation in `TvsProtectedTransport`, where physical transmission is safely interlocked pending verified supported communication behavior, while full diagnostic generation and offline simulation remain available.
