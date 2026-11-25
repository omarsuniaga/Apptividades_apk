package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class ReceivableStatus(val sortOrder: Int) {
    VENCIDO(0),
    ESTE_MES(1),
    PROXIMO_MES(2),
}

data class ReceivableGroup(
    val groupId: String,
    val supplierKey: String,
    val supplierDisplayName: String,
    val status: ReceivableStatus,
    val currency: Currency,
    val activities: List<Activity>,
    val totalAmount: BigDecimal,
    val oldestDate: LocalDate,
    val daysOutstanding: Long,
    val primaryVenue: String?,
    val totalPartialDeposits: BigDecimal = BigDecimal.ZERO,
) {
    val count: Int get() = activities.size
    val activityIds: List<String> get() = activities.map { it.id }
    val sortedDates: List<LocalDate> get() = activities.map { it.eventDate }.sorted()
}

data class ReceivablesSummary(
    val groups: List<ReceivableGroup>,
    val defaultCurrency: Currency,
    val totalReceivable: BigDecimal,
    val overdueTotal: BigDecimal,
    val dueThisMonthTotal: BigDecimal,
    val dueNextMonthTotal: BigDecimal,
) {
    val inTermTotal: BigDecimal get() = dueThisMonthTotal.add(dueNextMonthTotal)
    val overdueGroups: List<ReceivableGroup> get() = groups.filter { it.status == ReceivableStatus.VENCIDO }
    val dueThisMonthGroups: List<ReceivableGroup> get() = groups.filter { it.status == ReceivableStatus.ESTE_MES }
    val dueNextMonthGroups: List<ReceivableGroup> get() = groups.filter { it.status == ReceivableStatus.PROXIMO_MES }
}

/**
 * Rule 2.3 #1: Month-in-arrears receivables.
 *
 * Only gigs with payment_status == 'pendiente' AND event_date < today are receivable.
 * Let M be the gig's month and C be the current month:
 *   C > M + 1 -> vencido (overdue)
 *   C == M + 1 -> este mes (collect this month)
 *   C == M -> próximo mes (collect next month)
 */
object ReceivablesCalculator {

    fun classify(eventDate: LocalDate, today: LocalDate): ReceivableStatus {
        val gigMonth = YearMonth.from(eventDate)
        val currentMonth = YearMonth.from(today)
        val monthsDiff = ChronoUnit.MONTHS.between(gigMonth, currentMonth)
        return when {
            monthsDiff > 1L -> ReceivableStatus.VENCIDO
            monthsDiff == 1L -> ReceivableStatus.ESTE_MES
            else -> ReceivableStatus.PROXIMO_MES
        }
    }

    fun calculate(
        activities: List<Activity>,
        today: LocalDate,
        defaultCurrency: Currency = Currency.DOP,
        noSupplierLabel: String = "Sin proveedor",
        financialRecords: Map<String, ActivityFinancialRecord> = emptyMap(),
    ): ReceivablesSummary {
        val supplierResolution = NameNormalizer.buildSupplierResolution(
            activities.map { it.supplierName }
        )
        val venueResolution = NameNormalizer.buildVenueResolution(
            activities.map { it.venueName }
        )

        val receivableActivities = activities.filter { act ->
            if (act.paymentStatus != PaymentStatus.PENDIENTE || !act.eventDate.isBefore(today)) {
                false
            } else {
                val breakdown = ActivityFinanceCalculator.breakdown(act, financialRecords[act.id])
                act.amount.compareTo(BigDecimal.ZERO) == 0 || breakdown.pendingBalance > BigDecimal.ZERO
            }
        }

        data class GroupKey(
            val supplierKey: String,
            val status: ReceivableStatus,
            val currency: Currency,
        )

        val grouped = receivableActivities.groupBy { activity ->
            val rawKey = supplierResolution.canonicalKey(activity.supplierName)
            val key = rawKey.ifEmpty { "__no_supplier__" }
            GroupKey(
                supplierKey = key,
                status = classify(activity.eventDate, today),
                currency = activity.currency,
            )
        }

        val groups = grouped.map { (key, items) ->
            val sortedItems = items.sortedWith(compareBy<Activity> { it.eventDate }.thenBy { it.eventTime })
            val oldestDate = sortedItems.first().eventDate
            val daysOutstanding = ChronoUnit.DAYS.between(oldestDate, today).coerceAtLeast(0L)
            val displayName = if (key.supplierKey == "__no_supplier__") {
                noSupplierLabel
            } else {
                supplierResolution.canonicalToDisplay[key.supplierKey]
                    ?: sortedItems.first().supplierName.trim().ifEmpty { noSupplierLabel }
            }
            val primaryVenue = sortedItems
                .map { venueResolution.displayName(it.venueName) }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key

            val breakdowns = sortedItems.map { ActivityFinanceCalculator.breakdown(it, financialRecords[it.id]) }
            val total = breakdowns.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.pendingBalance) }
            val deposits = breakdowns.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.paidAmount) }

            ReceivableGroup(
                groupId = "${key.supplierKey}_${key.status.name}_${key.currency.code}",
                supplierKey = key.supplierKey,
                supplierDisplayName = displayName,
                status = key.status,
                currency = key.currency,
                activities = sortedItems,
                totalAmount = total,
                oldestDate = oldestDate,
                daysOutstanding = daysOutstanding,
                primaryVenue = primaryVenue,
                totalPartialDeposits = deposits,
            )
        }.sortedWith(
            compareBy<ReceivableGroup> { it.status.sortOrder }
                .thenByDescending { it.totalAmount }
                .thenBy { it.supplierDisplayName }
        )

        fun sumFor(status: ReceivableStatus? = null): BigDecimal =
            groups
                .filter { it.currency == defaultCurrency && (status == null || it.status == status) }
                .fold(BigDecimal.ZERO) { acc, g -> acc.add(g.totalAmount) }

        return ReceivablesSummary(
            groups = groups,
            defaultCurrency = defaultCurrency,
            totalReceivable = sumFor(null),
            overdueTotal = sumFor(ReceivableStatus.VENCIDO),
            dueThisMonthTotal = sumFor(ReceivableStatus.ESTE_MES),
            dueNextMonthTotal = sumFor(ReceivableStatus.PROXIMO_MES),
        )
    }
}
