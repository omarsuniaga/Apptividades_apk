package com.example.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class ExpenseCategory(val code: String) {
    TRANSPORT("transport"),
    MUSICIANS("musicians"),
    SOUND("sound"),
    LODGING_FOOD("lodging_food"),
    OTHER("other");

    companion object {
        fun fromCode(raw: String?): ExpenseCategory =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

data class ActivityPaymentEntry(
    val id: String = UUID.randomUUID().toString(),
    val amount: BigDecimal,
    val date: LocalDate,
    val note: String = "",
    val receiptFileName: String? = null,
)

data class ActivityExpenseEntry(
    val id: String = UUID.randomUUID().toString(),
    val category: ExpenseCategory = ExpenseCategory.TRANSPORT,
    val amount: BigDecimal,
    val note: String = "",
    val receiptFileName: String? = null,
)

/**
 * Local per-activity financial ledger holding partial payments (anticipos/señas),
 * attached receipt file names (stored inside app-private filesDir/receipts),
 * and gig-related expenses used to compute real net profit.
 */
data class ActivityFinancialRecord(
    val activityId: String = "",
    val payments: List<ActivityPaymentEntry> = emptyList(),
    val expenses: List<ActivityExpenseEntry> = emptyList(),
    val exchangeRateToDop: BigDecimal? = null,
) {
    val totalPartialPaid: BigDecimal
        get() = payments.fold(BigDecimal.ZERO) { acc, item ->
            if (item.amount > BigDecimal.ZERO) acc.add(item.amount) else acc
        }

    val totalExpenses: BigDecimal
        get() = expenses.fold(BigDecimal.ZERO) { acc, item ->
            if (item.amount > BigDecimal.ZERO) acc.add(item.amount) else acc
        }

    val receiptCount: Int
        get() = payments.count { !it.receiptFileName.isNullOrBlank() } +
            expenses.count { !it.receiptFileName.isNullOrBlank() }

    val hasCustomExchangeRate: Boolean
        get() = exchangeRateToDop != null && exchangeRateToDop > BigDecimal.ZERO

    val isEmpty: Boolean
        get() = payments.isEmpty() && expenses.isEmpty() && !hasCustomExchangeRate
}
