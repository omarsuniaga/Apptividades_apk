package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteCalculator
import com.example.domain.rules.QuoteLineItem
import com.example.widget.NextGigWidgetStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class QuoteAndWidgetLogicTest {

    @Test
    fun eventQuote_calculatesSubtotalDepositAndBalanceAccurately() {
        val quote = EventQuote(
            quoteNumber = QuoteCalculator.buildQuoteNumber(LocalDate.of(2026, 6, 15), 3),
            clientName = "Hotel Paradisus",
            eventTitle = "Boda en playa",
            eventDate = LocalDate.of(2026, 7, 20),
            eventTime = LocalTime.of(17, 30),
            venueName = "Paradisus Palma Real",
            issueDate = LocalDate.of(2026, 6, 15),
            validUntilDate = LocalDate.of(2026, 6, 30),
            currency = Currency.USD,
            depositPercent = 30,
            items = listOf(
                QuoteLineItem("1", "Ceremonia", quantity = 1, unitPrice = BigDecimal("450.00")),
                QuoteLineItem("2", "Hora adicional cóctel", quantity = 2, unitPrice = BigDecimal("175.00")),
            ),
            issuerName = "Omar Sax",
        )

        assertEquals("COT-20260615-03", quote.quoteNumber)
        assertEquals(BigDecimal("800.00"), quote.subtotal)
        assertEquals(BigDecimal("240.00"), quote.depositAmount)
        assertEquals(BigDecimal("560.00"), quote.balanceDue)

        val fileName = QuoteCalculator.buildFileName(quote.clientName, quote.issueDate)
        assertTrue(fileName.startsWith("cotizacion-hotel-paradisus-2026-06-15"))
        assertTrue(fileName.endsWith(".pdf"))

        val caption = QuoteCalculator.buildShareCaption(quote)
        assertTrue(caption.contains("COT-20260615-03"))
        assertTrue(caption.contains("30%"))
    }

    @Test
    fun eventQuote_withZeroDeposit_leavesFullBalance() {
        val quote = EventQuote(
            quoteNumber = "COT-20260615-01",
            clientName = "María",
            eventTitle = "Cumpleaños",
            eventDate = null,
            eventTime = null,
            venueName = "",
            issueDate = LocalDate.of(2026, 6, 15),
            validUntilDate = LocalDate.of(2026, 6, 30),
            currency = Currency.DOP,
            depositPercent = 0,
            items = listOf(QuoteLineItem("1", "Toque", quantity = 1, unitPrice = BigDecimal("15000"))),
            issuerName = "Omar",
        )

        assertEquals(BigDecimal("15000.00"), quote.subtotal)
        assertEquals(BigDecimal("0.00"), quote.depositAmount)
        assertEquals(BigDecimal("15000.00"), quote.balanceDue)
    }

    @Test
    fun nextGigWidget_selectsNearestUpcomingAndFormatsDeparture() {
        val today = LocalDate.of(2026, 6, 15)
        val nowTime = LocalTime.of(14, 0)

        val pastGig = sampleActivity("past", today.minusDays(1), LocalTime.of(20, 0), "Old Venue")
        val finishedToday = sampleActivity("done", today, LocalTime.of(10, 0), "Morning Venue")
        val upcomingToday = sampleActivity("today1", today, LocalTime.of(19, 30), "Hard Rock Hotel", "Bodas Caribe")
        val futureGig = sampleActivity("future1", today.plusDays(2), LocalTime.of(18, 0), "Dreams Macao")

        val leaveMillis = today.atTime(18, 15).atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
        val dep = ScheduledDeparture(
            activityId = "today1",
            venueName = "Hard Rock Hotel",
            eventDate = today,
            eventTime = LocalTime.of(19, 30),
            latitude = 18.7,
            longitude = -68.4,
            travelMinutes = 30,
            distanceKm = 18.2,
            prepareAtEpochMillis = leaveMillis - 3_600_000L,
            leaveAtEpochMillis = leaveMillis,
            estimatedArrivalTime = LocalTime.of(18, 45),
            isOsrmEstimated = true,
            alarmsScheduled = true,
        )

        val snapshot = NextGigWidgetStore.computeSnapshot(
            activities = listOf(futureGig, pastGig, finishedToday, upcomingToday),
            scheduledDepartures = mapOf("today1" to dep),
            today = today,
            nowTime = nowTime,
        )

        assertTrue(snapshot.signedIn)
        assertTrue(snapshot.hasGig)
        assertTrue(snapshot.isToday)
        assertEquals("Hard Rock Hotel", snapshot.title)
        assertTrue(snapshot.subtitle.contains("Bodas Caribe"))
        assertTrue(snapshot.dateTimeLabel.startsWith("Hoy"))
        assertTrue(snapshot.departureLabel.startsWith("Sal a las"))
    }

    @Test
    fun nextGigWidget_returnsEmptyStateWhenNoUpcomingGigs() {
        val today = LocalDate.of(2026, 6, 15)
        val pastGig = sampleActivity("past", today.minusDays(3), LocalTime.of(20, 0), "Old Venue")

        val snapshot = NextGigWidgetStore.computeSnapshot(
            activities = listOf(pastGig),
            scheduledDepartures = emptyMap(),
            today = today,
            nowTime = LocalTime.of(15, 0),
        )

        assertTrue(snapshot.signedIn)
        assertFalse(snapshot.hasGig)
    }

    private fun sampleActivity(
        id: String,
        date: LocalDate,
        time: LocalTime?,
        venue: String,
        supplier: String = "Proveedor",
    ): Activity = Activity(
        id = id,
        ownerId = "owner-1",
        title = venue,
        supplierName = supplier,
        venueName = venue,
        eventDate = date,
        eventTime = time,
        amount = BigDecimal("10000"),
        currency = Currency.DOP,
        paymentStatus = PaymentStatus.PENDIENTE,
    )
}
