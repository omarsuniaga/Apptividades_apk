package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

data class MonthOverview(
    val month: YearMonth,
    val currency: Currency,
    val billed: BigDecimal,
    val monthlyGoal: BigDecimal,
    val progressFraction: Float,
    val progressPercent: Int,
    val totalActivities: Int,
    val playedCount: Int,
    val upcomingCount: Int,
    val collectedAmount: BigDecimal,
    val missingGigsEstimate: Int,
    val expensesTotal: BigDecimal = BigDecimal.ZERO,
    val netProfit: BigDecimal = billed,
)

data class MonthlyBarPoint(
    val month: YearMonth,
    val total: BigDecimal,
    val count: Int,
    val reachedGoal: Boolean,
)

data class SupplierShare(
    val supplierKey: String,
    val displayName: String,
    val gigCount: Int,
    val totalAmount: BigDecimal,
    val percent: Int,
    val monthlyAverage: BigDecimal,
    val isOtherBucket: Boolean = false,
    val otherSupplierCount: Int = 0,
)

data class VenueRanking(
    val venueKey: String,
    val displayName: String,
    val gigCount: Int,
    val totalAmount: BigDecimal,
    val fractionOfTop: Float,
)

data class Balance12Months(
    val startMonth: YearMonth,
    val endMonth: YearMonth,
    val currency: Currency,
    val monthlyGoal: BigDecimal,
    val totalAmount: BigDecimal,
    val totalGigs: Int,
    val averagePerMonth: BigDecimal,
    val gigsPerMonth: Double,
    val averagePerGig: BigDecimal,
    val months: List<MonthlyBarPoint>,
    val monthsReachedGoal: Int,
    val lowestMonth: MonthlyBarPoint?,
    val supplierShares: List<SupplierShare>,
    val top3SharePercent: Int,
    val top3Count: Int,
    val topSupplierMonthlyImpact: BigDecimal,
    val topSupplierName: String?,
    val venueRankings: List<VenueRanking>,
    val totalMergedVenueVariants: Int,
)

data class FrequentSupplier(
    val canonicalKey: String,
    val displayName: String,
    val gigCount: Int,
)

data class SavedVenueSuggestion(
    val canonicalKey: String,
    val displayName: String,
    val gigCountLast12Months: Int,
)

/**
 * Rules 2.3 #3, #4, #6, #7:
 * - Usual rate per supplier & currency
 * - Frequent suppliers (top 3 in last 365 days)
 * - Month overview (billed, goal %, missing gigs estimate)
 * - 12-month balance (bars, averages, supplier concentration, venue ranking)
 */
object BalanceAndOverviewCalculator {

    /**
     * Rule 2.3 #3: Most frequent amount charged to a supplier in the selected currency,
     * with the most recent gig winning ties.
     */
    fun usualRate(
        activities: List<Activity>,
        supplierName: String,
        currency: Currency,
    ): BigDecimal? {
        val targetKey = NameNormalizer.normalizeKey(supplierName)
        if (targetKey.isEmpty()) return null

        val matching = activities.filter {
            it.currency == currency &&
                NameNormalizer.normalizeKey(it.supplierName) == targetKey &&
                it.amount > BigDecimal.ZERO
        }
        if (matching.isEmpty()) return null

        // Normalize BigDecimal scale by stripping trailing zeros so 6000 and 6000.00 count together
        val grouped = matching.groupBy { it.amount.stripTrailingZeros() }
        val winner = grouped.entries.maxWith(
            compareBy<Map.Entry<BigDecimal, List<Activity>>> { it.value.size }
                .thenBy { entry -> entry.value.maxOf { it.eventDate } }
        )
        return winner.value.maxBy { it.eventDate }.amount
    }

    /**
     * Rule 2.3 #4: Top 3 canonical suppliers by gig count in the last 365 days.
     */
    fun frequentSuppliers(
        activities: List<Activity>,
        today: LocalDate,
        limit: Int = 3,
    ): List<FrequentSupplier> {
        val cutoff = today.minusDays(365)
        val inWindow = activities.filter {
            it.supplierName.isNotBlank() &&
                !it.eventDate.isBefore(cutoff) &&
                !it.eventDate.isAfter(today)
        }
        val source = inWindow.ifEmpty { activities.filter { it.supplierName.isNotBlank() } }
        if (source.isEmpty()) return emptyList()

        val resolution = NameNormalizer.buildSupplierResolution(source.map { it.supplierName })
        return source
            .groupBy { resolution.canonicalKey(it.supplierName) }
            .filterKeys { it.isNotEmpty() }
            .map { (key, list) ->
                FrequentSupplier(
                    canonicalKey = key,
                    displayName = resolution.canonicalToDisplay[key] ?: list.first().supplierName.trim(),
                    gigCount = list.size,
                )
            }
            .sortedWith(compareByDescending<FrequentSupplier> { it.gigCount }.thenBy { it.displayName })
            .take(limit)
    }

