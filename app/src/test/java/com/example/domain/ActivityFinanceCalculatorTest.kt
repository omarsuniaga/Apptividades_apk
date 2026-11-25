package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityExpenseEntry
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityPaymentEntry
import com.example.domain.model.Currency
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.ActivityFinanceCalculator
import com.example.domain.rules.BalanceAndOverviewCalculator
import com.example.domain.rules.EffectivePaymentState
import com.example.domain.rules.ReceivablesCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class ActivityFinanceCalculatorTest {

    private fun sampleActivity(
        id: String,
        date: LocalDate,
        amount: String,
        status: PaymentStatus = PaymentStatus.PENDIENTE,
        supplier: String = "Eventos Bávaro",
        currency: Currency = Currency.DOP,
    ) = Activity(
        id = id,
        ownerId = "user-1",
        title = supplier,
        supplierName = supplier,
        venueName = "Hotel Paradisus",
        eventDate = date,
        eventTime = LocalTime.of(19, 0),
        amount = BigDecimal(amount),
        currency = currency,
        paymentStatus = status,
    )

    @Test
    fun `breakdown handles unpaid, partial, over-deposited, paid, expenses and receipt count`() {
        val baseDate = LocalDate.of(2026, 5, 10)
        val act = sampleActivity("a1", baseDate, "20000")

        // Unpaid with no record
        val unpaid = ActivityFinanceCalculator.breakdown(act, null)
        assertEquals(EffectivePaymentState.UNPAID, unpaid.state)
        assertEquals(0, unpaid.paidAmount.compareTo(BigDecimal.ZERO))
        assertEquals(0, unpaid.pendingBalance.compareTo(BigDecimal("20000")))
        assertEquals(0, unpaid.netProfit.compareTo(BigDecimal("20000")))
        assertEquals(0, unpaid.receiptCount)

        // Partial deposit + expenses + receipts
        val partialRecord = ActivityFinancialRecord(
            activityId = "a1",
            payments = listOf(
                ActivityPaymentEntry(
                    amount = BigDecimal("8000"),
                    date = baseDate.minusDays(5),
                    note = "Seña 40%",
                    receiptFileName = "rcpt_1.jpg",
                )
            ),
            expenses = listOf(
                ActivityExpenseEntry(
                    category = ExpenseCategory.TRANSPORT,
                    amount = BigDecimal("1500"),
                    note = "Gasolina",
                    receiptFileName = "rcpt_2.jpg",
                ),
                ActivityExpenseEntry(
                    category = ExpenseCategory.MUSICIANS,
                    amount = BigDecimal("3500"),
                    note = "Percusionista",
                ),
            ),
        )
        val partial = ActivityFinanceCalculator.breakdown(act, partialRecord)
        assertEquals(EffectivePaymentState.PARTIAL, partial.state)
        assertEquals(0, partial.paidAmount.compareTo(BigDecimal("8000")))
        assertEquals(0, partial.pendingBalance.compareTo(BigDecimal("12000")))
        assertEquals(0, partial.totalExpenses.compareTo(BigDecimal("5000")))
        assertEquals(0, partial.netProfit.compareTo(BigDecimal("15000")))
        assertEquals(2, partial.receiptCount)

        // Over-deposited clamps effectivePaid to total and marks PAID
        val overRecord = ActivityFinancialRecord(
            activityId = "a1",
            payments = listOf(
                ActivityPaymentEntry(amount = BigDecimal("15000"), date = baseDate),
                ActivityPaymentEntry(amount = BigDecimal("8000"), date = baseDate),
            ),
        )
        val settledByDeposit = ActivityFinanceCalculator.breakdown(act, overRecord)
        assertEquals(EffectivePaymentState.PAID, settledByDeposit.state)
        assertEquals(0, settledByDeposit.paidAmount.compareTo(BigDecimal("20000")))
        assertEquals(0, settledByDeposit.pendingBalance.compareTo(BigDecimal.ZERO))

        // Explicitly PAGADO always has 0 pending
        val paidAct = act.copy(paymentStatus = PaymentStatus.PAGADO)
        val explicitPaid = ActivityFinanceCalculator.breakdown(paidAct, partialRecord)
        assertEquals(EffectivePaymentState.PAID, explicitPaid.state)
        assertEquals(0, explicitPaid.paidAmount.compareTo(BigDecimal("20000")))
        assertEquals(0, explicitPaid.pendingBalance.compareTo(BigDecimal.ZERO))
        assertEquals(0, explicitPaid.netProfit.compareTo(BigDecimal("15000")))
    }

    @Test
    fun `receivables deduct partial deposits and omit fully covered activities`() {
        val today = LocalDate.of(2026, 6, 15)
        val aPartial = sampleActivity("a1", LocalDate.of(2026, 4, 20), "20000")
        val aCovered = sampleActivity("a2", LocalDate.of(2026, 4, 22), "10000")
        val activities = listOf(aPartial, aCovered)
        val records = mapOf(
            "a1" to ActivityFinancialRecord(
                activityId = "a1",
                payments = listOf(ActivityPaymentEntry(amount = BigDecimal("5000"), date = LocalDate.of(2026, 4, 15))),
            ),
            "a2" to ActivityFinancialRecord(
                activityId = "a2",
                payments = listOf(ActivityPaymentEntry(amount = BigDecimal("10000"), date = LocalDate.of(2026, 4, 22))),
            ),
        )

        val summary = ReceivablesCalculator.calculate(
            activities = activities,
            today = today,
            defaultCurrency = Currency.DOP,
            financialRecords = records,
        )

        assertEquals(1, summary.groups.size)
        val group = summary.groups.first()
        assertEquals(1, group.count)
        assertEquals(listOf("a1"), group.activityIds)
        assertEquals(0, group.totalAmount.compareTo(BigDecimal("15000")))
        assertEquals(0, group.totalPartialDeposits.compareTo(BigDecimal("5000")))
        assertEquals(0, summary.totalReceivable.compareTo(BigDecimal("15000")))
    }

    @Test
    fun `monthOverview counts partial deposits in collectedAmount and computes netProfit`() {
        val today = LocalDate.of(2026, 6, 15)
        val a1 = sampleActivity("a1", LocalDate.of(2026, 6, 5), "20000", PaymentStatus.PENDIENTE)
        val a2 = sampleActivity("a2", LocalDate.of(2026, 6, 10), "15000", PaymentStatus.PAGADO)
        val records = mapOf(
            "a1" to ActivityFinancialRecord(
                activityId = "a1",
                payments = listOf(ActivityPaymentEntry(amount = BigDecimal("6000"), date = LocalDate.of(2026, 6, 1))),
                expenses = listOf(ActivityExpenseEntry(category = ExpenseCategory.TRANSPORT, amount = BigDecimal("2000"))),
            ),
            "a2" to ActivityFinancialRecord(
                activityId = "a2",
                expenses = listOf(ActivityExpenseEntry(category = ExpenseCategory.SOUND, amount = BigDecimal("3000"))),
            ),
        )

        val overview = BalanceAndOverviewCalculator.monthOverview(
            activities = listOf(a1, a2),
            today = today,
            monthlyGoal = BigDecimal("50000"),
            defaultCurrency = Currency.DOP,
            targetMonth = YearMonth.of(2026, 6),
            financialRecords = records,
        )

        assertEquals(0, overview.billed.compareTo(BigDecimal("35000")))
        assertEquals(0, overview.collectedAmount.compareTo(BigDecimal("21000")))
        assertEquals(0, overview.expensesTotal.compareTo(BigDecimal("5000")))
        assertEquals(0, overview.netProfit.compareTo(BigDecimal("30000")))
    }

    @Test
    fun `accountStatement reflects pending balance and annotates partial deposits`() {
        val today = LocalDate.of(2026, 6, 15)
        val a1 = sampleActivity("a1", LocalDate.of(2026, 5, 10), "18000", supplier = "Resort Caribe")
        val records = mapOf(
            "a1" to ActivityFinancialRecord(
                activityId = "a1",
                payments = listOf(ActivityPaymentEntry(amount = BigDecimal("6000"), date = LocalDate.of(2026, 5, 1))),
            )
        )

        val stmt = AccountStatementBuilder.build(
            supplierName = "Resort Caribe",
            currency = Currency.DOP,
            activities = listOf(a1),
            bankAccounts = emptyList(),
            today = today,
            financialRecords = records,
        )

        assertEquals(1, stmt.items.size)
        assertEquals(0, stmt.items.first().amount.compareTo(BigDecimal("12000")))
        assertEquals(0, stmt.totalPending.compareTo(BigDecimal("12000")))
        assertTrue(stmt.items.first().serviceDescription.contains("abono:"))
    }

    @Test
    fun `summarize12Months aggregates gross, expenses, net profit, margin and categories`() {
        val today = LocalDate.of(2026, 6, 15) // Window: Jun 2025 – May 2026
        val inWindow1 = sampleActivity("w1", LocalDate.of(2026, 3, 10), "60000", PaymentStatus.PAGADO)
        val inWindow2 = sampleActivity("w2", LocalDate.of(2026, 4, 12), "40000", PaymentStatus.PAGADO)
        val currentMonthExcluded = sampleActivity("curr", LocalDate.of(2026, 6, 5), "50000", PaymentStatus.PAGADO)

        val records = mapOf(
            "w1" to ActivityFinancialRecord(
                activityId = "w1",
                expenses = listOf(
                    ActivityExpenseEntry(category = ExpenseCategory.MUSICIANS, amount = BigDecimal("15000")),
                    ActivityExpenseEntry(category = ExpenseCategory.TRANSPORT, amount = BigDecimal("3000")),
                ),
            ),
            "w2" to ActivityFinancialRecord(
                activityId = "w2",
                expenses = listOf(
                    ActivityExpenseEntry(category = ExpenseCategory.TRANSPORT, amount = BigDecimal("2000")),
                ),
            ),
            "curr" to ActivityFinancialRecord(
                activityId = "curr",
                expenses = listOf(
                    ActivityExpenseEntry(category = ExpenseCategory.SOUND, amount = BigDecimal("9999")),
                ),
            ),
        )

        val summary = ActivityFinanceCalculator.summarize12Months(
            activities = listOf(inWindow1, inWindow2, currentMonthExcluded),
            financialRecords = records,
            today = today,
            defaultCurrency = Currency.DOP,
        )

        assertTrue(summary.hasRecordedExpenses)
        assertEquals(0, summary.grossTotal.compareTo(BigDecimal("100000")))
        assertEquals(0, summary.totalExpenses.compareTo(BigDecimal("20000")))
        assertEquals(0, summary.netTotal.compareTo(BigDecimal("80000")))
        assertEquals(80, summary.profitMarginPercent)
        assertEquals(0, summary.averageNetPerGig.compareTo(BigDecimal("40000")))
        assertEquals(3, summary.totalExpenseEntries)
        assertEquals(2, summary.expensesByCategory.size)
        assertEquals(ExpenseCategory.MUSICIANS, summary.expensesByCategory[0].category)
        assertEquals(75, summary.expensesByCategory[0].percent)
        assertEquals(ExpenseCategory.TRANSPORT, summary.expensesByCategory[1].category)
        assertEquals(25, summary.expensesByCategory[1].percent)

        // Without expenses
        val noExp = ActivityFinanceCalculator.summarize12Months(
            activities = listOf(inWindow1),
            financialRecords = emptyMap(),
            today = today,
            defaultCurrency = Currency.DOP,
        )
        assertFalse(noExp.hasRecordedExpenses)
        assertEquals(100, noExp.profitMarginPercent)
    }
}
