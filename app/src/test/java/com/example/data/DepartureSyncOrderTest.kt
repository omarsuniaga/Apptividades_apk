package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alarms.DepartureAlarmScheduler
import com.example.data.local.SessionDataStore
import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.data.port.RouteEstimate
import com.example.domain.model.Activity
import com.example.domain.model.LabSettings
import com.example.domain.rules.RouteOriginKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DepartureSyncOrderTest {

    private val today = LocalDate.now(ZoneId.of("America/Santo_Domingo"))

    private fun activity(id: String, date: LocalDate, time: String, latitude: Double) = Activity(
        id = id,
        ownerId = "owner",
        title = id,
        eventDate = date,
        eventTime = LocalTime.parse(time),
        venueName = "Hotel $id",
        amount = BigDecimal.ONE,
        latitude = latitude,
        longitude = -68.40,
    )

    @Test
    fun `upcoming departures are saved before far routes are requested`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = SessionDataStore(context)
        store.saveScheduledDepartures(emptyMap())
        val farDestination = 19.50
        val savedWhenFarRouteWasRequested = mutableListOf<Set<String>>()
        val maps = object : MapsAndLocationPort {
            override suspend fun searchPlaces(query: String): Result<List<PlacePrediction>> =
                Result.success(emptyList())

            override suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace> =
                Result.failure(IllegalStateException("unused"))

            override suspend fun getLastKnownOrCurrentLocation(): GeoPoint? = null

            override suspend fun computeRouteEstimate(
                origin: GeoPoint,
                destination: GeoPoint,
            ): Result<RouteEstimate> {
                if (destination.latitude == farDestination) {
                    savedWhenFarRouteWasRequested += store.loadScheduledDepartures().keys
                }
                return Result.success(RouteEstimate(travelMinutes = 20, distanceKm = 9.0, fromOsrm = true))
            }
        }
        val farDay = today.plusDays(30)
        val activities = listOf(
            activity("near", today.plusDays(3), "20:00", 18.60),
            activity("farFirst", farDay, "16:00", 18.70),
            activity("farSecond", farDay, "19:30", farDestination),
        )

        DepartureAlarmScheduler(context, store, maps).syncUpcomingActivities(
            activities = activities,
            settings = LabSettings(homeLatitude = 18.68, homeLongitude = -68.45),
            overdueSummaryText = null,
            forceRecalculateRoutes = false,
        )

        assertTrue(savedWhenFarRouteWasRequested.isNotEmpty())
        assertTrue(savedWhenFarRouteWasRequested.all { "near" in it })
        // The far chained route is still refreshed and saved.
        assertEquals(RouteOriginKind.PREVIOUS_EVENT, store.loadActivityRoutes()["farSecond"]?.originKind)
    }
}