    /**
     * Normalized venue suggestions ordered by frequency in the last 12 months (or overall).
     */
    fun venueSuggestions(
        activities: List<Activity>,
        today: LocalDate,
    ): List<SavedVenueSuggestion> {
        val valid = activities.filter { it.venueName.isNotBlank() }
        if (valid.isEmpty()) return emptyList()

        val resolution = NameNormalizer.buildVenueResolution(valid.map { it.venueName })
        val cutoff = today.minusDays(365)
        return valid
            .groupBy { resolution.canonicalKey(it.venueName) }
            .filterKeys { it.isNotEmpty() }
            .map { (key, list) ->
                val count12m = list.count { !it.eventDate.isBefore(cutoff) && !it.eventDate.isAfter(today) }
                SavedVenueSuggestion(
                    canonicalKey = key,
                    displayName = resolution.canonicalToDisplay[key] ?: list.first().venueName.trim(),
                    gigCountLast12Months = if (count12m > 0) count12m else list.size,
                )
            }
            .sortedWith(compareByDescending<SavedVenueSuggestion> { it.gigCountLast12Months }.thenBy { it.displayName })
    }

    /**
     * Free weekend days (Fri–Sun) in the given month from today onward with no gigs.
     */
    fun freeWeekendDays(
        activities: List<Activity>,
        month: YearMonth,
        today: LocalDate,
    ): List<LocalDate> {
        val occupiedDates = activities.map { it.eventDate }.toSet()
        val result = mutableListOf<LocalDate>()
        var day = month.atDay(1)
        val lastDay = month.atEndOfMonth()
        while (!day.isAfter(lastDay)) {
            val isWeekend = day.dayOfWeek == DayOfWeek.FRIDAY ||
                day.dayOfWeek == DayOfWeek.SATURDAY ||
                day.dayOfWeek == DayOfWeek.SUNDAY
            if (isWeekend && !day.isBefore(today) && day !in occupiedDates) {
                result.add(day)
            }
            day = day.plusDays(1)
        }
        return result
    }

