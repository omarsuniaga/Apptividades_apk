package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.Currency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** How strong a calendar month is for you, learned from your own income history. */
enum class SeasonLevel(val label: String) {
    ALTA("Alta"),
    MEDIA("Media"),
    BAJA("Baja"),
}

/** Typical income of one calendar month (January..December) across the whole history. */
data class MonthSeasonPoint(
    val monthOfYear: Int,
    val averageIncome: BigDecimal,
    /** How many past months of this calendar month the average is based on (0 = no data). */
    val monthsCounted: Int,
    val level: SeasonLevel,
)

/** A coming month compared with what that month usually brings. */
data class UpcomingMonthOpportunity(
    val month: YearMonth,
    val bookedIncome: BigDecimal,
    val bookedGigs: Int,
    val typicalIncome: BigDecimal,
    /** typical - booked, never negative: money you usually have by then and still have not booked. */
    val gap: BigDecimal,
    val level: SeasonLevel,
)

data class HotelSeasonRow(
    val key: String,
    val displayName: String,
    /** Activities without a venue name. */
    val isUnassigned: Boolean,
    val totalIncome: BigDecimal,
    val previousIncome: BigDecimal,
    /** Change vs the previous 12 months; null when there was no income before. */
    val changePercent: Int?,
    val isNew: Boolean,
    val gigCount: Int,
    val averagePerGig: BigDecimal,
    val sharePercent: Int,
    /** 12 values aligned with [HotelSeasonReport.months]. */
    val monthlyIncome: List<BigDecimal>,
    val peakMonth: YearMonth?,
    /** Share of this hotel's income earned in high-season months; null without enough history. */
    val highSeasonSharePercent: Int?,
    val onlyHighSeason: Boolean,
)

enum class HotelInsightType {
    QUIET_MONTH,
    DROPPED,
    HIGH_SEASON_ONLY,
    TOP_CONCENTRATION,
    GREW,
    NEW_HOTEL,
}

data class HotelInsight(
    val type: HotelInsightType,
    val subject: String? = null,
    val percent: Int? = null,
    val month: YearMonth? = null,
    val amount: BigDecimal? = null,
)

data class HotelSeasonReport(
    val currency: Currency,
    /** The last 12 complete months, oldest first. */
    val months: List<YearMonth>,
    val totalIncome: BigDecimal,
    val previousTotalIncome: BigDecimal,
    val changePercent: Int?,
    val seasonProfile: List<MonthSeasonPoint>,
    /** False while the history is too short to call a month "alta" or "baja". */
    val hasSeasonHistory: Boolean,
    val hotels: List<HotelSeasonRow>,
    val upcoming: List<UpcomingMonthOpportunity>,
    val insights: List<HotelInsight>,
)

object HotelSeasonCalculator {

    const val MIN_HISTORY_MONTHS = 6
    const val HIGH_RATIO = 1.15
    const val LOW_RATIO = 0.85
    const val UPCOMING_MONTHS = 6
    const val CONCENTRATION_RISK_PERCENT = 40
    const val BIG_CHANGE_PERCENT = 30
    const val QUIET_MONTH_FRACTION = 0.5
    const val HIGH_SEASON_ONLY_PERCENT = 85
    const val MAX_INSIGHTS = 6

