package com.ntorqnav.bridge.tvs

import java.util.UUID

object TvsConstants {
    // Primary NTORQ BLE Service UUID ("TVSMVGSASBENTORQ")
    val SERVICE_UUID_NTORQ: UUID = UUID.fromString("5456534D-5647-5341-5342-454E544F5251")

    // Secondary / Alternative Service UUID
    val SERVICE_UUID_NTORQ_ALT: UUID = UUID.fromString("5456534D-5647-5555-5342-454E544F5251")

    // GATT Characteristics
    val CHAR_WRITE_UUID: UUID = UUID.fromString("00005352-0000-1000-8000-00805f9b34fb")
    val CHAR_NOTIFY_READ_UUID: UUID = UUID.fromString("00005354-0000-1000-8000-00805f9b34fb")

    // Standard BLE Client Characteristic Configuration Descriptor (for notifications)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Strict safety whitelist of writable characteristics
    val ALLOWED_WRITE_CHARACTERISTICS: Set<UUID> = setOf(CHAR_WRITE_UUID)

    // Protocol Frame Delimiters & IDs
    const val START_BYTE_5A: Byte = 0x5A
    const val START_BYTE_5B: Byte = 0x5B
    const val POSTFIX_TRAILER: Byte = 0xFF.toByte()

    const val DATA_ID_NAVIGATION_CONTROL: Byte = 0x4E // 78: Frame A
    const val DATA_ID_NAVIGATION_DATA1: Byte = 0x4F   // 79: Frame B
    const val DATA_ID_SPEEDOMETER_1: Byte = 0x10      // 16
    const val DATA_ID_SPEEDOMETER_2: Byte = 0x11      // 17
    const val DATA_ID_SPEEDOMETER_3: Byte = 0x19      // 25
    const val DATA_ID_SPEEDOMETER_4: Byte = 0x18      // 24
    const val DATA_ID_CALIBRATION_RESP: Byte = 0x37   // 55
    const val DATA_ID_SPEEDO_ACK: Byte = 0x54         // 84

    // Vehicle Type Codes
    const val VEHICLE_TYPE_U577_PREMIUM = "38"
    const val VEHICLE_TYPE_U577_BASE = "37"
    const val VEHICLE_TYPE_NTORQ_U812 = "39"
    const val VEHICLE_TYPE_RAIDER = "29"

    // Default Inter-Frame Delay (ms) observed in official TVS Connect app
    const val INTER_FRAME_DELAY_MS: Long = 400L
}
