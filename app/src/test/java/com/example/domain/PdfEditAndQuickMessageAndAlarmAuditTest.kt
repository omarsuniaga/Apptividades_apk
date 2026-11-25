package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.BankAccount
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.DepartureTimeCalculator
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.ReceivableStatus
import com.example.domain.rules.StatementLineItem
import com.example.domain.rules.SupplierMessagePreset
import com.example.domain.rules.SupplierQuickMessageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class PdfEditAndQuickMessageAndAlarmAuditTest {

    // -------------------------------------------------------------------------
    // 1. PDF Content Editor — AccountStatementBuilder.recalculate(...)
    // -------------------------------------------------------------------------

    @Test
    fun recalculate_updatesTotalsOverdueFileNameAndAutoCaptionAfterEditsAndDiscount() {
        val today = LocalDate.of(2026, 4, 20)
        val original = AccountStatementBuilder.build(
            supplierName = "Lina Eventos",
            currency = Currency.DOP,
            activities = listOf(
                Activity(
                    id = "a1",
                    ownerId = "u1",
                    title = "Boda",
                    supplierName = "Lina Eventos",
                    venueName = "Secrets Cap Cana",
                    eventDate = LocalDate.of(2026, 2, 10),
                    eventTime = LocalTime.of(18, 0),
                    amount = BigDecimal("18000"),
                    currency = Currency.DOP,
                    paymentStatus = PaymentStatus.PENDIENTE,
                ),
                Activity(
                    id = "a2",
                    ownerId = "u1",
                    title = "Cóctel",
                    supplierName = "Lina Eventos",
                    venueName = "Hard Rock",
                    eventDate = LocalDate.of(2026, 3, 25),
                    eventTime = LocalTime.of(19, 0),
                    amount = BigDecimal("12000"),
                    currency = Currency.DOP,
                    paymentStatus = PaymentStatus.PENDIENTE,
                ),
            ),
            bankAccounts = listOf(
                BankAccount(
                    id = "b1",
                    label = "Popular DOP",
                    bankName = "Banco Popular",
                    holderName = "Omar Eduardo",
                    accountNumber = "123456789",
                    currency = Currency.DOP,
                    isDefault = true,
                )
            ),
            today = today,
        )

        // Artist edits title, adjusts second item, adds a negative discount/abono line item
        val discountLine = StatementLineItem(
            activityId = "manual_1",
            eventDate = today,
            eventTime = null,
            serviceDescription = "Abono recibido",
            venueName = "—",
            status = ReceivableStatus.ESTE_MES,
            overdueDays = 0L,
            statusLabel = "Ajuste",
            amount = BigDecimal("-5000"),
        )
        val edited = AccountStatementBuilder.recalculate(
            statement = original.copy(
                documentTitle = "Resumen de actividades · Corregido",
                supplierDisplayName = "Lina Bodas RD",
                items = listOf(
                    original.items[0],
                    original.items[1].copy(amount = BigDecimal("15000")),
                    discountLine,
                ),
                closingQuote = "",
                gratitudeMessage = "",
            ),
            customShareCaptionEdited = false,
        )

        assertEquals("Resumen de actividades · Corregido", edited.documentTitle)
        assertEquals(BigDecimal("28000"), edited.totalPending) // 18000 + 15000 - 5000
        assertEquals(BigDecimal("18000"), edited.overdueTotal)
        assertEquals("resumen-de-actividades-lina-bodas-rd-2026-04-20.pdf", edited.fileName)
        assertTrue(edited.shareCaption.contains("Lina Bodas RD"))
        assertTrue(edited.shareCaption.contains("3 servicios"))
        assertTrue(edited.shareCaption.contains("28,000"))
        assertEquals("", edited.closingQuote)
        assertEquals("", edited.gratitudeMessage)
    }

    @Test
    fun recalculate_preservesCustomShareCaptionWhenUserEditedIt() {
        val today = LocalDate.of(2026, 4, 20)
        val base = AccountStatementBuilder.build(
            supplierName = "José",
            currency = Currency.USD,
            activities = listOf(
                Activity(
                    id = "a1",
                    ownerId = "u1",
                    title = "Evento",
                    supplierName = "José",
                    venueName = "Eden Roc",
                    eventDate = LocalDate.of(2026, 3, 15),
                    amount = BigDecimal("400"),
                    currency = Currency.USD,
                    paymentStatus = PaymentStatus.PENDIENTE,
                )
            ),
            bankAccounts = emptyList(),
            today = today,
        )

        val edited = AccountStatementBuilder.recalculate(
            statement = base.copy(shareCaption = "Hola José, aquí va el documento ajustado."),
            customShareCaptionEdited = true,
        )

        assertEquals("Hola José, aquí va el documento ajustado.", edited.shareCaption)
    }

    // -------------------------------------------------------------------------
    // 2. Quick Supplier Communication — SupplierQuickMessageBuilder
    // -------------------------------------------------------------------------

    @Test
    fun buildPresetMessage_generatesArrivedFinishedAndCustomMessages() {
        val arrived = SupplierQuickMessageBuilder.buildPresetMessage(
            preset = SupplierMessagePreset.ARRIVED,
            supplierDisplayName = "Lina",
            venueName = "Sanctuary Cap Cana",
            eventTime = LocalTime.of(18, 30),
        )
        assertTrue(arrived.startsWith("Hola Lina,"))
        assertTrue(arrived.contains("ya llegué a Sanctuary Cap Cana"))
        assertTrue(arrived.contains("6:30"))

        val finished = SupplierQuickMessageBuilder.buildPresetMessage(
            preset = SupplierMessagePreset.FINISHED,
            supplierDisplayName = "Lina",
            venueName = "Sanctuary Cap Cana",
            eventTime = LocalTime.of(18, 30),
            extraSituation = "Dejé el cable extra en recepción.",
        )
        assertTrue(finished.contains("ya terminé la actuación en Sanctuary Cap Cana"))
        assertTrue(finished.endsWith("Dejé el cable extra en recepción."))

        val customNoSupplier = SupplierQuickMessageBuilder.buildPresetMessage(
            preset = SupplierMessagePreset.CUSTOM,
            supplierDisplayName = null,
            venueName = "",
            eventTime = null,
        )
        assertTrue(customNoSupplier.startsWith("Hola,"))
        assertTrue(customNoSupplier.contains("en el lugar del evento"))
    }

    @Test
    fun appendSnippet_appendsOnceWithoutDuplicating() {
        val base = "Hola Lina, ya llegué."
        val snippet = SupplierQuickMessageBuilder.quickSituationSnippets.first()
        val appended = SupplierQuickMessageBuilder.appendSnippet(base, snippet)
        assertEquals("$base\n$snippet", appended)

        // Calling again with same snippet is a no-op
        val second = SupplierQuickMessageBuilder.appendSnippet(appended, snippet)
        assertEquals(appended, second)
    }

    @Test
    fun buildWhatsAppUrl_normalizesDominicanPhoneAndReturnsNullWhenBlank() {
        val url = SupplierQuickMessageBuilder.buildWhatsAppUrl("809-555-1234", "Hola, ya llegué")
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://wa.me/18095551234?text="))

        assertNull(SupplierQuickMessageBuilder.buildWhatsAppUrl(null, "Hola"))
        assertNull(SupplierQuickMessageBuilder.buildWhatsAppUrl("   ", "Hola"))
    }

    // -------------------------------------------------------------------------
    // 3. Notification / Alarm System Audit — Timeline & Alarm Alignment
    // -------------------------------------------------------------------------

    @Test
    fun calculateFromTimeline_matchesEventTimelineCalculatorIncludingInternalTransferAndOverride() {
        val date = LocalDate.of(2026, 6, 12)
        val time = LocalTime.of(19, 0)
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
        val override = ActivityTimelineOverride(
            travelMinutes = 35,
            internalTransferInMinutes = 20,
            preStartMarginMinutes = 10,
        )

        val timeline = EventTimelineCalculator.calculate(
            eventDate = date,
            eventTime = time,
            settings = settings,
            routeTravelMinutes = 25, // overridden by override.travelMinutes = 35
            override = override,
        )
        val alarmSchedule = DepartureTimeCalculator.calculateFromTimeline(
            eventDate = date,
            eventTime = time,
            travelMinutes = 25,
            settings = settings,
            override = override,
        )

        // Leave = 19:00 - (10 preMargin + 45 setup + 20 internalIn + 15 buffer + 35 travel) = 19:00 - 125m = 16:55
        // Prepare = 16:55 - 60m = 15:55
        assertEquals(timeline.leaveAt, alarmSchedule.leaveAt)
        assertEquals(timeline.prepareStartAt, alarmSchedule.prepareAt)
        assertEquals(LocalTime.of(16, 55), alarmSchedule.leaveAt.toLocalTime())
        assertEquals(LocalTime.of(15, 55), alarmSchedule.prepareAt.toLocalTime())
        assertEquals(35, alarmSchedule.travelMinutes)
    }

    @Test
    fun calculateFromTimeline_worksForActivitiesWithoutCoordinates() {
        val date = LocalDate.of(2026, 6, 12)
        val time = LocalTime.of(18, 0)
        val settings = LabSettings(
            prepMinutes = 60,
            travelBufferMinutes = 15,
            internalTransferInMinutes = 15,
            setupMinutes = 45,
            preStartMarginMinutes = 15,
        )

        // travelMinutes = null (activity without map pin) still produces exact Prepare & Leave alarms
        val schedule = DepartureTimeCalculator.calculateFromTimeline(
            eventDate = date,
            eventTime = time,
            travelMinutes = null,
            settings = settings,
            override = null,
        )

        // 18:00 - (15 preMargin + 45 setup + 15 internalIn) = 16:45 leave; 15:45 prepare
        assertEquals(LocalTime.of(16, 45), schedule.leaveAt.toLocalTime())
        assertEquals(LocalTime.of(15, 45), schedule.prepareAt.toLocalTime())
        assertEquals(0, schedule.travelMinutes)
    }
}
