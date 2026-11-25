package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.LiveMilestoneKind
import com.example.domain.rules.LiveStageCalculator
import com.example.domain.rules.LiveStagePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LiveStageCalculatorTest {

    private val baseDate = LocalDate.of(2026, 6, 15)
    private val settings = LabSettings(
        prepMinutes = 30,
        travelBufferMinutes = 10,
        internalTransferInMinutes = 10,
        setupMinutes = 30,
        preStartMarginMinutes = 15,
        eventMinutes = 60,
        teardownMinutes = 20,
        internalTransferOutMinutes = 10,
    )

    private fun sampleActivity(
        id: String,
        date: LocalDate = baseDate,
        time: LocalTime? = LocalTime.of(19, 0),
        venue: String = "Hotel Paradisus",
    ) = Activity(
        id = id,
        ownerId = "user-1",
        title = venue,
        supplierName = "Eventos Punta Cana",
        venueName = venue,
        eventDate = date,
        eventTime = time,
        amount = BigDecimal("15000"),
        currency = Currency.DOP,
        paymentStatus = PaymentStatus.PENDIENTE,
    )

    @Test
    fun `evaluate walks through all timeline phases with accurate progress and urgency`() {
        // Event at 19:00 with 20 min route + 10 min buffer ->
        // marginStart=18:45, setupStart=18:15, arrive=18:05, leave=17:35, prepStart=17:05
        // eventEnd=20:00, teardownEnd=20:20, freeAt=20:30
        val timeline = EventTimelineCalculator.calculate(
            eventDate = baseDate,
            eventTime = LocalTime.of(19, 0),
            settings = settings,
            routeTravelMinutes = 20,
        )

        // Before prep
        val upcoming = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(15, 0)), timeline)
        assertEquals(LiveStagePhase.UPCOMING_TODAY, upcoming.phase)
        assertEquals(LiveMilestoneKind.PREPARE, upcoming.nextMilestoneKind)
        assertEquals(0f, upcoming.progressFraction, 0.001f)
        assertFalse(upcoming.isUrgent)

        // Preparing (25 min before leave -> not urgent)
        val prepEarly = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(17, 10)), timeline)
        assertEquals(LiveStagePhase.PREPARING, prepEarly.phase)
        assertEquals(LiveMilestoneKind.LEAVE, prepEarly.nextMilestoneKind)
        assertEquals(25L, prepEarly.minutesToNextMilestone)
        assertFalse(prepEarly.isUrgent)

        // Preparing (5 min before leave -> urgent)
        val prepUrgent = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(17, 30)), timeline)
        assertEquals(LiveStagePhase.PREPARING, prepUrgent.phase)
        assertEquals(5L, prepUrgent.minutesToNextMilestone)
        assertTrue(prepUrgent.isUrgent)

        // In transit
        val transit = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(17, 45)), timeline)
        assertEquals(LiveStagePhase.IN_TRANSIT, transit.phase)
        assertEquals(LiveMilestoneKind.ARRIVE, transit.nextMilestoneKind)
        assertEquals(20L, transit.minutesToNextMilestone)
        assertTrue(transit.progressFraction in 0.1f..0.4f)

        // On-site setup (8 min before event start -> urgent)
        val setupUrgent = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(18, 52)), timeline)
        assertEquals(LiveStagePhase.ON_SITE_SETUP, setupUrgent.phase)
        assertEquals(LiveMilestoneKind.EVENT_START, setupUrgent.nextMilestoneKind)
        assertEquals(8L, setupUrgent.minutesToNextMilestone)
        assertTrue(setupUrgent.isUrgent)

        // On stage
        val onStage = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(19, 30)), timeline)
        assertEquals(LiveStagePhase.ON_STAGE, onStage.phase)
        assertEquals(LiveMilestoneKind.EVENT_END, onStage.nextMilestoneKind)
        assertEquals(30L, onStage.minutesToNextMilestone)
        assertFalse(onStage.isUrgent)

        // Wrapping up
        val wrapping = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(20, 10)), timeline)
        assertEquals(LiveStagePhase.WRAPPING_UP, wrapping.phase)
        assertEquals(LiveMilestoneKind.FREE, wrapping.nextMilestoneKind)
        assertEquals(20L, wrapping.minutesToNextMilestone)

        // Completed
        val completed = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(20, 35)), timeline)
        assertEquals(LiveStagePhase.COMPLETED, completed.phase)
        assertEquals(LiveMilestoneKind.NONE, completed.nextMilestoneKind)
        assertEquals(1f, completed.progressFraction, 0.001f)
    }

    @Test
    fun `evaluate skips IN_TRANSIT when no travel estimate is configured`() {
        val timeline = EventTimelineCalculator.calculate(
            eventDate = baseDate,
            eventTime = LocalTime.of(19, 0),
            settings = settings,
            routeTravelMinutes = null,
        )
        // arrive == leave == 18:05
        val atArrive = LiveStageCalculator.evaluate(LocalDateTime.of(baseDate, LocalTime.of(18, 10)), timeline)
        assertEquals(LiveStagePhase.ON_SITE_SETUP, atArrive.phase)
    }

    @Test
    fun `selectFeaturedTodayActivity prefers active gig over later upcoming gig`() {
        val early = sampleActivity("a1", time = LocalTime.of(14, 0), venue = "Hard Rock")
        val late = sampleActivity("a2", time = LocalTime.of(20, 0), venue = "Secrets Cap Cana")
        val tomorrow = sampleActivity("a3", date = baseDate.plusDays(1), time = LocalTime.of(12, 0))
        val untimed = sampleActivity("a4", time = null)

        // At 14:30, early gig is ON_STAGE
        val featured = LiveStageCalculator.selectFeaturedTodayActivity(
            now = LocalDateTime.of(baseDate, LocalTime.of(14, 30)),
            today = baseDate,
            activities = listOf(late, tomorrow, untimed, early),
            settings = settings,
            routes = mapOf(
                "a1" to CachedActivityRoute(
                    activityId = "a1",
                    originLat = 18.68,
                    originLng = -68.42,
                    destLat = 18.70,
                    destLng = -68.44,
                    travelMinutes = 20,
                    distanceKm = 14.5,
                )
            ),
            timings = mapOf("a1" to ActivityTimelineOverride(eventMinutes = 90)),
        )

        assertNotNull(featured)
        assertEquals("a1", featured!!.activity.id)
        assertEquals(LiveStagePhase.ON_STAGE, featured.snapshot.phase)
    }

    @Test
    fun `selectFeaturedTodayActivity keeps recently completed gig for 45 minutes then hides it`() {
        // Event 14:00, duration 60, teardown 20, out 10 -> freeAt 15:30
        val gig = sampleActivity("a1", time = LocalTime.of(14, 0))

        val withinWindow = LiveStageCalculator.selectFeaturedTodayActivity(
            now = LocalDateTime.of(baseDate, LocalTime.of(16, 0)),
            today = baseDate,
            activities = listOf(gig),
            settings = settings,
        )
        assertNotNull(withinWindow)
        assertEquals(LiveStagePhase.COMPLETED, withinWindow!!.snapshot.phase)

        val afterWindow = LiveStageCalculator.selectFeaturedTodayActivity(
            now = LocalDateTime.of(baseDate, LocalTime.of(16, 20)),
            today = baseDate,
            activities = listOf(gig),
            settings = settings,
        )
        assertNull(afterWindow)
    }
}