    fun build(
        activities: List<Activity>,
        today: LocalDate,
        defaultCurrency: Currency = Currency.DOP,
        convertMixedCurrencies: Boolean = false,
        defaultUsdToDopRate: BigDecimal = CurrencyConverter.DEFAULT_USD_TO_DOP_RATE,
        financialRecords: Map<String, ActivityFinancialRecord> = emptyMap(),
    ): HotelSeasonReport {
        val currentMonth = YearMonth.from(today)
        val endMonth = currentMonth.minusMonths(1)
        val startMonth = endMonth.minusMonths(11)
        val months = (0..11).map { startMonth.plusMonths(it.toLong()) }
        val monthSet = months.toSet()
        val previousMonths = (1..12).map { startMonth.minusMonths(it.toLong()) }.toSet()

        val usable = activities.filter { convertMixedCurrencies || it.currency == defaultCurrency }

        fun amountOf(a: Activity): BigDecimal =
            if (!convertMixedCurrencies || a.currency == defaultCurrency) {
                a.amount
            } else {
                CurrencyConverter.convert(
                    amount = a.amount,
                    from = a.currency,
                    to = defaultCurrency,
                    historicalUsdToDopRate = financialRecords[a.id]?.exchangeRateToDop,
                    fallbackUsdToDopRate = defaultUsdToDopRate,
                )
            }

        val byMonth: Map<YearMonth, List<Activity>> = usable.groupBy { YearMonth.from(it.eventDate) }
        val monthTotals: Map<YearMonth, BigDecimal> = byMonth.mapValues { (_, list) -> sum(list.map(::amountOf)) }

        val seasonProfile = buildSeasonProfile(usable, monthTotals, endMonth)
        val hasSeasonHistory = seasonProfile.count { it.monthsCounted > 0 } >= MIN_HISTORY_MONTHS &&
            seasonProfile.any { it.level != SeasonLevel.MEDIA }
        val levelByMonthOfYear = seasonProfile.associate { it.monthOfYear to it.level }

        val totalIncome = sum(months.map { monthTotals[it] ?: BigDecimal.ZERO })
        val previousTotal = sum(previousMonths.map { monthTotals[it] ?: BigDecimal.ZERO })

        val hotels = buildHotelRows(
            usable = usable,
            months = months,
            monthSet = monthSet,
            previousMonths = previousMonths,
            totalIncome = totalIncome,
            hasSeasonHistory = hasSeasonHistory,
            levelByMonthOfYear = levelByMonthOfYear,
            amountOf = ::amountOf,
        )

        val upcoming = (0 until UPCOMING_MONTHS).mapNotNull { i ->
            val ym = currentMonth.plusMonths(i.toLong())
            val point = seasonProfile[ym.monthValue - 1]
            if (point.monthsCounted == 0 || point.averageIncome.signum() <= 0) return@mapNotNull null
            val booked = monthTotals[ym] ?: BigDecimal.ZERO
            UpcomingMonthOpportunity(
                month = ym,
                bookedIncome = booked,
                bookedGigs = byMonth[ym].orEmpty().size,
                typicalIncome = point.averageIncome,
                gap = point.averageIncome.subtract(booked).max(BigDecimal.ZERO),
                level = if (hasSeasonHistory) point.level else SeasonLevel.MEDIA,
            )
        }

        return HotelSeasonReport(
            currency = defaultCurrency,
            months = months,
            totalIncome = totalIncome,
            previousTotalIncome = previousTotal,
            changePercent = percentChange(totalIncome, previousTotal),
            seasonProfile = seasonProfile,
            hasSeasonHistory = hasSeasonHistory,
            hotels = hotels,
            upcoming = upcoming,
            insights = buildInsights(hotels, upcoming),
        )
    }

