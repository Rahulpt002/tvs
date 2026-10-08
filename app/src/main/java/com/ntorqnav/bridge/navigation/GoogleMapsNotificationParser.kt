package com.ntorqnav.bridge.navigation

import java.util.regex.Pattern

/**
 * Robust parser for Google Maps turn-by-turn notification text.
 *
 * Example notification payloads:
 * Title: "Turn left onto MG Road"
 * Text: "250 m · 12 min"
 * SubText: "ETA 5:45 PM"
 *
 * Another format:
 * Title: "250 m"
 * Text: "Turn left onto MG Road"
 * SubText: "12 min · 4.2 km"
 */
object GoogleMapsNotificationParser {

    private val DIST_METERS_REGEX = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(m|meter|meters|km|kilometer|kilometers|ft|feet|mi|mile|miles)")
    private val ETA_MINUTES_REGEX = Pattern.compile("(?i)(?:(\\d+)\\s*(?:hr|hour|hours))?\\s*(\\d+)?\\s*(?:min|mins|minute|minutes)")

    fun parse(title: String?, text: String?, subText: String?, timestamp: Long = System.currentTimeMillis()): NavigationUpdate? {
        val safeTitle = title?.trim() ?: ""
        val safeText = text?.trim() ?: ""
        val safeSub = subText?.trim() ?: ""

        val combined = "$safeTitle $safeText $safeSub"
        if (combined.isBlank()) return null

        val maneuver = parseManeuver(safeTitle, safeText)
        val distanceMeters = parseDistanceMeters(safeTitle, safeText, safeSub)
        val etaMinutes = parseEtaMinutes(safeText, safeSub)
        val roadName = extractRoadName(safeTitle, safeText)
        val instruction = if (safeTitle.length > safeText.length && safeTitle.contains(" ")) safeTitle else safeText

        return NavigationUpdate(
            maneuver = maneuver,
            distanceMeters = distanceMeters,
            roadName = roadName,
            etaMinutes = etaMinutes,
            timestamp = timestamp,
            instruction = instruction.ifBlank { null },
            source = "google_maps_notification"
        )
    }

    fun parseManeuver(title: String, text: String): Maneuver {
        val lower = "$title $text".lowercase()
        return when {
            lower.contains("arrived") || lower.contains("destination") -> Maneuver.DESTINATION
            lower.contains("u-turn") || lower.contains("make a u-turn") -> Maneuver.U_TURN
            lower.contains("slight left") || lower.contains("bear left") || lower.contains("keep left") -> Maneuver.SLIGHT_LEFT
            lower.contains("slight right") || lower.contains("bear right") || lower.contains("keep right") -> Maneuver.SLIGHT_RIGHT
            lower.contains("turn left") || lower.contains("left") -> Maneuver.LEFT
            lower.contains("turn right") || lower.contains("right") -> Maneuver.RIGHT
            lower.contains("roundabout") || lower.contains("rotary") || lower.contains("circle") -> Maneuver.ROUNDABOUT
            lower.contains("straight") || lower.contains("continue") || lower.contains("head ") -> Maneuver.STRAIGHT
            else -> Maneuver.UNKNOWN
        }
    }

    fun parseDistanceMeters(vararg inputs: String): Int? {
        for (input in inputs) {
            val matcher = DIST_METERS_REGEX.matcher(input)
            if (matcher.find()) {
                val value = matcher.group(1)?.toDoubleOrNull() ?: continue
                val unit = matcher.group(2)?.lowercase() ?: "m"
                return when {
                    unit.startsWith("k") -> (value * 1000).toInt()
                    unit.startsWith("mi") -> (value * 1609.34).toInt()
                    unit.startsWith("f") -> (value * 0.3048).toInt()
                    else -> value.toInt()
                }
            }
        }
        return null
    }

    fun parseEtaMinutes(vararg inputs: String): Int? {
        for (input in inputs) {
            val matcher = ETA_MINUTES_REGEX.matcher(input)
            if (matcher.find()) {
                val hours = matcher.group(1)?.toIntOrNull() ?: 0
                val mins = matcher.group(2)?.toIntOrNull() ?: 0
                val total = hours * 60 + mins
                if (total > 0) return total
            }
        }
        return null
    }

    fun extractRoadName(title: String, text: String): String? {
        val candidate = when {
            title.contains(" onto ", ignoreCase = true) -> title.substringAfter(" onto ", "").trim()
            title.contains(" on ", ignoreCase = true) -> title.substringAfter(" on ", "").trim()
            title.contains(" toward ", ignoreCase = true) -> title.substringAfter(" toward ", "").trim()
            text.contains(" onto ", ignoreCase = true) -> text.substringAfter(" onto ", "").trim()
            text.contains(" on ", ignoreCase = true) -> text.substringAfter(" on ", "").trim()
            text.contains(" · ") -> text.substringBefore(" · ").trim()
            else -> null
        }
        return candidate?.ifBlank { null }
    }
}