    /**
     * Rule 2.3 #6: Month overview.
     */
    fun monthOverview(
        activities: List<Activity>,
        today: LocalDate,
        monthlyGoal: BigDecimal,
        defaultCurrency: Currency = Currency.DOP,
        targetMonth: YearMonth = YearMonth.from(today),
        financialRecords: Map<String, ActivityFinancialRecord> = emptyMap(),
        convertMixedCurrencies: Boolean = false,
        defaultUsdToDopRate: BigDecimal = CurrencyConverter.DEFAULT_USD_TO_DOP_RATE,
    ): MonthOverview {
        val monthActivities = activities.filter {
            YearMonth.from(it.eventDate) == targetMonth
        }
        val monthInCurrency = if (convertMixedCurrencies) {
            monthActivities
        } else {
            monthActivities.filter { it.currency == defaultCurrency }
        }
        fun convertFor(act: Activity, raw: BigDecimal): BigDecimal =
            if (!convertMixedCurrencies || act.currency == defaultCurrency) raw
            else CurrencyConverter.convert(
                amount = raw,
                from = act.currency,
                to = defaultCurrency,
                historicalUsdToDopRate = financialRecords[act.id]?.exchangeRateToDop,
                fallbackUsdToDopRate = defaultUsdToDopRate,
            )

        val billed = monthInCurrency.fold(BigDecimal.ZERO) { acc, a ->
            acc.add(convertFor(a, a.amount))
        }
        val collected = monthInCurrency.fold(BigDecimal.ZERO) { acc, a ->
            val breakdown = ActivityFinanceCalculator.breakdown(a, financialRecords[a.id])
            acc.add(convertFor(a, breakdown.paidAmount))
        }
        val expensesTotal = monthInCurrency.fold(BigDecimal.ZERO) { acc, a ->
            val breakdown = ActivityFinanceCalculator.breakdown(a, financialRecords[a.id])
            acc.add(convertFor(a, breakdown.totalExpenses))
        }
        val netProfit = billed.subtract(expensesTotal)

        val playedCount = monthActivities.count { !it.eventDate.isAfter(today) }
        val upcomingCount = monthActivities.count { it.eventDate.isAfter(today) }

        val progressRatio = if (monthlyGoal > BigDecimal.ZERO) {
            billed.divide(monthlyGoal, 4, RoundingMode.HALF_UP).toDouble()
        } else {
            0.0
        }
        val progressPercent = (progressRatio * 100.0).let { kotlin.math.round(it).toInt() }.coerceAtLeast(0)
        val progressFraction = progressRatio.toFloat().coerceIn(0f, 1f)

        val balance12 = balance12Months(
            activities = activities,
            today = today,
            monthlyGoal = monthlyGoal,
            defaultCurrency = defaultCurrency,
            financialRecords = financialRecords,
            convertMixedCurrencies = convertMixedCurrencies,
            defaultUsdToDopRate = defaultUsdToDopRate,
        )
        val avgPerGig = when {
            balance12.averagePerGig > BigDecimal.ZERO -> balance12.averagePerGig
            monthInCurrency.isNotEmpty() && billed > BigDecimal.ZERO ->
                billed.divide(BigDecimal(monthInCurrency.size), 2, RoundingMode.HALF_UP)
            else -> BigDecimal.ZERO
        }

        val missingGigs = if (billed >= monthlyGoal || avgPerGig <= BigDecimal.ZERO) {
            0
        } else {
            val remaining = monthlyGoal.subtract(billed)
            ceil(remaining.divide(avgPerGig, 6, RoundingMode.HALF_UP).toDouble()).toInt().coerceAtLeast(1)
        }

        return MonthOverview(
            month = targetMonth,
            currency = defaultCurrency,
            billed = billed,
            monthlyGoal = monthlyGoal,
            progressFraction = progressFraction,
            progressPercent = progressPercent,
            totalActivities = monthActivities.size,
            playedCount = playedCount,
            upcomingCount = upcomingCount,
            collectedAmount = collected,
            missingGigsEstimate = missingGigs,
            expensesTotal = expensesTotal,
            netProfit = netProfit,
        )
    }

