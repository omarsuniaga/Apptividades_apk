package com.example.domain

import com.example.domain.rules.NavigationUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationUrlOriginTest {

    @Test
    fun `Google Maps directions start at the given origin`() {
        val url = NavigationUrlBuilder.googleMapsDirectionsUrl(
            venueName = "Hotel B",
            latitude = 18.8,
            longitude = -68.5,
            originLatitude = 18.6,
            originLongitude = -68.4,
        )

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&origin=18.6,-68.4&destination=18.8,-68.5&travelmode=driving",
            url,
        )
    }

    @Test
    fun `without a complete origin Google Maps keeps starting where the phone is`() {
        val url = NavigationUrlBuilder.googleMapsDirectionsUrl(
            venueName = "Hotel B",
            latitude = 18.8,
            longitude = -68.5,
            originLatitude = 18.6,
        )

        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=18.8,-68.5&travelmode=driving",
            url,
        )
    }
}
