package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.rules.HotelInsightType
import com.example.domain.rules.HotelSeasonCalculator
import com.example.domain.rules.HotelSeasonCsvExporter
import com.example.domain.rules.HotelSeasonReport
import com.example.domain.rules.SeasonLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class HotelSeasonCalculatorTest {

    // Window: Nov 2025..Oct 2026. Previous window: Nov 2024..Oct 2025.
    private val today = LocalDate.of(2026, 11, 25)
    private var seq = 0

    private fun gig(
        venue: String,
        ym: YearMonth,
        amount: Int,
        currency: Currency = Currency.DOP,
    ) = Activity(
        id = "a${++seq}",
        ownerId = "o",
        supplierName = "FJ",
        title = "FJ",
        eventDate = ym.atDay(10),
        venueName = venue,
        amount = BigDecimal(amount),
        currency = currency,
    )

    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    private fun build(
        activities: List<Activity>,
        convert: Boolean = false,
        rate: BigDecimal = BigDecimal("60"),
    ): HotelSeasonReport = HotelSeasonCalculator.build(
        activities = activities,
        today = today,
        defaultCurrency = Currency.DOP,
        convertMixedCurrencies = convert,
        defaultUsdToDopRate = rate,
    )

    /** One gig per month from Jan 2025 to Oct 2026 shaped like Punta Cana: winter high, summer low. */
    private fun seasonalHistory(venue: String = "Lopesan"): List<Activity> {
        val list = mutableListOf<Activity>()
        var cursor = ym(2025, 1)
        while (!cursor.isAfter(ym(2026, 10))) {
            val amount = when (cursor.monthValue) {
                12, 1, 2, 3 -> 60_000
                11, 4 -> 30_000
                else -> 10_000
            }
            list += gig(venue, cursor, amount)
            cursor = cursor.plusMonths(1)
        }
        return list
    }

    @Test
    fun emptyHistoryGivesAnEmptyReport() {
        val r = build(emptyList())
        assertEquals(12, r.months.size)
        assertEquals(ym(2025, 11), r.months.first())
        assertEquals(ym(2026, 10), r.months.last())
        assertEquals(0, r.totalIncome.signum())
        assertNull(r.changePercent)
        assertTrue(r.hotels.isEmpty())
        assertFalse(r.hasSeasonHistory)
        assertTrue(r.upcoming.isEmpty())
        assertTrue(r.insights.isEmpty())
    }

    @Test
    fun hotelVariantsAreMergedAndTotalsAreComparedWithThePreviousYear() {
        val r = build(
            listOf(
                gig("Lopesan", ym(2026, 1), 7000),
                gig("Hotel Lopesan", ym(2026, 2), 7000),
                gig("Lopesan, Bávaro", ym(2026, 3), 6000),
                gig("Lopesan", ym(2025, 2), 10_000),
                gig("Iberostar", ym(2026, 4), 5000),
            ),
        )
        assertEquals(2, r.hotels.size)
        val lopesan = r.hotels.first()
        assertTrue(lopesan.displayName.contains("Lopesan"))
        assertEquals(3, lopesan.gigCount)
        assertEquals(0, BigDecimal(20_000).compareTo(lopesan.totalIncome))
        assertEquals(0, BigDecimal(10_000).compareTo(lopesan.previousIncome))
        assertEquals(100, lopesan.changePercent)
        assertEquals(80, lopesan.sharePercent)
        assertEquals(0, BigDecimal(6667).compareTo(lopesan.averagePerGig))
        assertEquals(ym(2026, 1), lopesan.peakMonth)
        assertEquals(0, BigDecimal(25_000).compareTo(r.totalIncome))
        assertEquals(150, r.changePercent)
    }

    @Test
    fun monthlyIncomeIsAlignedWithTheTwelveMonthWindow() {
        val r = build(listOf(gig("Lopesan", ym(2025, 11), 1000), gig("Lopesan", ym(2026, 10), 2000)))
        val row = r.hotels.single()
        assertEquals(12, row.monthlyIncome.size)
        assertEquals(0, BigDecimal(1000).compareTo(row.monthlyIncome.first()))
        assertEquals(0, BigDecimal(2000).compareTo(row.monthlyIncome.last()))
    }

    @Test
    fun currentMonthAndOlderThanPreviousWindowAreNotCounted() {
        val r = build(
            listOf(
                gig("Lopesan", ym(2026, 11), 9000),
                gig("Lopesan", ym(2024, 10), 9000),
                gig("Lopesan", ym(2026, 5), 3000),
            ),
        )
        assertEquals(0, BigDecimal(3000).compareTo(r.totalIncome))
        assertEquals(0, r.previousTotalIncome.signum())
    }

    @Test
    fun hotelWithoutPreviousIncomeIsNewAndHasNoPercentChange() {
        val r = build(listOf(gig("Riu Palace", ym(2026, 5), 8000)))
        val row = r.hotels.single()
        assertTrue(row.isNew)
        assertNull(row.changePercent)
        assertTrue(r.insights.any { it.type == HotelInsightType.NEW_HOTEL && it.subject == "Riu Palace" })
    }

    @Test
    fun droppedHotelIsFlaggedAndKeptEvenWithoutIncomeNow() {
        val r = build(
            listOf(
                gig("Catalonia", ym(2025, 3), 100_000),
                gig("Catalonia", ym(2026, 3), 50_000),
                gig("Barceló", ym(2025, 4), 40_000),
            ),
        )
        val cat = r.hotels.first { it.displayName == "Catalonia" }
        assertEquals(-50, cat.changePercent)
        val barcelo = r.hotels.first { it.displayName == "Barceló" }
        assertEquals(0, barcelo.totalIncome.signum())
        assertEquals(-100, barcelo.changePercent)
        val dropped = r.insights.filter { it.type == HotelInsightType.DROPPED }
        assertEquals(listOf("Catalonia", "Barceló"), dropped.map { it.subject })
    }

    @Test
    fun heavyConcentrationOnOneHotelIsReported() {
        val r = build(listOf(gig("Lopesan", ym(2026, 1), 90_000), gig("Iberostar", ym(2026, 2), 10_000)))
        val insight = r.insights.single { it.type == HotelInsightType.TOP_CONCENTRATION }
        assertEquals("Lopesan", insight.subject)
        assertEquals(90, insight.percent)
    }

    @Test
    fun strongGrowthIsReported() {
        val r = build(listOf(gig("Iberostar", ym(2025, 5), 10_000), gig("Iberostar", ym(2026, 5), 20_000), gig("Lopesan", ym(2026, 1), 1)))
        val grew = r.insights.single { it.type == HotelInsightType.GREW }
        assertEquals("Iberostar", grew.subject)
        assertEquals(100, grew.percent)
    }

    @Test
    fun seasonsAreLearnedFromTheIncomeHistory() {
        val r = build(seasonalHistory())
        assertTrue(r.hasSeasonHistory)
        val byMonth = r.seasonProfile.associateBy { it.monthOfYear }
        assertEquals(SeasonLevel.ALTA, byMonth.getValue(1).level)
        assertEquals(SeasonLevel.ALTA, byMonth.getValue(12).level)
        assertEquals(SeasonLevel.BAJA, byMonth.getValue(7).level)
        assertEquals(SeasonLevel.MEDIA, byMonth.getValue(4).level)
        assertEquals(2, byMonth.getValue(1).monthsCounted)
        assertEquals(1, byMonth.getValue(12).monthsCounted)
        assertEquals(0, BigDecimal(60_000).compareTo(byMonth.getValue(1).averageIncome))
    }

    @Test
    fun tooLittleHistoryDoesNotInventSeasons() {
        val r = build(listOf(gig("Lopesan", ym(2026, 8), 10_000), gig("Lopesan", ym(2026, 9), 60_000), gig("Lopesan", ym(2026, 10), 5_000)))
        assertFalse(r.hasSeasonHistory)
        assertTrue(r.seasonProfile.all { it.level == SeasonLevel.MEDIA })
        assertTrue(r.hotels.single().highSeasonSharePercent == null)
    }

    @Test
    fun upcomingMonthsAreComparedWithWhatTheMonthUsuallyBrings() {
        val r = build(seasonalHistory() + gig("Lopesan", ym(2026, 11), 5_000))
        assertEquals(6, r.upcoming.size)
        assertEquals(ym(2026, 11), r.upcoming.first().month)

        val nov = r.upcoming[0]
        assertEquals(0, BigDecimal(5_000).compareTo(nov.bookedIncome))
        assertEquals(1, nov.bookedGigs)
        assertEquals(0, BigDecimal(30_000).compareTo(nov.typicalIncome))
        assertEquals(0, BigDecimal(25_000).compareTo(nov.gap))

        val dec = r.upcoming[1]
        assertEquals(SeasonLevel.ALTA, dec.level)
        assertEquals(0, BigDecimal(60_000).compareTo(dec.gap))

        val quiet = r.insights.filter { it.type == HotelInsightType.QUIET_MONTH }
        assertEquals(listOf(ym(2026, 11), ym(2026, 12)), quiet.map { it.month })
    }

    @Test
    fun alreadyBookedMonthsHaveNoGap() {
        val r = build(seasonalHistory() + gig("Lopesan", ym(2026, 12), 80_000))
        val dec = r.upcoming.first { it.month == ym(2026, 12) }
        assertEquals(0, dec.gap.signum())
        assertTrue(r.insights.none { it.type == HotelInsightType.QUIET_MONTH && it.month == ym(2026, 12) })
    }

    @Test
    fun hotelThatOnlyWorksInHighSeasonIsFlagged() {
        val winterOnly = listOf(
            gig("Riu Palace", ym(2025, 12), 20_000),
            gig("Riu Palace", ym(2026, 1), 20_000),
            gig("Riu Palace", ym(2026, 2), 20_000),
        )
        val r = build(seasonalHistory() + winterOnly)
        val riu = r.hotels.first { it.displayName == "Riu Palace" }
        assertEquals(100, riu.highSeasonSharePercent)
        assertTrue(riu.onlyHighSeason)
        assertTrue(r.insights.any { it.type == HotelInsightType.HIGH_SEASON_ONLY && it.subject == "Riu Palace" })

        val lopesan = r.hotels.first { it.displayName == "Lopesan" }
        assertFalse(lopesan.onlyHighSeason)
    }

    @Test
    fun otherCurrenciesAreIgnoredUnlessConversionIsEnabled() {
        val acts = listOf(gig("Lopesan", ym(2026, 1), 5_000, Currency.DOP), gig("Lopesan", ym(2026, 2), 100, Currency.USD))
        assertEquals(0, BigDecimal(5_000).compareTo(build(acts).totalIncome))
        assertEquals(0, BigDecimal(11_000).compareTo(build(acts, convert = true, rate = BigDecimal("60")).totalIncome))
    }

    @Test
    fun activitiesWithoutVenueAreGroupedLast() {
        val r = build(listOf(gig("", ym(2026, 1), 90_000), gig("Lopesan", ym(2026, 2), 1_000)))
        assertEquals(2, r.hotels.size)
        assertTrue(r.hotels.last().isUnassigned)
        assertEquals("Lopesan", r.hotels.first().displayName)
        assertTrue(r.insights.none { it.subject.isNullOrEmpty() && it.type != HotelInsightType.QUIET_MONTH })
    }

    @Test
    fun csvHasOneRowPerHotelWithMonthColumnsAndEscapesCommas() {
        val r = build(listOf(gig("Lopesan, Bávaro", ym(2026, 1), 7000), gig("Iberostar", ym(2026, 2), 3000)))
        val csv = HotelSeasonCsvExporter.build(r)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines.first().contains("Hotel"))
        assertTrue(lines.first().contains("nov 2025"))
        assertTrue(lines[1].startsWith("\"Lopesan, Bávaro\"") || lines[1].contains("Lopesan"))
        assertNotNull(lines.firstOrNull { it.contains("7000") })
        assertTrue(lines.all { it.endsWith("DOP") || it.contains("Moneda") })
    }
}
