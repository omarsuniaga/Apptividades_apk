package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.LabSettings
import com.example.domain.rules.ChainDecision
import com.example.domain.rules.EventChainPlanner
import com.example.domain.rules.RouteOriginKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EventChainPlannerTest {

    private val day = LocalDate.of(2026, 11, 26)

    // Done with an event = start + 90 + 15 teardown + 15 exit = start + 2 h.
    // Leaving for the next one = its start - 15 margin - 45 setup - 15 entry - travel - 15 buffer,
    // which is 110 min before it with the default 20 min of travel.
    private val settings = LabSettings(
        eventMinutes = 90,
        teardownMinutes = 15,
        internalTransferOutMinutes = 15,
        preStartMarginMinutes = 15,
        setupMinutes = 45,
        internalTransferInMinutes = 15,
        travelBufferMinutes = 15,
    )

    private fun activity(
        id: String,
        time: String?,
        date: LocalDate = day,
        pinned: Boolean = true,
    ) = Activity(
        id = id,
        ownerId = "owner",
        title = id,
        eventDate = date,
        eventTime = time?.let(LocalTime::parse),
        venueName = "Hotel $id",
        amount = BigDecimal.ONE,
        latitude = if (pinned) 18.6 else null,
        longitude = if (pinned) -68.4 else null,
    )

    private fun plan(
        target: Activity,
        others: List<Activity>,
        travel: Int? = 20,
        overrides: Map<String, ActivityTimelineOverride> = emptyMap(),
    ) = EventChainPlanner.plan(target, others + target, settings, overrides, travel)

    @Test
    fun `chains from the previous event when free time is short`() {
        val a = activity("A", "16:00")
        // Done with A at 18:00. B 20:30: leave at 18:40.
        val decision = plan(activity("B", "20:30"), listOf(a)) as ChainDecision.Chained

        assertEquals("A", decision.previous.id)
        assertEquals(LocalDateTime.of(day, LocalTime.of(18, 0)), decision.freeAt)
        assertEquals(0, decision.shortfallMinutes)
    }

    @Test
    fun `reports the missing minutes when there is not enough time`() {
        // B 19:30: leave at 17:40, but A frees at 18:00.
        val decision = plan(activity("B", "19:30"), listOf(activity("A", "16:00")))

        assertEquals(20, (decision as ChainDecision.Chained).shortfallMinutes)
    }

    @Test
    fun `exactly two free hours still chains`() {
        // 21:50 means leaving at 20:00, 120 min after 18:00.
        val decision = plan(activity("B", "21:50"), listOf(activity("A", "16:00")))

        assertTrue(decision is ChainDecision.Chained)
    }

    @Test
    fun `more than two free hours starts from home`() {
        val decision = plan(activity("B", "21:51"), listOf(activity("A", "16:00")))

        assertTrue(decision is ChainDecision.TooMuchFreeTime)
    }

    @Test
    fun `longer travel shortens the free time and chains`() {
        val decision = plan(activity("B", "22:30"), listOf(activity("A", "16:00")), travel = 60)

        assertTrue(decision is ChainDecision.Chained)
    }

    @Test
    fun `without an earlier event the same day there is no chain`() {
        val others = listOf(
            activity("yesterday", "16:00", date = day.minusDays(1)),
            activity("later", "23:00"),
            activity("untimed", null),
        )

        assertEquals(ChainDecision.NoPreviousEvent, plan(activity("B", "20:00"), others))
    }

    @Test
    fun `the closest earlier event is the origin`() {
        val others = listOf(activity("early", "12:00"), activity("A", "16:00"))

        val decision = plan(activity("B", "20:30"), others) as ChainDecision.Chained

        assertEquals("A", decision.previous.id)
    }

    @Test
    fun `per activity timing overrides move the moment A is done`() {
        val overrides = mapOf("A" to ActivityTimelineOverride(activityId = "A", eventMinutes = 180))

        val decision = plan(activity("B", "20:30"), listOf(activity("A", "16:00")), overrides = overrides)

        // Done at 19:30, leave at 18:40.
        assertEquals(50, (decision as ChainDecision.Chained).shortfallMinutes)
    }

    @Test
    fun `a previous event without a map pin is reported`() {
        val decision = plan(activity("B", "20:30"), listOf(activity("A", "16:00", pinned = false)), travel = null)

        assertEquals("A", (decision as ChainDecision.PreviousWithoutPin).previous.id)
    }

    @Test
    fun `a destination without a map pin has no route to chain`() {
        val decision = plan(activity("B", "20:30", pinned = false), listOf(activity("A", "16:00")), travel = null)

        assertEquals(ChainDecision.NoPreviousEvent, decision)
    }

    @Test
    fun `duration from leaving leaves the home preparation out`() {
        val timeline = com.example.domain.rules.EventTimelineCalculator.calculate(
            eventDate = day,
            eventTime = LocalTime.of(19, 30),
            settings = settings,
            routeTravelMinutes = 20,
        )

        assertEquals(timeline.totalMinutes - settings.prepMinutes, timeline.minutesFromLeaving)
    }

    @Test
    fun `home wins over current location when there is no chain`() {
        val origin = EventChainPlanner.fallbackOrigin(
            homeLatitude = 1.0,
            homeLongitude = 2.0,
            currentLatitude = 3.0,
            currentLongitude = 4.0,
        )

        assertEquals(RouteOriginKind.HOME, origin.kind)
        assertEquals(1.0, origin.latitude, 0.0)
    }

    @Test
    fun `current location then default area are used when home is unset`() {
        val current = EventChainPlanner.fallbackOrigin(null, null, 3.0, 4.0)
        val default = EventChainPlanner.fallbackOrigin(null, null, null, null)

        assertEquals(RouteOriginKind.CURRENT_LOCATION, current.kind)
        assertEquals(RouteOriginKind.DEFAULT_AREA, default.kind)
    }
}
