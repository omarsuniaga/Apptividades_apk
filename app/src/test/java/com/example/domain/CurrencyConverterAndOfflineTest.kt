package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.ActivityExpenseEntry
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.Currency
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.ActivityFinanceCalculator
import com.example.domain.rules.BalanceAndOverviewCalculator
import com.example.domain.rules.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CurrencyConverterAndOfflineTest {

    private fun gig(
        id: String,
        date: LocalDate,
        amount: String,
        currency: Currency,
        paymentStatus: PaymentStatus = PaymentStatus.PAGADO,
        supplier: String = "Producciones Caribe",
    ) = Activity(
        id = id,
        ownerId = "user-1",
        title = "Toque $id",
        eventDate = date,
        eventTime = LocalTime.of(19, 0),
        venueName = "Hard Rock Punta Cana",
        supplierName = supplier,
        amount = BigDecimal(amount),
        currency = currency,
        paymentStatus = paymentStatus,
    )

    @Test
    fun converter_usesHistoricalRate_andFallsBackCleanly() {
        // Locked historical rate takes precedence over fallback
        val locked = CurrencyConverter.convert(
            amount = BigDecimal("250"),
            from = Currency.USD,
            to = Currency.DOP,
            historicalUsdToDopRate = BigDecimal("58.50"),
            fallbackUsdToDopRate = BigDecimal("61.00"),
        )
        assertEquals(0, locked.compareTo(BigDecimal("14625.00")))

        // Null or invalid historical rate falls back to user's configured default
        val fallback = CurrencyConverter.convert(
            amount = BigDecimal("200"),
            from = Currency.USD,
            to = Currency.DOP,
            historicalUsdToDopRate = BigDecimal.ZERO,
            fallbackUsdToDopRate = BigDecimal("60.50"),
        )
        assertEquals(0, fallback.compareTo(BigDecimal("12100.00")))

        // Reverse conversion DOP -> USD
        val dopToUsd = CurrencyConverter.convert(
            amount = BigDecimal("12000"),
            from = Currency.DOP,
            to = Currency.USD,
            historicalUsdToDopRate = BigDecimal("60.00"),
        )
        assertEquals(0, dopToUsd.compareTo(BigDecimal("200.00")))

        // Same currency is a no-op
        val same = CurrencyConverter.convert(
            amount = BigDecimal("7500"),
            from = Currency.DOP,
            to = Currency.DOP,
            historicalUsdToDopRate = BigDecimal("59.00"),
        )
        assertEquals(0, same.compareTo(BigDecimal("7500")))
    }

    @Test
    fun monthOverview_unifiesMixedCurrenciesUsingPerActivityHistoricalRates() {
        val today = LocalDate.of(2026, 4, 20)
        val activities = listOf(
            gig("g1", LocalDate.of(2026, 4, 5), "10000", Currency.DOP),
            gig("g2", LocalDate.of(2026, 4, 10), "200", Currency.USD), // locked at 58.50 -> 11,700
            gig("g3", LocalDate.of(2026, 4, 15), "100", Currency.USD), // locked at 61.00 -> 6,100
        )
        val records = mapOf(
            "g2" to ActivityFinancialRecord(
                activityId = "g2",
                exchangeRateToDop = BigDecimal("58.50"),
            ),
            "g3" to ActivityFinancialRecord(
                activityId = "g3",
                exchangeRateToDop = BigDecimal("61.00"),
            ),
        )

        val unified = BalanceAndOverviewCalculator.monthOverview(
            activities = activities,
            today = today,
            monthlyGoal = BigDecimal("50000"),
            defaultCurrency = Currency.DOP,
            targetMonth = YearMonth.of(2026, 4),
            financialRecords = records,
            convertMixedCurrencies = true,
            defaultUsdToDopRate = BigDecimal("60.00"),
        )
        // 10,000 + 11,700 + 6,100 = 27,800
        assertEquals(0, unified.billed.compareTo(BigDecimal("27800.00")))
        assertEquals(0, unified.collectedAmount.compareTo(BigDecimal("27800.00")))

        val dopOnly = BalanceAndOverviewCalculator.monthOverview(
            activities = activities,
            today = today,
            monthlyGoal = BigDecimal("50000"),
            defaultCurrency = Currency.DOP,
            targetMonth = YearMonth.of(2026, 4),
            financialRecords = records,
            convertMixedCurrencies = false,
            defaultUsdToDopRate = BigDecimal("60.00"),
        )
        assertEquals(0, dopOnly.billed.compareTo(BigDecimal("10000")))
    }

    @Test
    fun balanceAndNetProfit12Months_convertGrossAndExpensesWithHistoricalRate() {
        val today = LocalDate.of(2026, 5, 10)
        val activities = listOf(
            gig("g1", LocalDate.of(2026, 3, 12), "15000", Currency.DOP),
            gig("g2", LocalDate.of(2026, 4, 4), "300", Currency.USD), // rate 59.00 -> 17,700 gross; 50 USD exp -> 2,950
        )
        val records = mapOf(
            "g1" to ActivityFinancialRecord(
                activityId = "g1",
                expenses = listOf(
                    ActivityExpenseEntry(
                        id = "e1",
                        amount = BigDecimal("2000"),
                        category = ExpenseCategory.TRANSPORT,
                        note = "Gasolina",
                    )
                ),
            ),
            "g2" to ActivityFinancialRecord(
                activityId = "g2",
                exchangeRateToDop = BigDecimal("59.00"),
                expenses = listOf(
                    ActivityExpenseEntry(
                        id = "e2",
                        amount = BigDecimal("50"),
                        category = ExpenseCategory.MUSICIANS,
                        note = "Percusionista",
                    )
                ),
            ),
        )

        val balance = BalanceAndOverviewCalculator.balance12Months(
            activities = activities,
            today = today,
            monthlyGoal = BigDecimal("40000"),
            defaultCurrency = Currency.DOP,
            financialRecords = records,
            convertMixedCurrencies = true,
            defaultUsdToDopRate = BigDecimal("60.00"),
        )
        assertEquals(2, balance.totalGigs)
        assertEquals(0, balance.totalAmount.compareTo(BigDecimal("32700.00")))

        val net = ActivityFinanceCalculator.summarize12Months(
            activities = activities,
            financialRecords = records,
            today = today,
            defaultCurrency = Currency.DOP,
            convertMixedCurrencies = true,
            defaultUsdToDopRate = BigDecimal("60.00"),
        )
        // Gross: 15,000 + 17,700 = 32,700
        // Expenses: 2,000 + 2,950 = 4,950
        // Net: 27,750
        assertEquals(0, net.grossTotal.compareTo(BigDecimal("32700.00")))
        assertEquals(0, net.totalExpenses.compareTo(BigDecimal("4950.00")))
        assertEquals(0, net.netTotal.compareTo(BigDecimal("27750.00")))
    }
}
