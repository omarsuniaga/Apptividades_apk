package com.example.data

import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.data.port.RouteEstimate
import com.example.domain.model.Activity
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.LabSettings
import com.example.domain.rules.RouteOriginKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class EventRouteResolverTest {

    private val home = GeoPoint(18.70, -68.45)
    private val phone = GeoPoint(18.90, -68.90)
    private val settings = LabSettings(
        eventMinutes = 90,
        teardownMinutes = 15,
        homeLatitude = home.latitude,
        homeLongitude = home.longitude,
    )

    private class FakeMaps(
        private val current: GeoPoint?,
        private val offline: Boolean = false,
    ) : MapsAndLocationPort {
        val requestedOrigins = mutableListOf<GeoPoint>()

        override suspend fun searchPlaces(query: String): Result<List<PlacePrediction>> =
            Result.success(emptyList())

        override suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun getLastKnownOrCurrentLocation(): GeoPoint? = current

        override suspend fun computeRouteEstimate(
            origin: GeoPoint,
            destination: GeoPoint,
        ): Result<RouteEstimate> {
            requestedOrigins += origin
            return if (offline) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(RouteEstimate(travelMinutes = 20, distanceKm = 9.5, fromOsrm = true))
            }
        }
    }

    private fun activity(id: String, time: String, lat: Double?, lng: Double?) = Activity(
        id = id,
        ownerId = "owner",
        title = id,
        eventDate = LocalDate.of(2026, 11, 26),
        eventTime = LocalTime.parse(time),
        venueName = "Hotel $id",
        amount = BigDecimal.ONE,
        latitude = lat,
        longitude = lng,
    )

    private val a = activity("A", "16:00", 18.60, -68.40)
    private val b = activity("B", "19:30", 18.80, -68.50)

    private fun resolve(
        maps: FakeMaps,
        activities: List<Activity>,
        target: Activity = b,
        cached: CachedActivityRoute? = null,
        forceRefresh: Boolean = false,
    ) = runBlocking {
        EventRouteResolver(maps).resolve(target, activities, settings, emptyMap(), cached, forceRefresh)
    }!!

    @Test
    fun `a chained event measures the trip from the previous venue`() {
        val maps = FakeMaps(current = phone)

        val route = resolve(maps, listOf(a, b))

        assertEquals(RouteOriginKind.PREVIOUS_EVENT, route.originKind)
        assertEquals("Hotel A", route.originVenueName)
        assertEquals(18.60, route.originLat, 0.0)
        assertEquals(9.5, route.distanceKm!!, 0.0)
        assertEquals(listOf(GeoPoint(18.60, -68.40)), maps.requestedOrigins)
    }

    @Test
    fun `the first event of the day starts from home even when the phone is elsewhere`() {
        val route = resolve(FakeMaps(current = phone), listOf(a, b), target = a)

        assertEquals(RouteOriginKind.HOME, route.originKind)
        assertEquals(home.latitude, route.originLat, 0.0)
        assertNull(route.originVenueName)
    }

    @Test
    fun `an unpinned previous event falls back to home and is reported`() {
        val unpinned = a.copy(latitude = null, longitude = null)

        val route = resolve(FakeMaps(current = phone), listOf(unpinned, b))

        assertEquals(RouteOriginKind.HOME, route.originKind)
        assertEquals("Hotel A", route.unpinnedPreviousVenueName)
    }

    @Test
    fun `offline estimates still start at the previous venue`() {
        val route = resolve(FakeMaps(current = null, offline = true), listOf(a, b))

        assertEquals(RouteOriginKind.PREVIOUS_EVENT, route.originKind)
        assertEquals(false, route.fromOsrm)
        assertNull(route.distanceKm)
    }

    @Test
    fun `a cached route from another origin is not reused`() {
        val stale = CachedActivityRoute("B", home.latitude, home.longitude, 18.80, -68.50, 55, 40.0)
        val maps = FakeMaps(current = null)

        val route = resolve(maps, listOf(a, b), cached = stale)

        assertEquals(20, route.travelMinutes)
        assertEquals(1, maps.requestedOrigins.size)
    }

    @Test
    fun `a matching cached route is reused unless refresh is forced`() {
        val cached = CachedActivityRoute("B", 18.60, -68.40, 18.80, -68.50, 25, 11.0)
        val maps = FakeMaps(current = null)

        assertEquals(25, resolve(maps, listOf(a, b), cached = cached).travelMinutes)
        assertEquals(0, maps.requestedOrigins.size)
        assertEquals(20, resolve(maps, listOf(a, b), cached = cached, forceRefresh = true).travelMinutes)
    }

    @Test
    fun `a forced refresh that fails keeps the cached online estimate`() {
        val cached = CachedActivityRoute("B", 18.60, -68.40, 18.80, -68.50, 35, 14.0)

        val route = resolve(
            FakeMaps(current = null, offline = true),
            listOf(a, b),
            cached = cached,
            forceRefresh = true,
        )

        assertEquals(35, route.travelMinutes)
        assertEquals(14.0, route.distanceKm!!, 0.0)
        assertEquals(RouteOriginKind.PREVIOUS_EVENT, route.originKind)
    }

    @Test
    fun `far events need an origin refresh only when chaining is involved`() {
        val homeRoute = CachedActivityRoute(
            "B", home.latitude, home.longitude, 18.80, -68.50, 20, originKind = RouteOriginKind.HOME,
        )
        val chainedRoute = homeRoute.copy(originKind = RouteOriginKind.PREVIOUS_EVENT)

        assertTrue(EventRouteResolver.needsOriginRefresh(b, listOf(a, b), homeRoute))
        assertTrue(EventRouteResolver.needsOriginRefresh(b, listOf(b), chainedRoute))
        assertFalse(EventRouteResolver.needsOriginRefresh(b, listOf(b), homeRoute))
        assertFalse(
            EventRouteResolver.needsOriginRefresh(b.copy(latitude = null), listOf(a, b), chainedRoute),
        )
    }

    @Test
    fun `the chain and the shortfall use the tracked times of the destination`() {
        val tracked = com.example.domain.rules.TrackedRoute(
            venueKey = com.example.domain.rules.TrackedRoutes.venueKey(b.venueName),
            walkMinutes = 60,
            setupMinutes = 45,
            marginMinutes = 30,
            driveMinutesByOrigin = mapOf("venue:a" to 50),
        )

        val route = runBlocking {
            EventRouteResolver(FakeMaps(null))
                .resolve(b, listOf(a, b), settings, emptyMap(), null, false, mapOf(tracked.venueKey to tracked))
        }!!

        // B 19:30 minus 50 + 60 + 45 + 30 means leaving at 16:25; A is done at 18:00.
        assertEquals(RouteOriginKind.PREVIOUS_EVENT, route.originKind)
        assertEquals(50, route.trackedDriveMinutes)
        assertEquals(20, route.travelMinutes)
        assertEquals(95, route.shortfallMinutes)
    }

    @Test
    fun `a destination without a pin has no route`() {
        val route = runBlocking {
            EventRouteResolver(FakeMaps(null)).resolve(
                b.copy(latitude = null, longitude = null),
                listOf(a, b),
                settings,
                emptyMap(),
                null,
                false,
            )
        }

        assertNull(route)
    }
}
