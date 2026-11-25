package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.rules.CalendarLabelFormatter
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.TimelineStepKind
import com.example.ui.viewmodel.AppDataState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EventTimelineAndCurrentMonthTest {

    @Test
    fun timelineComputesAllArtistMilestonesWithRouteTravelAndBuffer() {
        val settings = LabSettings(
            prepMinutes = 60,
            travelBufferMinutes = 15,
            internalTransferInMinutes = 15,
            setupMinutes = 45,
            preStartMarginMinutes = 15,
            eventMinutes = 120,
            teardownMinutes = 45,
            internalTransferOutMinutes = 15,
        )

        val timeline = EventTimelineCalculator.calculate(
            eventDate = LocalDate.of(2026, 11, 20),
            eventTime = LocalTime.of(19, 0),
            settings = settings,
            routeTravelMinutes = 30,
            routeDistanceKm = 18.5,
        )

        assertTrue(timeline.hasTravelEstimate)
        // Working backwards from 19:00:
        // marginStart = 18:45 (15m)
        // setupStart = 18:00 (45m)
        // arriveAtVenue = 17:45 (15m internal in)
        // leaveAt = 17:00 (30m travel + 15m buffer)
        // prepareStart = 16:00 (60m prep)
        assertEquals(LocalDateTime.of(2026, 11, 20, 18, 45), timeline.marginStartAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 18, 0), timeline.setupStartAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 17, 45), timeline.arriveAtVenueAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 17, 0), timeline.leaveAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 16, 0), timeline.prepareStartAt)

        // Working forwards from 19:00:
        // eventEnd = 21:00 (120m)
        // teardownEnd = 21:45 (45m)
        // freeAt = 22:00 (15m internal out)
        assertEquals(LocalDateTime.of(2026, 11, 20, 21, 0), timeline.eventEndAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 21, 45), timeline.teardownEndAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 22, 0), timeline.freeAt)
        assertEquals(360, timeline.totalMinutes) // 6 hours total
        assertEquals("6 h", EventTimelineCalculator.formatDurationCompact(timeline.totalMinutes))

        assertEquals(
            listOf(
                TimelineStepKind.PREPARE,
                TimelineStepKind.LEAVE,
                TimelineStepKind.TRAVEL,
                TimelineStepKind.INTERNAL_TRANSFER_IN,
                TimelineStepKind.SETUP,
                TimelineStepKind.PRE_START_MARGIN,
                TimelineStepKind.EVENT,
                TimelineStepKind.TEARDOWN,
                TimelineStepKind.INTERNAL_TRANSFER_OUT,
                TimelineStepKind.FREE,
            ),
            timeline.steps.map { it.kind },
        )
    }

    @Test
    fun timelineOmitsTravelStepWhenNoRouteOrManualTravelProvided() {
        val timeline = EventTimelineCalculator.calculate(
            eventDate = LocalDate.of(2026, 11, 20),
            eventTime = LocalTime.of(18, 0),
            settings = LabSettings(),
            routeTravelMinutes = null,
        )

        assertFalse(timeline.hasTravelEstimate)
        assertFalse(timeline.steps.any { it.kind == TimelineStepKind.TRAVEL })
        // 18:00 - 15 (margin) - 45 (setup) - 15 (internal in) = 16:45 leave, 15:45 prepare
        assertEquals(LocalDateTime.of(2026, 11, 20, 16, 45), timeline.leaveAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 15, 45), timeline.prepareStartAt)
    }

    @Test
    fun timelineAppliesCustomOverrideAndWrapsAcrossMidnight() {
        val override = ActivityTimelineOverride(
            activityId = "gig-late",
            travelMinutes = 40,
            travelBufferMinutes = 0,
            internalTransferInMinutes = 25,
            eventMinutes = 180,
            internalTransferOutMinutes = 20,
        )

        val timeline = EventTimelineCalculator.calculate(
            eventDate = LocalDate.of(2026, 11, 20),
            eventTime = LocalTime.of(22, 0),
            settings = LabSettings(),
            routeTravelMinutes = 15,
            override = override,
        )

        assertTrue(timeline.hasTravelEstimate)
        assertTrue(timeline.config.travelIsManualOverride)
        assertEquals(40, timeline.config.travelMinutes)
        // 22:00 + 180m = 01:00 next day; + 45m teardown = 01:45; + 20m internal out = 02:05 next day
        assertEquals(LocalDateTime.of(2026, 11, 21, 2, 5), timeline.freeAt)
    }

    @Test
    fun upcomingActivitiesIncludesOnlyCurrentMonthAndExcludesFollowingMonths() {
        val today = LocalDate.of(2026, 11, 15)
        fun act(id: String, date: LocalDate, hour: Int = 18) = Activity(
            id = id,
            ownerId = "owner-1",
            supplierName = "Lina",
            title = "Evento $id",
            eventDate = date,
            eventTime = LocalTime.of(hour, 0),
            venueName = "Hotel $id",
            amount = BigDecimal("5000"),
            currency = Currency.DOP,
        )

        val state = AppDataState(
            today = today,
            activities = listOf(
                act("past_nov", LocalDate.of(2026, 11, 10)),
                act("today_nov", LocalDate.of(2026, 11, 15), 20),
                act("nov_18", LocalDate.of(2026, 11, 18)),
                act("nov_21", LocalDate.of(2026, 11, 21)),
                act("nov_25", LocalDate.of(2026, 11, 25)),
                act("nov_29", LocalDate.of(2026, 11, 29)),
                act("nov_30", LocalDate.of(2026, 11, 30)),
                act("next_month_dec", LocalDate.of(2026, 12, 2)),
                act("next_year_jan", LocalDate.of(2027, 1, 5)),
            ),
        )

        assertEquals(
            listOf("today_nov", "nov_18", "nov_21", "nov_25", "nov_29", "nov_30"),
            state.upcomingActivities.map { it.id },
        )
    }

    @Test
    fun calendarLabelFormatterExtractsConciseSupplierName() {
        assertEquals(
            "Lina",
            CalendarLabelFormatter.shortSupplierLabel("Lina Martínez", "Lina Martínez"),
        )
        assertEquals(
            "José",
            CalendarLabelFormatter.shortSupplierLabel("José David", "Jose David"),
        )
        assertEquals(
            "La Bodega",
            CalendarLabelFormatter.shortSupplierLabel("La Bodega Eventos", "La Bodega Eventos"),
        )
        assertEquals(
            "Club",
            CalendarLabelFormatter.shortSupplierLabel("", "", "Club Med Punta Cana"),
        )
    }
}
