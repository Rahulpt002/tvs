package com.ntorqnav.bridge.tvs

import java.nio.charset.StandardCharsets

sealed interface DecodedTvsPacket {
    data class FrameANavigationControl(
        val distanceMeters: Int,
        val etaMinutes: Int,
        val totalDistanceMeters: Int,
        val pictogramId: Int,
        val isLongInstruction: Boolean,
        val isStopped: Boolean,
        val rawBytes: ByteArray
    ) : DecodedTvsPacket {
        override fun equals(other: Any?): Boolean =
            this === other || (other is FrameANavigationControl && rawBytes.contentEquals(other.rawBytes))
        override fun hashCode(): Int = rawBytes.contentHashCode()
    }

    data class FrameBNavigationText(
        val text: String,
        val rawBytes: ByteArray
    ) : DecodedTvsPacket {
        override fun equals(other: Any?): Boolean =
            this === other || (other is FrameBNavigationText && rawBytes.contentEquals(other.rawBytes))
        override fun hashCode(): Int = rawBytes.contentHashCode()
    }

    data class ClusterSpeedometerFrame(
        val frameId: Byte,
        val rawBytes: ByteArray
    ) : DecodedTvsPacket {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ClusterSpeedometerFrame && rawBytes.contentEquals(other.rawBytes))
        override fun hashCode(): Int = rawBytes.contentHashCode()
    }

    data class UnknownPacket(
        val header: Byte?,
        val dataId: Byte?,
        val rawBytes: ByteArray
    ) : DecodedTvsPacket {
        override fun equals(other: Any?): Boolean =
            this === other || (other is UnknownPacket && rawBytes.contentEquals(other.rawBytes))
        override fun hashCode(): Int = rawBytes.contentHashCode()
    }
}

object TvsPacketDecoder {

    fun decode(bytes: ByteArray): DecodedTvsPacket {
        if (bytes.size < 2) {
            return DecodedTvsPacket.UnknownPacket(null, null, bytes)
        }

        val header = bytes[0]
        val dataId = bytes[1]

        return when {
            header == TvsConstants.START_BYTE_5A && dataId == TvsConstants.DATA_ID_NAVIGATION_CONTROL -> {
                decodeFrameA(bytes)
            }
            header == TvsConstants.START_BYTE_5B && dataId == TvsConstants.DATA_ID_NAVIGATION_DATA1 -> {
                decodeFrameB(bytes)
            }
            header == TvsConstants.START_BYTE_5A && (dataId == TvsConstants.DATA_ID_SPEEDOMETER_1 ||
                    dataId == TvsConstants.DATA_ID_SPEEDOMETER_2 ||
                    dataId == TvsConstants.DATA_ID_SPEEDOMETER_3 ||
                    dataId == TvsConstants.DATA_ID_SPEEDOMETER_4) -> {
                DecodedTvsPacket.ClusterSpeedometerFrame(dataId, bytes)
            }
            else -> DecodedTvsPacket.UnknownPacket(header, dataId, bytes)
        }
    }

    private fun decodeFrameA(bytes: ByteArray): DecodedTvsPacket {
        if (bytes.size < 12) return DecodedTvsPacket.UnknownPacket(bytes[0], bytes[1], bytes)

        val dist = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val eta = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        val totalDist = ((bytes[6].toInt() and 0xFF) shl 16) or
                ((bytes[7].toInt() and 0xFF) shl 8) or
                (bytes[8].toInt() and 0xFF)
        val picto = bytes[9].toInt() and 0xFF
        val isLong = bytes[10].toInt() == 2
        val isStopped = bytes[11].toInt() == -1 || bytes[11].toInt() == 0xFF

        return DecodedTvsPacket.FrameANavigationControl(
            distanceMeters = dist,
            etaMinutes = eta,
            totalDistanceMeters = totalDist,
            pictogramId = picto,
            isLongInstruction = isLong,
            isStopped = isStopped,
            rawBytes = bytes
        )
    }

    private fun decodeFrameB(bytes: ByteArray): DecodedTvsPacket {
        if (bytes.size < 3) return DecodedTvsPacket.UnknownPacket(bytes[0], bytes[1], bytes)

        val end = (bytes.size - 1).coerceAtMost(19) // exclude trailer if 20 bytes
        var nullIndex = end
        for (i in 2 until end) {
            if (bytes[i] == 0.toByte()) {
                nullIndex = i
                break
            }
        }
        val len = (nullIndex - 2).coerceAtLeast(0)
        val text = String(bytes, 2, len, StandardCharsets.UTF_8).trim()

        return DecodedTvsPacket.FrameBNavigationText(
            text = text,
            rawBytes = bytes
        )
    }

    fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(separator = " ") { "%02X".format(it) }
}