    private fun buildSeasonProfile(
        usable: List<Activity>,
        monthTotals: Map<YearMonth, BigDecimal>,
        endMonth: YearMonth,
    ): List<MonthSeasonPoint> {
        val first = usable.minOfOrNull { YearMonth.from(it.eventDate) }
        val history: List<YearMonth> = if (first == null || first.isAfter(endMonth)) {
            emptyList()
        } else {
            generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(endMonth) }.toList()
        }
        val averages = (1..12).map { moy ->
            val inMonth = history.filter { it.monthValue == moy }
            val avg = if (inMonth.isEmpty()) {
                BigDecimal.ZERO
            } else {
                sum(inMonth.map { monthTotals[it] ?: BigDecimal.ZERO })
                    .divide(BigDecimal(inMonth.size), 0, RoundingMode.HALF_UP)
            }
            Triple(moy, avg, inMonth.size)
        }
        val withData = averages.filter { it.third > 0 }
        val mean = if (withData.isEmpty()) {
            BigDecimal.ZERO
        } else {
            sum(withData.map { it.second }).divide(BigDecimal(withData.size), 2, RoundingMode.HALF_UP)
        }
        val classify = withData.size >= MIN_HISTORY_MONTHS && mean.signum() > 0
        return averages.map { (moy, avg, counted) ->
            val level = if (!classify || counted == 0) {
                SeasonLevel.MEDIA
            } else {
                val ratio = avg.toDouble() / mean.toDouble()
                when {
                    ratio >= HIGH_RATIO -> SeasonLevel.ALTA
                    ratio <= LOW_RATIO -> SeasonLevel.BAJA
                    else -> SeasonLevel.MEDIA
                }
            }
            MonthSeasonPoint(moy, avg, counted, level)
        }
    }

    private fun buildHotelRows(
        usable: List<Activity>,
        months: List<YearMonth>,
        monthSet: Set<YearMonth>,
        previousMonths: Set<YearMonth>,
        totalIncome: BigDecimal,
        hasSeasonHistory: Boolean,
        levelByMonthOfYear: Map<Int, SeasonLevel>,
        amountOf: (Activity) -> BigDecimal,
    ): List<HotelSeasonRow> {
        val resolution = NameNormalizer.buildVenueResolution(usable.map { it.venueName })
        val inWindows = usable.filter {
            val ym = YearMonth.from(it.eventDate)
            ym in monthSet || ym in previousMonths
        }
        return inWindows
            .groupBy { resolution.canonicalKey(it.venueName) }
            .map { (key, group) ->
                val period = group.filter { YearMonth.from(it.eventDate) in monthSet }
                val previous = group.filter { YearMonth.from(it.eventDate) in previousMonths }
                val monthly = months.map { ym ->
                    sum(period.filter { YearMonth.from(it.eventDate) == ym }.map(amountOf))
                }
                val total = sum(monthly)
                val prevTotal = sum(previous.map(amountOf))
                val unassigned = key.isEmpty()

                val highShare = if (hasSeasonHistory && total.signum() > 0) {
                    val high = sum(
                        months.indices
                            .filter { levelByMonthOfYear[months[it].monthValue] == SeasonLevel.ALTA }
                            .map { monthly[it] },
                    )
                    percentOf(high, total)
                } else {
                    null
                }
                HotelSeasonRow(
                    key = key.ifEmpty { "__unassigned__" },
                    displayName = if (unassigned) "" else resolution.displayName(group.first().venueName),
                    isUnassigned = unassigned,
                    totalIncome = total,
                    previousIncome = prevTotal,
                    changePercent = percentChange(total, prevTotal),
                    isNew = prevTotal.signum() == 0 && total.signum() > 0,
                    gigCount = period.size,
                    averagePerGig = if (period.isEmpty()) {
                        BigDecimal.ZERO
                    } else {
                        total.divide(BigDecimal(period.size), 0, RoundingMode.HALF_UP)
                    },
                    sharePercent = percentOf(total, totalIncome),
                    monthlyIncome = monthly,
                    peakMonth = if (total.signum() > 0) months[monthly.indices.maxByOrNull { monthly[it] }!!] else null,
                    highSeasonSharePercent = highShare,
                    onlyHighSeason = highShare != null && highShare >= HIGH_SEASON_ONLY_PERCENT && period.size >= 2,
                )
            }
            .sortedWith(
                compareBy<HotelSeasonRow> { it.isUnassigned }
                    .thenByDescending { it.totalIncome }
                    .thenByDescending { it.previousIncome }
                    .thenBy { it.displayName.lowercase() },
            )
    }

    private fun buildInsights(
        hotels: List<HotelSeasonRow>,
        upcoming: List<UpcomingMonthOpportunity>,
    ): List<HotelInsight> {
        val named = hotels.filter { !it.isUnassigned }
        val result = mutableListOf<HotelInsight>()

        upcoming
            .filter { it.bookedIncome.toDouble() < it.typicalIncome.toDouble() * QUIET_MONTH_FRACTION }
            .take(2)
            .forEach { result += HotelInsight(HotelInsightType.QUIET_MONTH, month = it.month, amount = it.gap) }

        named
            .filter { it.previousIncome.signum() > 0 && (it.changePercent ?: 0) <= -BIG_CHANGE_PERCENT }
            .sortedByDescending { it.previousIncome.subtract(it.totalIncome) }
            .take(2)
            .forEach {
                result += HotelInsight(
                    HotelInsightType.DROPPED,
                    subject = it.displayName,
                    percent = it.changePercent,
                    amount = it.previousIncome.subtract(it.totalIncome),
                )
            }

        named.filter { it.onlyHighSeason }.take(2).forEach {
            result += HotelInsight(HotelInsightType.HIGH_SEASON_ONLY, subject = it.displayName, percent = it.highSeasonSharePercent)
        }

        named.firstOrNull()?.takeIf { it.sharePercent >= CONCENTRATION_RISK_PERCENT }?.let {
            result += HotelInsight(HotelInsightType.TOP_CONCENTRATION, subject = it.displayName, percent = it.sharePercent)
        }

        named
            .filter { it.previousIncome.signum() > 0 && (it.changePercent ?: 0) >= BIG_CHANGE_PERCENT }
            .maxByOrNull { it.totalIncome.subtract(it.previousIncome) }
            ?.let {
                result += HotelInsight(
                    HotelInsightType.GREW,
                    subject = it.displayName,
                    percent = it.changePercent,
                    amount = it.totalIncome.subtract(it.previousIncome),
                )
            }

        named.firstOrNull { it.isNew }?.let {
            result += HotelInsight(HotelInsightType.NEW_HOTEL, subject = it.displayName, amount = it.totalIncome)
        }

        return result.take(MAX_INSIGHTS)
    }

    private fun sum(values: Collection<BigDecimal>): BigDecimal = values.fold(BigDecimal.ZERO, BigDecimal::add)

    private fun percentOf(part: BigDecimal, whole: BigDecimal): Int =
        if (whole.signum() <= 0) 0 else part.multiply(BigDecimal(100)).divide(whole, 0, RoundingMode.HALF_UP).toInt()

    private fun percentChange(current: BigDecimal, previous: BigDecimal): Int? =
        if (previous.signum() <= 0) {
            null
        } else {
            current.subtract(previous).multiply(BigDecimal(100)).divide(previous, 0, RoundingMode.HALF_UP).toInt()
        }
}

