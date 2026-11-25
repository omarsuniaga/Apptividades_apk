package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.Currency
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

enum class EffectivePaymentState {
    UNPAID,
    PARTIAL,
    PAID,
}

data class ActivityPaymentBreakdown(
    val totalAmount: BigDecimal,
    val paidAmount: BigDecimal,
    val pendingBalance: BigDecimal,
    val partialPaidRecorded: BigDecimal,
    val totalExpenses: BigDecimal,
    val netProfit: BigDecimal,
    val state: EffectivePaymentState,
    val receiptCount: Int,
)

data class ExpenseCategoryBreakdown(
    val category: ExpenseCategory,
    val total: BigDecimal,
    val percent: Int,
    val count: Int,
)

data class NetProfitSummary12Months(
    val currency: Currency,
    val grossTotal: BigDecimal,
    val totalExpenses: BigDecimal,
    val netTotal: BigDecimal,
    val profitMarginPercent: Int,
    val averageNetPerMonth: BigDecimal,
    val averageNetPerGig: BigDecimal,
    val expensesByCategory: List<ExpenseCategoryBreakdown>,
    val totalExpenseEntries: Int,
) {
    val hasRecordedExpenses: Boolean get() = totalExpenses > BigDecimal.ZERO
}

/**
 * Pure domain rules for partial payments (anticipos/señas), automatic remaining balances,
 * per-activity expenses, and 12-month real net profit analytics.
 */
object ActivityFinanceCalculator {

    fun breakdown(
        activity: Activity,
        record: ActivityFinancialRecord?,
    ): ActivityPaymentBreakdown = breakdown(
        totalAmount = activity.amount,
        paymentStatus = activity.paymentStatus,
        record = record,
    )

    fun breakdown(
        totalAmount: BigDecimal,
        paymentStatus: PaymentStatus,
        record: ActivityFinancialRecord?,
    ): ActivityPaymentBreakdown {
        val safeTotal = if (totalAmount > BigDecimal.ZERO) totalAmount else BigDecimal.ZERO
        val partialRecorded = record?.totalPartialPaid ?: BigDecimal.ZERO
        val expenses = record?.totalExpenses ?: BigDecimal.ZERO

        val effectivePaid = if (paymentStatus == PaymentStatus.PAGADO) {
            safeTotal
        } else {
            partialRecorded.min(safeTotal).max(BigDecimal.ZERO)
        }

        val pending = safeTotal.subtract(effectivePaid).max(BigDecimal.ZERO)
        val state = when {
            paymentStatus == PaymentStatus.PAGADO -> EffectivePaymentState.PAID
            safeTotal > BigDecimal.ZERO && pending.compareTo(BigDecimal.ZERO) == 0 && effectivePaid > BigDecimal.ZERO ->
                EffectivePaymentState.PAID
            effectivePaid > BigDecimal.ZERO -> EffectivePaymentState.PARTIAL
            else -> EffectivePaymentState.UNPAID
        }

        val net = safeTotal.subtract(expenses)

        return ActivityPaymentBreakdown(
            totalAmount = safeTotal,
            paidAmount = effectivePaid,
            pendingBalance = pending,
            partialPaidRecorded = partialRecorded,
            totalExpenses = expenses,
            netProfit = net,
            state = state,
            receiptCount = record?.receiptCount ?: 0,
        )
    }

    /**
     * Computes real net profit and expense distribution over the same 12 complete months window
     * used by [BalanceAndOverviewCalculator.balance12Months].
     */
    fun summarize12Months(
        activities: List<Activity>,
        financialRecords: Map<String, ActivityFinancialRecord>,
        today: LocalDate,
        defaultCurrency: Currency = Currency.DOP,
        convertMixedCurrencies: Boolean = false,
        defaultUsdToDopRate: BigDecimal = CurrencyConverter.DEFAULT_USD_TO_DOP_RATE,
    ): NetProfitSummary12Months {
        val endMonth = YearMonth.from(today).minusMonths(1)
        val startMonth = endMonth.minusMonths(11)
        val monthSet = (0..11).map { startMonth.plusMonths(it.toLong()) }.toSet()

        val filtered = activities.filter {
            (convertMixedCurrencies || it.currency == defaultCurrency) &&
                YearMonth.from(it.eventDate) in monthSet
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

        val grossTotal = filtered.fold(BigDecimal.ZERO) { acc, act -> acc.add(convertFor(act, act.amount)) }

        val convertedExpenses = filtered.flatMap { act ->
            financialRecords[act.id]?.expenses.orEmpty()
                .filter { it.amount > BigDecimal.ZERO }
                .map { entry -> entry.category to convertFor(act, entry.amount) }
        }
        val totalExpenses = convertedExpenses.fold(BigDecimal.ZERO) { acc, (_, amt) -> acc.add(amt) }
        val netTotal = grossTotal.subtract(totalExpenses)

        val profitMarginPercent = if (grossTotal > BigDecimal.ZERO) {
            netTotal
                .multiply(BigDecimal(100))
                .divide(grossTotal, 4, RoundingMode.HALF_UP)
                .toDouble()
                .roundToInt()
        } else {
            0
        }

        val averageNetPerMonth = netTotal.divide(BigDecimal(12), 0, RoundingMode.HALF_UP)
        val averageNetPerGig = if (filtered.isNotEmpty()) {
            netTotal.divide(BigDecimal(filtered.size), 0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val byCategory = convertedExpenses
            .groupBy { it.first }
            .map { (cat, list) ->
                val sum = list.fold(BigDecimal.ZERO) { acc, (_, amt) -> acc.add(amt) }
                val pct = if (totalExpenses > BigDecimal.ZERO) {
                    sum.multiply(BigDecimal(100))
                        .divide(totalExpenses, 4, RoundingMode.HALF_UP)
                        .toDouble()
                        .roundToInt()
                        .coerceIn(0, 100)
                } else {
                    0
                }
                ExpenseCategoryBreakdown(
                    category = cat,
                    total = sum,
                    percent = pct,
                    count = list.size,
                )
            }
            .sortedByDescending { it.total }

        return NetProfitSummary12Months(
            currency = defaultCurrency,
            grossTotal = grossTotal,
            totalExpenses = totalExpenses,
            netTotal = netTotal,
            profitMarginPercent = profitMarginPercent,
            averageNetPerMonth = averageNetPerMonth,
            averageNetPerGig = averageNetPerGig,
            expensesByCategory = byCategory,
            totalExpenseEntries = convertedExpenses.size,
        )
    }
}
