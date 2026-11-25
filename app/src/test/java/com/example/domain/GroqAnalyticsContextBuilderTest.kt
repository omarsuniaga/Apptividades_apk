package com.example.domain

import com.example.data.port.PrivacyPreferences
import com.example.data.remote.GroqAnalysisPayloadDto
import com.example.data.repository.GroqRepository
import com.example.domain.model.Activity
import com.example.domain.model.ActivityExpenseEntry
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.Currency
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.GroqAgendaFinanceAnalysisRequest
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.GroqAnalyticsContextBuilder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class GroqAnalyticsContextBuilderTest {

    private val today = LocalDate.of(2026, 5, 15)
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun sampleActivity(
        id: String,
        date: LocalDate,
        time: LocalTime? = LocalTime.of(19, 0),
        supplier: String = "Eventos Caribe SRL",
        venue: String = "Hotel Paradisus Palma Real",
        amount: String = "15000",
        status: PaymentStatus = PaymentStatus.PAGADO,
    ) = Activity(
        id = id,
        ownerId = "user-1",
        title = "Boda en terraza",
        eventDate = date,
        eventTime = time,
        venueName = venue,
        supplierName = supplier,
        amount = BigDecimal(amount),
        currency = Currency.DOP,
        paymentStatus = status,
    )

    @Test
    fun buildIncludesFinancialHistoryNetProfitRoutesAndMultiGigGaps() {
        val gig1 = sampleActivity(
            id = "a1",
            date = LocalDate.of(2026, 4, 10),
            time = LocalTime.of(16, 0),
            supplier = "Producciones Bávaro",
            venue = "Dreams Macao Beach",
            amount = "18000",
            status = PaymentStatus.PAGADO,
        )
        val gig2 = sampleActivity(
            id = "a2",
            date = LocalDate.of(2026, 4, 10),
            time = LocalTime.of(18, 0),
            supplier = "Bodas Punta Cana",
            venue = "Hard Rock Hotel",
            amount = "22000",
            status = PaymentStatus.PENDIENTE,
        )
        val overdueGig = sampleActivity(
            id = "a3",
            date = LocalDate.of(2026, 2, 14),
            time = LocalTime.of(20, 0),
            supplier = "Producciones Bávaro",
            venue = "Dreams Macao Beach",
            amount = "15000",
            status = PaymentStatus.PENDIENTE,
        )

        val routes = mapOf(
            "a1" to CachedActivityRoute(
                activityId = "a1",
                originLat = 18.68,
                originLng = -68.40,
                destLat = 18.78,
                destLng = -68.53,
                travelMinutes = 35,
                distanceKm = 28.4,
                fromOsrm = true,
            ),
            "a2" to CachedActivityRoute(
                activityId = "a2",
                originLat = 18.68,
                originLng = -68.40,
                destLat = 18.73,
                destLng = -68.46,
                travelMinutes = 20,
                distanceKm = 14.6,
                fromOsrm = true,
            ),
        )

        val finances = mapOf(
            "a1" to ActivityFinancialRecord(
                activityId = "a1",
                expenses = listOf(
                    ActivityExpenseEntry(
                        category = ExpenseCategory.TRANSPORT,
                        amount = BigDecimal("2500"),
                        note = "Combustible y peaje",
                    ),
                ),
            ),
        )

        val departures = mapOf(
            "a2" to ScheduledDeparture(
                activityId = "a2",
                eventDate = LocalDate.of(2026, 5, 20),
                eventTime = LocalTime.of(19, 0),
                venueName = "Hard Rock Hotel",
                travelMinutes = 20,
                distanceKm = 14.6,
                prepareAtEpochMillis = 1779307800000L,
                leaveAtEpochMillis = 1779311400000L,
                estimatedArrivalTime = LocalTime.of(18, 20),
                isOsrmEstimated = true,
            ),
        )

        val payload = GroqAnalyticsContextBuilder.build(
            activities = listOf(gig1, gig2, overdueGig),
            routes = routes,
            scheduledDepartures = departures,
            financialRecords = finances,
            settings = LabSettings(
                defaultCurrency = Currency.DOP,
                monthlyGoal = BigDecimal("80000"),
                eventMinutes = 60,
            ),
            privacyPreferences = PrivacyPreferences(),
            today = today,
            anonymizeClientNames = false,
        )

        assertEquals(3, payload.indexedActivitiesCount)
        assertEquals(2, payload.indexedRoutesCount)
        assertTrue(payload.contextData.contains("<datos_estadisticos>"))
        assertTrue(payload.contextData.contains("</datos_estadisticos>"))
        assertTrue(payload.contextData.contains("distancia_total_km: 43.0"))
        assertTrue(payload.contextData.contains("Dreams Macao Beach"))
        assertTrue(payload.contextData.contains("brecha 60 min"))
        assertTrue(payload.contextData.contains("transporte="))
        assertTrue(payload.contextData.contains("Producciones Bávaro"))
        assertTrue(payload.systemPrompt.contains("datos_estadisticos"))
    }

    @Test
    fun repositorySerializesCurrentAgendaAndFinancialHistoryAsValidJsonPayload() {
        val upcomingGig = sampleActivity(
            id = "u1",
            date = LocalDate.of(2026, 5, 22),
            time = LocalTime.of(18, 30),
            supplier = "Resort Punta Cana",
            venue = "Secrets Cap Cana",
            amount = "24000",
            status = PaymentStatus.PENDIENTE,
        )
        val pastGig = sampleActivity(
            id = "p1",
            date = LocalDate.of(2026, 3, 14),
            time = LocalTime.of(20, 0),
            supplier = "Resort Punta Cana",
            venue = "Secrets Cap Cana",
            amount = "20000",
            status = PaymentStatus.PAGADO,
        )
        val routes = mapOf(
            "u1" to CachedActivityRoute(
                activityId = "u1",
                originLat = 18.68,
                originLng = -68.40,
                destLat = 18.46,
                destLng = -68.40,
                travelMinutes = 32,
                distanceKm = 26.5,
                fromOsrm = true,
            ),
        )
        val request = GroqAgendaFinanceAnalysisRequest(
            activities = listOf(upcomingGig, pastGig),
            routes = routes,
            scheduledDepartures = emptyMap(),
            financialRecords = emptyMap(),
            settings = LabSettings(defaultCurrency = Currency.DOP, monthlyGoal = BigDecimal("90000")),
            privacyPreferences = PrivacyPreferences(),
            today = today,
            anonymizeClientNames = false,
        )

        val json = GroqRepository.buildAgendaAndFinanceJsonPayload(request, moshi)
        assertTrue(json.contains("\"agenda_actual\""))
        assertTrue(json.contains("\"historico_financiero\""))
        assertTrue(json.contains("\"inteligencia_rutas\""))
        assertTrue(json.contains("Secrets Cap Cana"))

        val parsed = moshi.adapter(GroqAnalysisPayloadDto::class.java).fromJson(json)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.currentAgenda.upcomingGigs.size)
        assertEquals("Secrets Cap Cana", parsed.currentAgenda.upcomingGigs.first().venue)
        assertEquals(26.5, parsed.currentAgenda.upcomingGigs.first().distanceKm)
        assertEquals(20000.0, parsed.financialHistory.last12Months.totalBilled, 0.01)
        assertEquals(1, parsed.routeIntelligence.measuredRoutesCount)
    }

    @Test
    fun repositoryAnonymizesClientNamesInsideJsonPayloadWhenEnabled() {
        val act1 = sampleActivity(
            id = "b1",
            date = LocalDate.of(2026, 5, 25),
            supplier = "Cliente Secreto VIP",
            venue = "Hotel Casa de Campo",
            amount = "30000",
            status = PaymentStatus.PENDIENTE,
        )
        val act2 = sampleActivity(
            id = "b2",
            date = LocalDate.of(2026, 2, 12),
            supplier = "Agencia Exclusiva RD",
            venue = "Hotel Eden Roc",
            amount = "25000",
            status = PaymentStatus.PENDIENTE,
        )

        val json = GroqRepository.buildAgendaAndFinanceJsonPayload(
            request = GroqAgendaFinanceAnalysisRequest(
                activities = listOf(act1, act2),
                routes = emptyMap(),
                scheduledDepartures = emptyMap(),
                financialRecords = emptyMap(),
                settings = LabSettings(defaultCurrency = Currency.DOP, monthlyGoal = BigDecimal("100000")),
                privacyPreferences = PrivacyPreferences(),
                today = today,
                anonymizeClientNames = true,
            ),
            moshi = moshi,
        )

        assertFalse(json.contains("Cliente Secreto VIP"))
        assertFalse(json.contains("Agencia Exclusiva RD"))
        assertTrue(json.contains("Proveedor A"))
        assertTrue(json.contains("Proveedor B"))
    }

    @Test
    fun groqApiKeyValidationAndMaskingWorkAsExpected() {
        assertNotNull(GroqRepository.validateKeyFormat(""))
        assertNotNull(GroqRepository.validateKeyFormat("sk-not-a-groq-key-1234567890"))
        assertNotNull(GroqRepository.validateKeyFormat("gsk_short"))
        assertNotNull(GroqRepository.validateKeyFormat("gsk_has space inside key 12345678"))

        val validSample = "gsk_1234567890abcdefghij1234567890"
        assertNull(GroqRepository.validateKeyFormat(validSample))
        assertEquals("gsk_123••••••7890", GroqRepository.maskApiKey(validSample))
    }
}
