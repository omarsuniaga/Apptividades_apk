package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.ContingencyEvaluator
import com.example.domain.rules.ContingencyState
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.ScheduleConflictChecker
import com.example.domain.rules.SupplierMessagePreset
import com.example.domain.rules.SupplierQuickMessageBuilder
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

class ContingencyAndDurationAndChannelsTest {

    private val eventDate = LocalDate.of(2026, 11, 28)

    @Test
    fun `contingency stays MONITORING before lead window`() {
        val eventStart = LocalDateTime.of(eventDate, LocalTime.of(19, 0))
        val now = LocalDateTime.of(eventDate, LocalTime.of(18, 10)) // 50 min before

        val assessment = ContingencyEvaluator.evaluate(
            now = now,
            eventStart = eventStart,
            contingencyEnabled = true,
            leadMinutes = 30,
            isMarkedUnderControl = false,
            venueLat = 18.6813,
            venueLng = -68.4055,
            currentLat = 18.5800,
            currentLng = -68.4000,
        )

        assertEquals(ContingencyState.MONITORING, assessment.state)
        assertFalse(assessment.shouldNotify)
        assertEquals(50L, assessment.minutesUntilStart)
    }

    @Test
    fun `contingency enters ACTIVE_ALERT when inside window and far from venue`() {
        val eventStart = LocalDateTime.of(eventDate, LocalTime.of(19, 0))
        val now = LocalDateTime.of(eventDate, LocalTime.of(18, 35)) // 25 min before

        val assessment = ContingencyEvaluator.evaluate(
            now = now,
            eventStart = eventStart,
            contingencyEnabled = true,
            leadMinutes = 30,
            isMarkedUnderControl = false,
            venueLat = 18.6813,
            venueLng = -68.4055,
            currentLat = 18.6100, // ~7.9 km away
            currentLng = -68.4055,
        )

        assertEquals(ContingencyState.ACTIVE_ALERT, assessment.state)
        assertTrue(assessment.shouldNotify)
        assertFalse(assessment.isFinalEscalation)
        assertNotNull(assessment.distanceMeters)
        assertTrue(assessment.distanceMeters!! > 5000.0)
    }

    @Test
    fun `contingency marks final escalation at 10 minutes or less before start`() {
        val eventStart = LocalDateTime.of(eventDate, LocalTime.of(19, 0))
        val now = LocalDateTime.of(eventDate, LocalTime.of(18, 52)) // 8 min before

        val assessment = ContingencyEvaluator.evaluate(
            now = now,
            eventStart = eventStart,
            contingencyEnabled = true,
            leadMinutes = 30,
            isMarkedUnderControl = false,
            venueLat = null,
            venueLng = null,
            currentLat = null,
            currentLng = null,
        )

        assertEquals(ContingencyState.ACTIVE_ALERT, assessment.state)
        assertTrue(assessment.shouldNotify)
        assertTrue(assessment.isFinalEscalation)
        assertNull(assessment.distanceMeters)
    }

    @Test
    fun `contingency auto-silences as ARRIVED_NEARBY when within 500 meters`() {
        val eventStart = LocalDateTime.of(eventDate, LocalTime.of(19, 0))
        val now = LocalDateTime.of(eventDate, LocalTime.of(18, 40)) // 20 min before

        val assessment = ContingencyEvaluator.evaluate(
            now = now,
            eventStart = eventStart,
            contingencyEnabled = true,
            leadMinutes = 30,
            isMarkedUnderControl = false,
            venueLat = 18.6813,
            venueLng = -68.4055,
            currentLat = 18.6822, // ~100 m away
            currentLng = -68.4055,
        )

        assertEquals(ContingencyState.ARRIVED_NEARBY, assessment.state)
        assertFalse(assessment.shouldNotify)
    }

    @Test
    fun `contingency stays silent when artist confirms Todo bajo control`() {
        val eventStart = LocalDateTime.of(eventDate, LocalTime.of(19, 0))
        val now = LocalDateTime.of(eventDate, LocalTime.of(18, 50)) // 10 min before

        val assessment = ContingencyEvaluator.evaluate(
            now = now,
            eventStart = eventStart,
            contingencyEnabled = true,
            leadMinutes = 30,
            isMarkedUnderControl = true,
            venueLat = 18.6813,
            venueLng = -68.4055,
            currentLat = 18.5000, // far away, but artist explicitly confirmed
            currentLng = -68.4055,
        )

        assertEquals(ContingencyState.UNDER_CONTROL, assessment.state)
        assertFalse(assessment.shouldNotify)
    }

