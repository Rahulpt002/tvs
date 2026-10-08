package com.ntorqnav.bridge.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class Maneuver {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    U_TURN,
    ROUNDABOUT,
    DESTINATION,
    UNKNOWN;

    companion object {
        fun fromMapplsId(id: Int): Maneuver = when (id) {
            1 -> STRAIGHT
            2, 15, 19 -> TURN_LEFT_VARIANTS(id)
            3 -> SLIGHT_LEFT
            4 -> SLIGHT_RIGHT
            5, 16, 20 -> RIGHT
            6 -> STRAIGHT
            7, 21, 50 -> U_TURN
            8 -> ROUNDABOUT
            23, 25 -> DESTINATION
            else -> UNKNOWN
        }

        private fun TURN_LEFT_VARIANTS(id: Int): Maneuver = LEFT
    }
}

@Serializable
data class NavigationUpdate(
    val maneuver: Maneuver,
    val distanceMeters: Int?,
    val roadName: String?,
    val etaMinutes: Int?,
    val timestamp: Long,
    val instruction: String? = null,
    val totalDistanceMeters: Int? = null,
    val source: String = "unknown"
) {
    /** Formats a compact summary string for logging and UI */
    fun toSummary(): String {
        val distStr = distanceMeters?.let { "${it}m" } ?: "--"
        val roadStr = roadName ?: instruction ?: "--"
        val etaStr = etaMinutes?.let { "${it}min" } ?: "--"
        return "${maneuver.name} / $distStr / $roadStr / ETA: $etaStr"
    }
}
