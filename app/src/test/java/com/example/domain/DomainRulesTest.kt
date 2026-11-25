package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityType
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.ActivityTitleDeriver
import com.example.domain.rules.BalanceAndOverviewCalculator
import com.example.domain.rules.CsvExporter
import com.example.domain.rules.DepartureTimeCalculator
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.NameNormalizer
import com.example.domain.rules.ReceivableStatus
import com.example.domain.rules.ReceivablesCalculator
import com.example.domain.rules.ScheduleConflictChecker
import com.example.domain.rules.WhatsAppReminderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class DomainRulesTest {

    private val refToday = LocalDate.of(2026, 11, 20)

    private fun gig(
        id: String,
        date: LocalDate,
        time: LocalTime? = LocalTime.of(18, 0),
        supplier: String = "Crisbel",
        venue: String = "Hotel Club Med Punta Cana",
        amount: Long = 6000,
        currency: Currency = Currency.DOP,
        status: PaymentStatus = PaymentStatus.PAGADO,
        type: ActivityType = ActivityType.EVENTUAL,
    ) = Activity(
        id = id,
        ownerId = "owner-1",
        supplierName = supplier,
        title = supplier,
        activityType = type,
        eventDate = date,
        eventTime = time,
        venueName = venue,
        amount = BigDecimal.valueOf(amount),
        currency = currency,
        paymentStatus = status,
    )

    // Rule 2.3 #1: Month-in-arrears receivables & reference numbers (today = 2026-11-20)
    @Test
    fun `receivables rule reproduces reference overdue numbers for 2026-11-20`() {
        val activities = listOf(
            // José David & Jose David (accent variant unified): 3 gigs from Aug 4 & Sep -> RD$ 15,000 overdue (108 days)
            gig("jd1", LocalDate.of(2026, 8, 4), supplier = "José David", amount = 5000, status = PaymentStatus.PENDIENTE),
            gig("jd2", LocalDate.of(2026, 8, 18), supplier = "Jose David", amount = 5000, status = PaymentStatus.PENDIENTE),
            gig("jd3", LocalDate.of(2026, 9, 12), supplier = "José David", amount = 5000, status = PaymentStatus.PENDIENTE),
            // Romina: 1 gig on 27 Sep -> RD$ 7,000 overdue (54 days)
            gig("rom1", LocalDate.of(2026, 9, 27), supplier = "Romina", amount = 7000, status = PaymentStatus.PENDIENTE),
            // October gig -> ESTE_MES (collect in November)
            gig("oct1", LocalDate.of(2026, 10, 15), supplier = "FJ", amount = 7000, status = PaymentStatus.PENDIENTE),
            // November past gig -> PROXIMO_MES (collect in December)
            gig("nov1", LocalDate.of(2026, 11, 3), supplier = "Crisbel", amount = 6000, status = PaymentStatus.PENDIENTE),
            // Future gig (not yet played) -> excluded from receivables
            gig("fut1", LocalDate.of(2026, 11, 26), supplier = "FJ", amount = 14000, status = PaymentStatus.PENDIENTE),
            // Today's gig -> strictly event_date < today, so excluded from receivables
            gig("tod1", refToday, supplier = "Crisbel", amount = 6000, status = PaymentStatus.PENDIENTE),
            // Already paid -> excluded
            gig("paid1", LocalDate.of(2026, 8, 10), supplier = "Crisbel", amount = 6000, status = PaymentStatus.PAGADO),
        )

        val summary = ReceivablesCalculator.calculate(activities, refToday, Currency.DOP)

        assertEquals(BigDecimal("22000"), summary.overdueTotal)
        assertEquals(BigDecimal("7000"), summary.dueThisMonthTotal)
        assertEquals(BigDecimal("6000"), summary.dueNextMonthTotal)
        assertEquals(BigDecimal("35000"), summary.totalReceivable)

        assertEquals(2, summary.overdueGroups.size)
        val joseDavid = summary.overdueGroups[0]
        assertEquals("José David", joseDavid.supplierDisplayName)
        assertEquals(3, joseDavid.count)
        assertEquals(BigDecimal("15000"), joseDavid.totalAmount)
        assertEquals(108L, joseDavid.daysOutstanding)
        assertEquals(ReceivableStatus.VENCIDO, joseDavid.status)

        val romina = summary.overdueGroups[1]
        assertEquals("Romina", romina.supplierDisplayName)
        assertEquals(1, romina.count)
        assertEquals(BigDecimal("7000"), romina.totalAmount)
        assertEquals(54L, romina.daysOutstanding)
    }

    // Rule 2.3 #2: Name normalization & prefix merging
    @Test
    fun `name normalization unifies accents and Club Med spellings`() {
        val supplierRes = NameNormalizer.buildSupplierResolution(
            listOf("Jose David", "José David", "José David")
        )
        assertEquals(
            supplierRes.canonicalKey("Jose David"),
            supplierRes.canonicalKey("José David"),
        )
        assertEquals("José David", supplierRes.displayName("Jose David"))

        // Tie-breaker prefers longer and accented spelling
        val tieRes = NameNormalizer.buildSupplierResolution(listOf("Jose David", "José David"))
        assertEquals("José David", tieRes.displayName("Jose David"))

        val venueRes = NameNormalizer.buildVenueResolution(
            listOf(
                "Hotel ClubMed Punta Cana",
                "Hotel Club Med Punta Cana",
                "hotel club med",
                "Hotel Club Med Punta Cana",
            )
        )
        val k1 = venueRes.canonicalKey("Hotel ClubMed Punta Cana")
        val k2 = venueRes.canonicalKey("Hotel Club Med Punta Cana")
        val k3 = venueRes.canonicalKey("hotel club med")
        assertEquals(k1, k2)
        assertEquals(k2, k3)
        assertEquals("Hotel Club Med Punta Cana", venueRes.displayName("hotel club med"))
        assertEquals(2, venueRes.totalMergedVariants) // 3 distinct spellings -> 2 merged into canonical
    }

    // Rule 2.3 #3: Usual rate per supplier & currency, recency wins ties
    @Test
    fun `usual rate picks most frequent amount and breaks ties by most recent date`() {
        val activities = listOf(
            gig("1", LocalDate.of(2026, 1, 10), supplier = "Crisbel", amount = 5500),
            gig("2", LocalDate.of(2026, 2, 10), supplier = "Crisbel", amount = 6000),
            gig("3", LocalDate.of(2026, 3, 10), supplier = "Crisbel", amount = 5500),
            gig("4", LocalDate.of(2026, 4, 10), supplier = "Crisbel", amount = 6000),
            gig("5", LocalDate.of(2026, 5, 10), supplier = "Crisbel", amount = 250, currency = Currency.USD),
        )

        // 5500 (2 times, latest Mar 10) vs 6000 (2 times, latest Apr 10) -> 6000 wins tie!
        assertEquals(
            BigDecimal.valueOf(6000),
            BalanceAndOverviewCalculator.usualRate(activities, "Crisbel", Currency.DOP),
        )
        assertEquals(
            BigDecimal.valueOf(250),
            BalanceAndOverviewCalculator.usualRate(activities, "Crisbel", Currency.USD),
        )
    }

    // Rule 2.3 #4: Frequent suppliers top 3 in last 365 days
    @Test
    fun `frequent suppliers returns top 3 in last 365 days`() {
        val activities = listOf(
            gig("1", LocalDate.of(2026, 10, 1), supplier = "Crisbel"),
            gig("2", LocalDate.of(2026, 10, 2), supplier = "Crisbel"),
            gig("3", LocalDate.of(2026, 10, 3), supplier = "Crisbel"),
            gig("4", LocalDate.of(2026, 10, 4), supplier = "FJ"),
            gig("5", LocalDate.of(2026, 10, 5), supplier = "FJ"),
            gig("6", LocalDate.of(2026, 10, 6), supplier = "José David"),
            gig("7", LocalDate.of(2026, 10, 7), supplier = "Jose David"),
            gig("8", LocalDate.of(2026, 10, 8), supplier = "Romina"),
            // Older than 365 days -> ignored
            gig("old1", LocalDate.of(2024, 1, 1), supplier = "OldAgency"),
            gig("old2", LocalDate.of(2024, 1, 2), supplier = "OldAgency"),
            gig("old3", LocalDate.of(2024, 1, 3), supplier = "OldAgency"),
            gig("old4", LocalDate.of(2024, 1, 4), supplier = "OldAgency"),
        )

        val top3 = BalanceAndOverviewCalculator.frequentSuppliers(activities, refToday, limit = 3)
        assertEquals(3, top3.size)
        assertEquals("Crisbel", top3[0].displayName)
        assertEquals(3, top3[0].gigCount)
    }

    // Rule 2.3 #5: Local schedule conflict check
    @Test
    fun `schedule conflict detects overlap using setup event and teardown minutes`() {
        val settings = LabSettings(setupMinutes = 45, eventMinutes = 120, teardownMinutes = 45)
        // Existing gig at 12:15 -> reserved window [11:30, 15:00]
        val existing = listOf(
            gig("g1", LocalDate.of(2026, 11, 27), time = LocalTime.of(12, 15), supplier = "Crisbel")
        )

        // 14:30 has setup start 13:45 < 15:00 -> conflict!
        assertNotNull(
            ScheduleConflictChecker.findConflict(
                candidateDate = LocalDate.of(2026, 11, 27),
                candidateTime = LocalTime.of(14, 30),
                existingActivities = existing,
                settings = settings,
            )
        )

        // 16:45 has setup start 16:00 >= 15:00 -> NO conflict!
        assertNull(
            ScheduleConflictChecker.findConflict(
                candidateDate = LocalDate.of(2026, 11, 27),
                candidateTime = LocalTime.of(16, 45),
                existingActivities = existing,
                settings = settings,
            )
        )

        // Self-exclusion when editing g1
        assertNull(
            ScheduleConflictChecker.findConflict(
                candidateDate = LocalDate.of(2026, 11, 27),
                candidateTime = LocalTime.of(12, 15),
                existingActivities = existing,
                settings = settings,
                excludeActivityId = "g1",
            )
        )
    }

    // Rules 2.3 #6 & #7: Reference numbers verification (Nov 2025 – Oct 2026 & Nov 2026)
    @Test
    fun `12 month balance and month overview reproduce exact reference metrics`() {
        val activities = mutableListOf<Activity>()
        var idx = 0

        // Construct 146 gigs across Nov 2025 - Oct 2026 totaling RD$ 1,021,260:
        // Crisbel: 58 gigs = 344,000 (~34%)
        // FJ: 40 gigs = 281,000 (~27%)
        // José David: 26 gigs = 144,000 (~14%) -> Top 3 = 769,000 = 75.3% ≈ 75%!
        // Others: 22 gigs = 252,260 (~25%)
        // Total = 146 gigs, RD$ 1,021,260 -> avg/month = 85,105, gigs/month = 12.166 -> 12.2, per gig = 6,995!
        val startYm = YearMonth.of(2025, 11)
        fun addBatch(supplier: String, count: Int, totalAmount: Long, venue: String) {
            val base = totalAmount / count
            val remainder = totalAmount % count
            repeat(count) { i ->
                val ym = startYm.plusMonths((i % 12).toLong())
                val amt = if (i == 0) base + remainder else base
                activities.add(
                    gig(
                        id = "b${idx++}",
                        date = ym.atDay((i % 25) + 1),
                        supplier = supplier,
                        venue = venue,
                        amount = amt,
                    )
                )
            }
        }

        addBatch("Crisbel", 58, 344_000, "Club Med Punta Cana")
        addBatch("FJ", 40, 281_000, "Sanctuary Cap Cana")
        addBatch("José David", 26, 144_000, "Meliá Palma Real")
        // 14 smaller suppliers totaling 22 gigs and RD$ 252,260 (~25%)
        val otherBase = 252_260L / 22L
        val otherRem = 252_260L % 22L
        repeat(22) { i ->
            val ym = startYm.plusMonths((i % 12).toLong())
            val amt = if (i == 0) otherBase + otherRem else otherBase
            activities.add(
                gig(
                    id = "other_${i}",
                    date = ym.atDay((i % 25) + 1),
                    supplier = "Agencia ${(i % 14) + 1}",
                    venue = "Ocean Blue",
                    amount = amt,
                )
            )
        }

        // Add Nov 2026: 8 gigs totaling RD$ 59,000 (6 played <= Nov 20, 2 upcoming > Nov 20)
        val novGigs = listOf(
            LocalDate.of(2026, 11, 3) to 7000L,
            LocalDate.of(2026, 11, 6) to 6000L,
            LocalDate.of(2026, 11, 13) to 6000L,
            LocalDate.of(2026, 11, 16) to 7000L,
            LocalDate.of(2026, 11, 20) to 6000L,
            LocalDate.of(2026, 11, 20) to 7000L,
            LocalDate.of(2026, 11, 26) to 14000L,
            LocalDate.of(2026, 11, 27) to 6000L,
        )
        novGigs.forEachIndexed { i, (date, amt) ->
            activities.add(
                gig("nov$i", date, amount = amt, status = PaymentStatus.PENDIENTE)
            )
        }

        val goal = BigDecimal("100000")
        val balance = BalanceAndOverviewCalculator.balance12Months(activities, refToday, goal, Currency.DOP)

        assertEquals(YearMonth.of(2025, 11), balance.startMonth)
        assertEquals(YearMonth.of(2026, 10), balance.endMonth)
        assertEquals(146, balance.totalGigs)
        assertEquals(BigDecimal("1021260"), balance.totalAmount)
        assertEquals(BigDecimal("85105"), balance.averagePerMonth)
        assertEquals("12.2", DominicanFormatter.formatOneDecimal(balance.gigsPerMonth))
        assertEquals(BigDecimal("6995"), balance.averagePerGig)
        assertEquals(75, balance.top3SharePercent)

        val overview = BalanceAndOverviewCalculator.monthOverview(activities, refToday, goal, Currency.DOP)
        assertEquals(BigDecimal("59000"), overview.billed)
        assertEquals(8, overview.totalActivities)
        assertEquals(6, overview.playedCount)
        assertEquals(2, overview.upcomingCount)
        assertEquals(59, overview.progressPercent)
        // ceil((100,000 - 59,000) / 6,995) = ceil(5.86) = 6 toques
        assertEquals(6, overview.missingGigsEstimate)
    }

    // Rule 2.3 #8: Departure time math
    @Test
    fun `departure time subtracts travel buffer setup and prep minutes accurately`() {
        val settings = LabSettings(
            setupMinutes = 45,
            travelBufferMinutes = 15,
            prepMinutes = 60,
        )
        // Gig at 12:15, travel = 25 min -> leave = 12:15 - (25 + 15 + 45) = 12:15 - 85m = 10:50
        // prepare = 10:50 - 60m = 09:50
        val schedule = DepartureTimeCalculator.calculate(
            eventDate = LocalDate.of(2026, 11, 20),
            eventTime = LocalTime.of(12, 15),
            travelMinutes = 25,
            settings = settings,
        )
        assertEquals(LocalDateTime.of(2026, 11, 20, 10, 50), schedule.leaveAt)
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 50), schedule.prepareAt)
    }

    @Test
    fun `whatsapp reminder formats Dominican phone and singular plural messages`() {
        assertEquals("18095551234", WhatsAppReminderBuilder.normalizeDominicanPhone("(809) 555-1234"))
        assertEquals("18295559999", WhatsAppReminderBuilder.normalizeDominicanPhone("+1 829-555-9999"))
        assertNull(WhatsAppReminderBuilder.normalizeDominicanPhone("   "))

        val single = ReceivablesCalculator.calculate(
            listOf(gig("r1", LocalDate.of(2026, 9, 27), supplier = "Romina", amount = 7000, status = PaymentStatus.PENDIENTE)),
            refToday,
        ).groups.first()
        assertEquals(
            "Hola Romina, ¿cómo estás? Te escribo por el toque del 27/09, pendiente de pago: RD$ 7,000. ¡Gracias!",
            WhatsAppReminderBuilder.buildMessage(single),
        )

        val multiple = ReceivablesCalculator.calculate(
            listOf(
                gig("j1", LocalDate.of(2026, 8, 4), supplier = "José David", amount = 5000, status = PaymentStatus.PENDIENTE),
                gig("j2", LocalDate.of(2026, 9, 12), supplier = "José David", amount = 5000, status = PaymentStatus.PENDIENTE),
            ),
            refToday,
        ).groups.first()
        assertEquals(
            "Hola José David, ¿cómo estás? Te escribo por los 2 toques del 04/08, 12/09, pendientes de pago: RD$ 10,000. ¡Gracias!",
            WhatsAppReminderBuilder.buildMessage(multiple),
        )
    }

    @Test
    fun `title derivation and CSV export with BOM follow contract`() {
        assertEquals("Crisbel", ActivityTitleDeriver.derive("Crisbel", "Boda", "Club Med"))
        assertEquals("Boda playa", ActivityTitleDeriver.derive("", "Boda playa\nDetalles", "Club Med"))
        assertEquals("Club Med", ActivityTitleDeriver.derive("", "", "Club Med"))
        assertEquals("Servicio musical", ActivityTitleDeriver.derive("", "", ""))

        val csv = CsvExporter.buildActivitiesCsv(
            listOf(gig("1", LocalDate.of(2026, 11, 20), venue = "Lopesan, Bávaro", amount = 7000))
        )
        assertTrue(csv.startsWith("\uFEFFFecha,Hora,Título,Proveedor"))
        assertTrue(csv.contains("\"Lopesan, Bávaro\""))
    }

    // Phase 2: Offline Haversine estimate, 5-min reschedule threshold, late arrival & 12h formatting
    @Test
    fun `departure engine offline estimate reschedule threshold and 12h formatting work accurately`() {
        // Bávaro center (18.6813, -68.4547) -> Club Med Punta Cana (18.5540, -68.3490) (~18 km -> ~25-45 min)
        val estimatedMins = DepartureTimeCalculator.estimateOfflineTravelMinutes(
            originLat = 18.6813,
            originLng = -68.4547,
            destLat = 18.5540,
            destLng = -68.3490,
        )
        assertTrue("Expected realistic Punta Cana travel estimate, got $estimatedMins", estimatedMins in 20..50)

        // Same point returns minimum floor of 10 min
        assertEquals(
            10,
            DepartureTimeCalculator.estimateOfflineTravelMinutes(18.615, -68.42, 18.615, -68.42),
        )

        // Reschedule threshold: < 5 min = false, >= 5 min = true
        val baseMillis = 1_700_000_000_000L
        assertEquals(false, DepartureTimeCalculator.shouldReschedule(baseMillis, baseMillis + 4 * 60_000L))
        assertEquals(true, DepartureTimeCalculator.shouldReschedule(baseMillis, baseMillis + 5 * 60_000L))
        assertEquals(true, DepartureTimeCalculator.shouldReschedule(baseMillis, baseMillis - 6 * 60_000L))

        // Estimated arrival if leaving now at 15:40 with 35 min travel -> 16:15
        val now = LocalDateTime.of(2026, 11, 20, 15, 40)
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 16, 15),
            DepartureTimeCalculator.estimatedArrivalIfLeavingNow(now, travelMinutes = 35),
        )

        // 12-hour Dominican time format
        assertEquals("9:50 a. m.", DominicanFormatter.formatTime12h(LocalTime.of(9, 50)))
        assertEquals("3:35 p. m.", DominicanFormatter.formatTime12h(LocalTime.of(15, 35)))
        assertEquals("12:00 p. m.", DominicanFormatter.formatTime12h(LocalTime.of(12, 0)))
        assertEquals("12:15 a. m.", DominicanFormatter.formatTime12h(LocalTime.of(0, 15)))
    }

    @Test
    fun `google maps and waze external navigation URLs follow contract`() {
        // With coordinates
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=18.554,-68.349&travelmode=driving",
            com.example.domain.rules.NavigationUrlBuilder.googleMapsDirectionsUrl(
                venueName = "Club Med Punta Cana",
                latitude = 18.5540,
                longitude = -68.3490,
            ),
        )
        assertEquals(
            "https://waze.com/ul?ll=18.554,-68.349&navigate=yes",
            com.example.domain.rules.NavigationUrlBuilder.wazeNavigationUrl(
                venueName = "Club Med Punta Cana",
                latitude = 18.5540,
                longitude = -68.3490,
            ),
        )

        // Without coordinates: falls back to hotel name
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Club%20Med%20Punta%20Cana&travelmode=driving",
            com.example.domain.rules.NavigationUrlBuilder.googleMapsDirectionsUrl(
                venueName = "Club Med Punta Cana",
                latitude = null,
                longitude = null,
            ),
        )
        assertEquals(
            "https://waze.com/ul?q=Club%20Med%20Punta%20Cana&navigate=yes",
            com.example.domain.rules.NavigationUrlBuilder.wazeNavigationUrl(
                venueName = "Club Med Punta Cana",
                latitude = null,
                longitude = null,
            ),
        )

        // Missing both returns null
        assertNull(com.example.domain.rules.NavigationUrlBuilder.googleMapsDirectionsUrl("   ", null, null))
        assertNull(com.example.domain.rules.NavigationUrlBuilder.wazeNavigationUrl("   ", null, null))

        // Invalid / out-of-range / NaN coordinates fall back safely to venue name
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=Club%20Med&travelmode=driving",
            com.example.domain.rules.NavigationUrlBuilder.googleMapsDirectionsUrl("Club Med", Double.NaN, -68.349),
        )
        assertEquals(
            "https://waze.com/ul?q=Club%20Med&navigate=yes",
            com.example.domain.rules.NavigationUrlBuilder.wazeNavigationUrl("Club Med", 95.0, -68.349),
        )
    }

    @Test
    fun `photon autocomplete and osrm routing URLs parsing and route cache follow contract`() {
        // Photon URL with Punta Cana bias (lat=18.58, lon=-68.40, limit=8)
        assertEquals(
            "https://photon.komoot.io/api/?q=Club%20Med&lat=18.58&lon=-68.40&limit=8",
            com.example.data.remote.OpenMapsApiClient.buildPhotonUrl("Club Med"),
        )

        // Photon GeoJSON uses [longitude, latitude] order
        val samplePhoton = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "geometry": {"type": "Point", "coordinates": [-68.349, 18.554]},
                  "properties": {
                    "osm_id": 123456,
                    "osm_type": "W",
                    "name": "Club Med Punta Cana",
                    "city": "Punta Cana",
                    "country": "República Dominicana"
                  }
                }
              ]
            }
        """.trimIndent()
        val photonItems = com.example.data.remote.OpenMapsApiClient.parsePhotonResponse(samplePhoton)
        assertEquals(1, photonItems.size)
        val (prediction, resolved) = photonItems.first()
        assertEquals("osm_W_123456", prediction.placeId)
        assertEquals("Club Med Punta Cana", prediction.primaryText)
        assertEquals("Punta Cana, República Dominicana", prediction.secondaryText)
        assertEquals(18.554, resolved.latitude, 0.0001)
        assertEquals(-68.349, resolved.longitude, 0.0001)

        // OSRM URL uses {lng1},{lat1};{lng2},{lat2}?overview=false
        val origin = com.example.data.port.GeoPoint(latitude = 18.6813, longitude = -68.4547)
        val dest = com.example.data.port.GeoPoint(latitude = 18.5540, longitude = -68.3490)
        assertEquals(
            "https://router.project-osrm.org/route/v1/driving/-68.454700,18.681300;-68.349000,18.554000?overview=false",
            com.example.data.remote.OpenMapsApiClient.buildOsrmUrl(origin, dest),
        )

        // OSRM JSON parsing (duration in seconds -> ceil minutes, distance in meters -> km)
        val sampleOsrm = """
            {"code":"Ok","routes":[{"distance":18420.0,"duration":1425.0}]}
        """.trimIndent()
        val parsedRoute = com.example.data.remote.OpenMapsApiClient.parseOsrmRouteResponse(sampleOsrm)
        assertEquals(24, parsedRoute.travelMinutes) // ceil(1425 / 60.0) = 24
        assertEquals(18.42, parsedRoute.distanceKm ?: 0.0, 0.001)
        assertTrue(parsedRoute.fromOsrm)

        // Non-Ok OSRM code throws AppError.Network
        val errorResult = runCatching {
            com.example.data.remote.OpenMapsApiClient.parseOsrmRouteResponse("""{"code":"NoRoute","routes":[]}""")
        }
        assertTrue(errorResult.isFailure)

        // CachedActivityRoute coordinate matching avoids redundant OSRM queries
        val cached = com.example.domain.model.CachedActivityRoute(
            activityId = "act-1",
            originLat = 18.6813,
            originLng = -68.4547,
            destLat = 18.5540,
            destLng = -68.3490,
            travelMinutes = 24,
            distanceKm = 18.42,
            fromOsrm = true,
        )
        assertTrue(cached.matchesCoordinates(18.6815, -68.4548, 18.5540, -68.3490))
        assertEquals(false, cached.matchesCoordinates(18.7500, -68.5000, 18.5540, -68.3490))
        assertEquals(false, cached.matchesCoordinates(18.6813, -68.4547, 18.4500, -68.4000))
    }
}