    /**
     * Rule 2.3 #7: 12-month balance (the previous 12 complete months before today's month).
     */
    fun balance12Months(
        activities: List<Activity>,
        today: LocalDate,
        monthlyGoal: BigDecimal,
        defaultCurrency: Currency = Currency.DOP,
        noSupplierLabel: String = "Sin proveedor",
        financialRecords: Map<String, ActivityFinancialRecord> = emptyMap(),
        convertMixedCurrencies: Boolean = false,
        defaultUsdToDopRate: BigDecimal = CurrencyConverter.DEFAULT_USD_TO_DOP_RATE,
    ): Balance12Months {
        val endMonth = YearMonth.from(today).minusMonths(1)
        val startMonth = endMonth.minusMonths(11)
        val monthList = (0..11).map { startMonth.plusMonths(it.toLong()) }
        val monthSet = monthList.toSet()

        val filtered = activities.filter {
            (convertMixedCurrencies || it.currency == defaultCurrency) &&
                YearMonth.from(it.eventDate) in monthSet
        }

        fun convertedAmount(a: Activity): BigDecimal =
            if (!convertMixedCurrencies || a.currency == defaultCurrency) a.amount
            else CurrencyConverter.convert(
                amount = a.amount,
                from = a.currency,
                to = defaultCurrency,
                historicalUsdToDopRate = financialRecords[a.id]?.exchangeRateToDop,
                fallbackUsdToDopRate = defaultUsdToDopRate,
            )

        val byMonth = filtered.groupBy { YearMonth.from(it.eventDate) }
        val bars = monthList.map { ym ->
            val items = byMonth[ym].orEmpty()
            val total = items.fold(BigDecimal.ZERO) { acc, a -> acc.add(convertedAmount(a)) }
            MonthlyBarPoint(
                month = ym,
                total = total,
                count = items.size,
                reachedGoal = monthlyGoal > BigDecimal.ZERO && total >= monthlyGoal,
            )
        }

        val totalAmount = filtered.fold(BigDecimal.ZERO) { acc, a -> acc.add(convertedAmount(a)) }
        val totalGigs = filtered.size
        val averagePerMonth = totalAmount.divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
        val gigsPerMonth = totalGigs / 12.0
        val averagePerGig = if (totalGigs > 0) {
            totalAmount.divide(BigDecimal(totalGigs), 0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val monthsReachedGoal = bars.count { it.reachedGoal }
        val lowestMonth = if (totalGigs > 0) bars.minByOrNull { it.total } else null

        // Supplier concentration (top 3 + "N proveedores más")
        val supplierRes = NameNormalizer.buildSupplierResolution(filtered.map { it.supplierName })
        val supplierGroups = filtered
            .groupBy {
                val key = supplierRes.canonicalKey(it.supplierName)
                key.ifEmpty { "__no_supplier__" }
            }
            .map { (key, items) ->
                val name = if (key == "__no_supplier__") noSupplierLabel
                else supplierRes.canonicalToDisplay[key] ?: items.first().supplierName.trim()
                val sum = items.fold(BigDecimal.ZERO) { acc, a -> acc.add(convertedAmount(a)) }
                val pct = if (totalAmount > BigDecimal.ZERO) {
                    kotlin.math.round(sum.multiply(BigDecimal(100)).divide(totalAmount, 4, RoundingMode.HALF_UP).toDouble()).toInt()
                } else 0
                SupplierShare(
                    supplierKey = key,
                    displayName = name,
                    gigCount = items.size,
                    totalAmount = sum,
                    percent = pct,
                    monthlyAverage = sum.divide(BigDecimal(12), 0, RoundingMode.HALF_UP),
                )
            }
            .sortedByDescending { it.totalAmount }

        val top3 = supplierGroups.take(3)
        val rest = supplierGroups.drop(3)
        val shares = buildList {
            addAll(top3)
            if (rest.isNotEmpty()) {
                val restTotal = rest.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.totalAmount) }
                val restCount = rest.sumOf { it.gigCount }
                val restPct = (100 - top3.sumOf { it.percent }).coerceAtLeast(0)
                add(
                    SupplierShare(
                        supplierKey = "__other__",
                        displayName = "",
                        gigCount = restCount,
                        totalAmount = restTotal,
                        percent = restPct,
                        monthlyAverage = restTotal.divide(BigDecimal(12), 0, RoundingMode.HALF_UP),
                        isOtherBucket = true,
                        otherSupplierCount = rest.size,
                    )
                )
            }
        }

        val top3Sum = top3.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.totalAmount) }
        val top3SharePct = if (totalAmount > BigDecimal.ZERO) {
            kotlin.math.round(top3Sum.multiply(BigDecimal(100)).divide(totalAmount, 4, RoundingMode.HALF_UP).toDouble()).toInt()
        } else 0

        // Venue ranking (top 4 with prefix merging)
        val venueRes = NameNormalizer.buildVenueResolution(filtered.map { it.venueName })
        val venueGroups = filtered
            .filter { it.venueName.isNotBlank() }
            .groupBy { venueRes.canonicalKey(it.venueName) }
            .filterKeys { it.isNotEmpty() }
            .map { (key, items) ->
                val sum = items.fold(BigDecimal.ZERO) { acc, a -> acc.add(convertedAmount(a)) }
                Triple(
                    key,
                    venueRes.canonicalToDisplay[key] ?: items.first().venueName.trim(),
                    items.size to sum,
                )
            }
            .sortedWith(compareByDescending<Triple<String, String, Pair<Int, BigDecimal>>> { it.third.first }
                .thenByDescending { it.third.second })
            .take(4)

        val maxVenueGigs = venueGroups.firstOrNull()?.third?.first?.coerceAtLeast(1) ?: 1
        val venueRankings = venueGroups.map { (key, name, stats) ->
            VenueRanking(
                venueKey = key,
                displayName = name,
                gigCount = stats.first,
                totalAmount = stats.second,
                fractionOfTop = stats.first.toFloat() / maxVenueGigs.toFloat(),
            )
        }

        return Balance12Months(
            startMonth = startMonth,
            endMonth = endMonth,
            currency = defaultCurrency,
            monthlyGoal = monthlyGoal,
            totalAmount = totalAmount,
            totalGigs = totalGigs,
            averagePerMonth = averagePerMonth,
            gigsPerMonth = gigsPerMonth,
            averagePerGig = averagePerGig,
            months = bars,
            monthsReachedGoal = monthsReachedGoal,
            lowestMonth = lowestMonth,
            supplierShares = shares,
            top3SharePercent = top3SharePct,
            top3Count = top3.size,
            topSupplierMonthlyImpact = top3.firstOrNull()?.monthlyAverage ?: BigDecimal.ZERO,
            topSupplierName = top3.firstOrNull()?.displayName,
            venueRankings = venueRankings,
            totalMergedVenueVariants = venueRes.totalMergedVariants,
        )
    }
}
