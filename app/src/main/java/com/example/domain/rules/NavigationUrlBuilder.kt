package com.example.domain.rules

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Pure URL builders for zero-API-key external navigation via Android Intent.ACTION_VIEW:
 * - Google Maps: https://www.google.com/maps/dir/?api=1&destination={name or lat,lng}&travelmode=driving
 * - Waze: https://waze.com/ul?ll={lat},{lng}&navigate=yes (falls back to q={name} when coords are missing)
 */
object NavigationUrlBuilder {

    fun googleMapsDirectionsUrl(
        venueName: String?,
        latitude: Double?,
        longitude: Double?,
    ): String? {
        val destination = when {
            isValidCoordinates(latitude, longitude) -> formatCoords(latitude!!, longitude!!)
            !venueName.isNullOrBlank() -> encodeQuery(venueName.trim())
            else -> return null
        }
        return "https://www.google.com/maps/dir/?api=1&destination=$destination&travelmode=driving"
    }

    fun wazeNavigationUrl(
        venueName: String?,
        latitude: Double?,
        longitude: Double?,
    ): String? {
        return when {
            isValidCoordinates(latitude, longitude) -> {
                val coords = formatCoords(latitude!!, longitude!!)
                "https://waze.com/ul?ll=$coords&navigate=yes"
            }
            !venueName.isNullOrBlank() -> {
                val encoded = encodeQuery(venueName.trim())
                "https://waze.com/ul?q=$encoded&navigate=yes"
            }
            else -> null
        }
    }

    private fun isValidCoordinates(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        return latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
    }

    private fun formatCoords(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
            .replace(Regex("0+(?=,|$)"), "")
            .replace(Regex("\\.(?=,|$)"), "")

    private fun encodeQuery(raw: String): String =
        URLEncoder.encode(raw, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