    @Test
    fun `formatDistanceLabel renders meters below 1km and km above`() {
        assertEquals("320 m", ContingencyEvaluator.formatDistanceLabel(320.4))
        assertEquals("4.5 km", ContingencyEvaluator.formatDistanceLabel(4500.0))
        assertNull(ContingencyEvaluator.formatDistanceLabel(null))
    }

    @Test
    fun `per-activity event duration updates EventTimelineCalculator accurately`() {
        val settings = LabSettings(
            prepMinutes = 30,
            travelBufferMinutes = 10,
            internalTransferInMinutes = 10,
            setupMinutes = 20,
            preStartMarginMinutes = 10,
            eventMinutes = 120,
            teardownMinutes = 15,
            internalTransferOutMinutes = 10,
        )

        val timeline45 = EventTimelineCalculator.calculate(
            eventDate = eventDate,
            eventTime = LocalTime.of(18, 0),
            settings = settings,
            routeTravelMinutes = 20,
            override = ActivityTimelineOverride(activityId = "a1", eventMinutes = 45),
        )
        val timeline90 = EventTimelineCalculator.calculate(
            eventDate = eventDate,
            eventTime = LocalTime.of(18, 0),
            settings = settings,
            routeTravelMinutes = 20,
            override = ActivityTimelineOverride(activityId = "a1", eventMinutes = 90),
        )

        assertEquals(45, timeline45.config.eventMinutes)
        assertEquals(LocalTime.of(18, 45), timeline45.eventEndAt.toLocalTime())
        assertEquals(LocalTime.of(19, 0), timeline45.teardownEndAt.toLocalTime())
        assertEquals(LocalTime.of(19, 10), timeline45.freeAt.toLocalTime())

        assertEquals(90, timeline90.config.eventMinutes)
        assertEquals(LocalTime.of(19, 30), timeline90.eventEndAt.toLocalTime())
        assertEquals(LocalTime.of(19, 55), timeline90.freeAt.toLocalTime())
        assertEquals(45, timeline90.totalMinutes - timeline45.totalMinutes)
    }

    @Test
    fun `ScheduleConflictChecker respects custom per-activity event duration`() {
        val settings = LabSettings(
            setupMinutes = 15,
            eventMinutes = 120,
            teardownMinutes = 15,
        )
        val existing45MinGig = Activity(
            id = "gig-1",
            ownerId = "u1",
            title = "Cóctel",
            supplierName = "Lina",
            venueName = "Hard Rock",
            eventDate = eventDate,
            eventTime = LocalTime.of(18, 0),
            amount = BigDecimal("8000"),
            currency = Currency.DOP,
            paymentStatus = PaymentStatus.PENDIENTE,
        )

        // With 120 min default, 19:30 overlaps 18:00..20:15
        val conflictWithDefault = ScheduleConflictChecker.findConflict(
            candidateDate = eventDate,
            candidateTime = LocalTime.of(19, 30),
            existingActivities = listOf(existing45MinGig),
            settings = settings,
        )
        assertNotNull(conflictWithDefault)

        // With 45 min duration on gig-1 (17:45..19:00), 19:30 (19:15..20:30) does NOT conflict!
        val conflictWithCustom45 = ScheduleConflictChecker.findConflict(
            candidateDate = eventDate,
            candidateTime = LocalTime.of(19, 30),
            existingActivities = listOf(existing45MinGig),
            settings = settings,
            candidateEventMinutes = 45,
            activityEventMinutesProvider = { if (it.id == "gig-1") 45 else null },
        )
        assertNull(conflictWithCustom45)
    }

    @Test
    fun `SupplierQuickMessageBuilder builds DELAY preset message for Modo Contingencia`() {
        val msg = SupplierQuickMessageBuilder.buildPresetMessage(
            preset = SupplierMessagePreset.DELAY,
            supplierDisplayName = "Lina",
            venueName = "Secrets Cap Cana",
            eventTime = LocalTime.of(19, 0),
        )

        assertTrue(msg.contains("Hola Lina"))
        assertTrue(msg.contains("Secrets Cap Cana"))
        assertTrue(msg.contains("en camino"))
        assertTrue(msg.contains("contratiempo"))
    }
}
