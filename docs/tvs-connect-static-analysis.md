# Phase 1 — TVS Connect static analysis (v8.12.4)

Source: `tvs-connect-8-12-4.xapk` (base `com.tvsm.connect.apk`), decompiled with jadx 1.5.1
into `re/xapk/srcall` (local only, do not commit). All findings are **static analysis only**.
Nothing here has been verified against a real scooter yet.

Confidence: HIGH = read directly from code · MEDIUM = inferred from code paths · UNKNOWN = not determined.

## 1. Transport and GATT

| Item | Value | Source | Confidence |
|---|---|---|---|
| Transport | BLE GATT | `com.tvs.bike.core.bikes.NtorqBLEConnectionConfig` | HIGH (for that config) |
| Service UUID | `5456534D-5647-5341-5342-454E544F5251` (ASCII "TVSMVGSASBENTORQ") | same | HIGH |
| Write characteristic | `00005352-0000-1000-8000-00805f9b34fb` | same | HIGH |
| Read/notify characteristic | `00005354-0000-1000-8000-00805f9b34fb` | same | HIGH |
| Write type | `2` (WRITE_TYPE_DEFAULT, with response) by default; U577 code checks the characteristic's write type at runtime | `NtorqBLEConnectionConfig`, `BleEngine.writeCharacteristicToCentral` | HIGH |
| Alternate service UUID | `5456534d-5647-5555-5342-454e544f5251` | `BluetoothConstants` | HIGH that it exists, UNKNOWN which models use it |
| Advertised device name | UNKNOWN | — | UNKNOWN |
| Pairing / bonding | UNKNOWN | — | UNKNOWN |

The Jupiter, Apache and iQube configs use the same service/characteristic UUIDs.

## 2. Vehicle variants (enum `com.tvsm.connect.common.BikeType`)

NTORQ-related entries: `NTORQ_U812` (39), `NTORQ_N251_STANDARD_NEW` (43), `NTORQ_U458_Race_NEW` (44),
`NTORQ_U377_SUPER_SQUAD_NEW` (45), `U_577_BASE` (37), `U_577_PREMIUM` (38).

**Which code is the NTORQ 150 TFT: UNKNOWN.** U577 has dedicated "base"/"premium" navigation
pictogram tables and its own MTU constants, which would fit NTORQ 150 / NTORQ 150 TFT.
That is a MEDIUM-confidence guess and has not been verified. It has to be confirmed
on the real scooter, e.g. from the vehicle-type byte the cluster reports.

## 3. Frame structure (app → cluster)

| Field | Observation | Source | Confidence |
|---|---|---|---|
| Byte 0 | Start byte `0x5A` or `0x5B` | `FrameConstants.StartId` | HIGH |
| Byte 1 | Data ID | `FrameConstants.DataId`, `IncomingFrameIdentifier.identify()` | HIGH |
| Trailer | `0xFF` postfix on navigation frames | `BleNavigationSendData` | HIGH |
| Checksum | Optional `addChecksum()` before sending | `BleEngine.writeCharacteristicToCentral` | HIGH that it exists, algorithm not yet documented |
| **Payload obfuscation** | For most bike types, including `U_577_PREMIUM`, the frame is XORed with a static key built into the app before it is written | `BluetoothUtil.encryptData*`, `getKeyByteArray*` | HIGH |

## 4. Navigation messages (ICE cluster path)

Built by `BleNavigationSendData.byteArrayForIceBikeNavigation()` and sent by
`BleEngine.sendNavigationData()`: the second frame is written first, then a 400 ms sleep, then the first frame.

**Frame A — NAVIGATION_CONTROL** (`0x5A 0x4E …`)

| Offset | Field | Confidence |
|---|---|---|
| 0–1 | `5A 4E` | HIGH |
| 2–3 | Distance to next maneuver in metres, big-endian (`FF FF` if ≥ 32767) | HIGH |
| 4–5 | Remaining time in minutes, big-endian | HIGH |
| 6–8 | Total remaining distance in metres, 3 bytes | MEDIUM (BigInteger sign handling) |
| 9 | Cluster pictogram ID (mapped from a Mappls maneuver ID) | HIGH |
| 10 | 1 if the instruction is ≤ 17 chars, 2 if longer | HIGH |
| 11 | Navigation on/off flag (`00`/`FF` or `01`/`00` depending on vehicle type) | MEDIUM (decompiler errors in this method) |
| 12–18 | Zero bytes | MEDIUM |
| last | `FF` | HIGH |

**Frame B — NAVIGATION_DATA1** (`0x5B 0x4F …`): 20 bytes = `5B 4F` + instruction text in UTF-8
(up to 17 bytes, zero-padded) + `FF`. HIGH.

**Pictogram mapping:** `BleNavigationDataSourceImp.getPictogramMappingForNtorqU812`,
`getClusterPictogramMappingForU577PremiumMappls` and `getClusterPictogramIdForBaseU577` map
Mappls maneuver IDs to cluster pictogram IDs. The meaning of each cluster ID (left, right, …)
has to be confirmed on the TFT.

Road name (`5A 49`, `sendNavigationControl2`) appears only in the Apache code path. For NTORQ it is UNKNOWN.

## 5. Blocking issue

Every app → cluster frame for the likely NTORQ 150 variants is XOR-obfuscated with a key embedded
in the official app. Sending a frame the TFT accepts would mean taking that key out of the TVS app
and re-implementing the obfuscation. That conflicts with the project rule
"do not defeat encryption / reverse-engineer cryptographic protections".
**The key has deliberately not been extracted or recorded.**

## 6. Still UNKNOWN

Advertised name, bonding requirements, session/handshake sequence, checksum algorithm,
which bike types skip obfuscation, firmware-version differences, and which cluster IDs the NTORQ 150 TFT uses.
