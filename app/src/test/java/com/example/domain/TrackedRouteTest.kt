package com.example.domain

import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.LabSettings
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.RouteOriginKind
import com.example.domain.rules.TrackedRoute
import com.example.domain.rules.TrackedRoutes
import com.example.domain.rules.effectiveDriveMinutes
import com.example.domain.rules.latestLeaveAt
import com.example.domain.rules.timingOverride
import com.example.domain.rules.withTracked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TrackedRouteTest {

    private val day = LocalDate.of(2026, 11, 27)
    private val settings = LabSettings()
    private val measured = CachedActivityRoute(
        activityId = "gig",
        originLat = 18.68,
        originLng = -68.45,
        destLat = 18.55,
        destLng = -68.35,
        travelMinutes = 25,
        distanceKm = 19.0,
        originKind = RouteOriginKind.HOME,
    )

    private val clubMed = TrackedRoute(
        venueKey = TrackedRoutes.venueKey("Hotel Club Med Punta Cana"),
        walkMinutes = 15,
        setupMinutes = 10,
        marginMinutes = 15,
        driveMinutesByOrigin = mapOf(TrackedRoutes.HOME_ORIGIN to 17),
    )

    private fun timeline(route: CachedActivityRoute, dayOverride: ActivityTimelineOverride? = null) =
        EventTimelineCalculator.calculate(
            eventDate = day,
            eventTime = LocalTime.of(12, 15),
            settings = settings,
            routeTravelMinutes = route.effectiveDriveMinutes,
            override = route.timingOverride(dayOverride),
        )

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(day, LocalTime.of(hour, minute))

    @Test
    fun `measured times give a latest departure and a recommended one with the margin`() {
        val result = timeline(measured.withTracked(clubMed))

        // 17 driving + 15 walking + 10 setting up = 42 min before 12:15.
        assertEquals(at(11, 18), result.leaveAt)
        assertEquals(at(11, 33), result.latestLeaveAt)
    }

    @Test
    fun `a trip from another venue keeps the walk and setup but not the home drive`() {
        val fromHotel = measured.copy(
            originKind = RouteOriginKind.PREVIOUS_EVENT,
            originVenueName = "Hotel Iberostar",
        ).withTracked(clubMed)

        assertEquals(25, fromHotel.effectiveDriveMinutes)
        assertNull(fromHotel.trackedDriveMinutes)
        // 25 + 15 + 10 + 15 of margin = 65 min before 12:15.
        assertEquals(at(11, 10), timeline(fromHotel).leaveAt)
    }

    @Test
    fun `adjustments made for one day win over the saved route`() {
        val dayOverride = ActivityTimelineOverride(activityId = "gig", travelMinutes = 30, setupMinutes = 20)

        val result = timeline(measured.withTracked(clubMed), dayOverride)

        // 30 + 15 + 20 + 15 of margin = 80 min before 12:15.
        assertEquals(at(10, 55), result.leaveAt)
    }

    @Test
    fun `without a saved route nothing changes and old tracked values are cleared`() {
        val dayOverride = ActivityTimelineOverride(activityId = "gig", setupMinutes = 20)

        val cleared = measured.withTracked(clubMed).withTracked(null)

        assertEquals(measured, cleared)
        assertSame(dayOverride, cleared.timingOverride(dayOverride))
        assertNull(cleared.timingOverride(null))
    }

    @Test
    fun `saving a drive for one origin keeps the drives from the others`() {
        val updated = TrackedRoutes.update(
            existing = clubMed,
            venueName = "Club Med Punta Cana",
            originKey = TrackedRoutes.originKey(RouteOriginKind.PREVIOUS_EVENT, "Hotel Iberostar"),
            driveMinutes = 22,
            walkMinutes = 12,
            setupMinutes = 10,
            marginMinutes = 20,
        )

        assertEquals(2, updated.driveMinutesByOrigin.size)
        assertEquals(17, updated.driveMinutesByOrigin[TrackedRoutes.HOME_ORIGIN])
        assertEquals(12, updated.walkMinutes)
        assertEquals(20, updated.marginMinutes)
    }

    @Test
    fun `spelling variants of a hotel share the saved route`() {
        assertEquals(
            TrackedRoutes.venueKey("Hotel Meliá Punta Cana"),
            TrackedRoutes.venueKey("melia punta cana"),
        )
    }

    @Test
    fun `name variants of one hotel find the saved route and an exact name wins`() {
        val saved = mapOf(clubMed.venueKey to clubMed)

        assertEquals(clubMed, TrackedRoutes.find(saved, "Club Med Punta Cana Tiara private pool"))
        assertNull(TrackedRoutes.find(saved, "Hotel Iberostar"))

        val pool = clubMed.copy(
            venueKey = TrackedRoutes.venueKey("Club Med Punta Cana Tiara private pool"),
            walkMinutes = 20,
        )
        val both = saved + (pool.venueKey to pool)
        assertEquals(pool, TrackedRoutes.find(both, "Club Med Punta Cana Tiara private pool"))
        assertEquals(clubMed, TrackedRoutes.find(both, "Hotel Club Med Punta Cana"))
        // Saved from the longer name, the plain hotel name finds it too.
        assertEquals(pool, TrackedRoutes.find(mapOf(pool.venueKey to pool), "Club Med Punta Cana"))
    }
}