/** CSV with one row per hotel and one column per month, for spreadsheets. */
object HotelSeasonCsvExporter {
    private const val UTF8_BOM = "﻿"

    fun build(report: HotelSeasonReport, unassignedLabel: String = "Sin lugar"): String {
        val sb = StringBuilder(UTF8_BOM)
        val monthLabels = report.months.map { "${DominicanFormatter.formatShortMonth(it.month)} ${it.year}" }
        sb.appendLine((listOf("Hotel") + monthLabels + listOf("Total", "Total 12 meses anteriores", "Cambio %", "Toques", "Moneda")).joinToString(",") { cell(it) })
        report.hotels.forEach { h ->
            val cols = buildList {
                add(if (h.isUnassigned) unassignedLabel else h.displayName)
                h.monthlyIncome.forEach { add(it.toPlainString()) }
                add(h.totalIncome.toPlainString())
                add(h.previousIncome.toPlainString())
                add(h.changePercent?.toString().orEmpty())
                add(h.gigCount.toString())
                add(report.currency.code)
            }
            sb.appendLine(cols.joinToString(",") { cell(it) })
        }
        return sb.toString()
    }

    private fun cell(raw: String): String =
        if (raw.any { it == ',' || it == '"' || it == '\n' }) "\"" + raw.replace("\"", "\"\"") + "\"" else raw
}
