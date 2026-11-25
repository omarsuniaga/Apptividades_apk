package com.example.domain.rules

import com.example.domain.model.ActivityDraft
import com.example.domain.model.ActivityType
import com.example.domain.model.Currency
import com.example.domain.model.GigCategory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Stages of the quote funnel. Open stages still need an answer from the client. */
enum class QuoteStatus(val label: String, val isOpen: Boolean) {
    BORRADOR("Borrador", true),
    ENVIADA("Enviada", true),
    SEGUIMIENTO("En seguimiento", true),
    GANADA("Ganada", false),
    PERDIDA("Perdida", false);

    /** Sent to the client and waiting for an answer. */
    val isAwaitingAnswer: Boolean
        get() = this == ENVIADA || this == SEGUIMIENTO

    companion object {
        fun fromName(raw: String?): QuoteStatus = entries.firstOrNull { it.name == raw } ?: BORRADOR
    }
}

/** A quote persisted locally together with its funnel tracking data. */
data class SavedQuote(
    val id: String,
    val quote: EventQuote,
    /** Bank accounts are not stored; they are re-attached from current accounts when the PDF is regenerated. */
    val includeBankAccounts: Boolean = true,
    val status: QuoteStatus = QuoteStatus.BORRADOR,
    val createdAtMillis: Long,
    val updatedAtMillis: Long = createdAtMillis,
    val sentAtMillis: Long? = null,
    val followUpDate: LocalDate? = null,
    val lostReason: String? = null,
    val convertedToActivity: Boolean = false,
) {
    val amount: BigDecimal get() = quote.subtotal
    val currency: Currency get() = quote.currency
}

data class QuoteFunnelSummary(
    val countsByStatus: Map<QuoteStatus, Int>,
    /** Potential money still in play (draft + sent + follow-up), per currency. */
    val openAmountByCurrency: Map<Currency, BigDecimal>,
    val wonAmountByCurrency: Map<Currency, BigDecimal>,
    val lostAmountByCurrency: Map<Currency, BigDecimal>,
    /** won / (won + lost) as a whole percentage; null while nothing has been decided yet. */
    val closeRatePercent: Int?,
    val followUpsDue: Int,
) {
    val totalCount: Int get() = countsByStatus.values.sum()
}

data class ClientFunnelStat(
    val displayName: String,
    val totalQuotes: Int,
    val openCount: Int,
    val wonCount: Int,
    val lostCount: Int,
    val closeRatePercent: Int?,
    val wonAmountByCurrency: Map<Currency, BigDecimal>,
)

object QuoteFunnelCalculator {

    const val DEFAULT_FOLLOW_UP_DAYS = 3L
    const val CLOSED_QUOTE_DEFAULT_REASON = "Sin motivo indicado"

    val LOST_REASON_PRESETS: List<String> = listOf(
        "Precio",
        "Eligió otro artista",
        "Fecha no disponible",
        "Sin respuesta",
        "Evento cancelado",
    )

    fun isFollowUpDue(saved: SavedQuote, today: LocalDate): Boolean =
        saved.status.isAwaitingAnswer && saved.followUpDate?.let { !it.isAfter(today) } == true

    /** A sent quote whose validity window ended without an answer. */
    fun isExpired(saved: SavedQuote, today: LocalDate): Boolean =
        saved.status.isAwaitingAnswer && saved.quote.validUntilDate.isBefore(today)

    fun closeRatePercent(won: Int, lost: Int): Int? {
        val decided = won + lost
        if (decided == 0) return null
        return BigDecimal(won * 100).divide(BigDecimal(decided), 0, RoundingMode.HALF_UP).toInt()
    }

    fun summarize(quotes: List<SavedQuote>, today: LocalDate): QuoteFunnelSummary {
        val counts = QuoteStatus.entries.associateWith { status -> quotes.count { it.status == status } }
        return QuoteFunnelSummary(
            countsByStatus = counts,
            openAmountByCurrency = sumByCurrency(quotes.filter { it.status.isOpen }),
            wonAmountByCurrency = sumByCurrency(quotes.filter { it.status == QuoteStatus.GANADA }),
            lostAmountByCurrency = sumByCurrency(quotes.filter { it.status == QuoteStatus.PERDIDA }),
            closeRatePercent = closeRatePercent(
                won = counts.getValue(QuoteStatus.GANADA),
                lost = counts.getValue(QuoteStatus.PERDIDA),
            ),
            followUpsDue = quotes.count { isFollowUpDue(it, today) },
        )
    }

    /** Per-client results, best closers first (then by volume). */
    fun statsByClient(quotes: List<SavedQuote>): List<ClientFunnelStat> =
        quotes
            .filter { it.quote.clientName.isNotBlank() }
            .groupBy { NameNormalizer.normalizeKey(it.quote.clientName) }
            .map { (_, group) ->
                val won = group.count { it.status == QuoteStatus.GANADA }
                val lost = group.count { it.status == QuoteStatus.PERDIDA }
                ClientFunnelStat(
                    displayName = group.maxByOrNull { it.updatedAtMillis }!!.quote.clientName.trim(),
                    totalQuotes = group.size,
                    openCount = group.count { it.status.isOpen },
                    wonCount = won,
                    lostCount = lost,
                    closeRatePercent = closeRatePercent(won, lost),
                    wonAmountByCurrency = sumByCurrency(group.filter { it.status == QuoteStatus.GANADA }),
                )
            }
            .sortedWith(
                compareByDescending<ClientFunnelStat> { it.wonCount }
                    .thenByDescending { it.totalQuotes }
                    .thenBy { it.displayName.lowercase() },
            )

    private fun sumByCurrency(quotes: List<SavedQuote>): Map<Currency, BigDecimal> =
        quotes
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.fold(BigDecimal.ZERO) { acc, q -> acc.add(q.amount) } }
            .toSortedMap(compareBy { it.ordinal })

    /** First free `COT-YYYYMMDD-NN` number for [date], given numbers that already exist. */
    fun nextQuoteNumber(date: LocalDate, existingNumbers: Collection<String>): String {
        val taken = existingNumbers.toHashSet()
        for (seq in 1..999) {
            val candidate = QuoteCalculator.buildQuoteNumber(date, seq)
            if (candidate !in taken) return candidate
        }
        return QuoteCalculator.buildQuoteNumber(date, 999)
    }

    // ---- State transitions (pure; the caller persists the result) ----

    fun markSent(saved: SavedQuote, today: LocalDate, nowMillis: Long): SavedQuote {
        if (!saved.status.isOpen) return saved
        return saved.copy(
            status = if (saved.status == QuoteStatus.SEGUIMIENTO) QuoteStatus.SEGUIMIENTO else QuoteStatus.ENVIADA,
            sentAtMillis = saved.sentAtMillis ?: nowMillis,
            followUpDate = saved.followUpDate ?: today.plusDays(DEFAULT_FOLLOW_UP_DAYS),
            updatedAtMillis = nowMillis,
        )
    }

    fun scheduleFollowUp(saved: SavedQuote, date: LocalDate, nowMillis: Long): SavedQuote {
        if (!saved.status.isOpen) return saved
        return saved.copy(
            status = QuoteStatus.SEGUIMIENTO,
            sentAtMillis = saved.sentAtMillis ?: nowMillis,
            followUpDate = date,
            updatedAtMillis = nowMillis,
        )
    }

    fun markWon(saved: SavedQuote, nowMillis: Long): SavedQuote =
        saved.copy(
            status = QuoteStatus.GANADA,
            followUpDate = null,
            lostReason = null,
            updatedAtMillis = nowMillis,
        )

    fun markLost(saved: SavedQuote, reason: String, nowMillis: Long): SavedQuote =
        saved.copy(
            status = QuoteStatus.PERDIDA,
            followUpDate = null,
            lostReason = reason.trim().ifBlank { CLOSED_QUOTE_DEFAULT_REASON },
            updatedAtMillis = nowMillis,
        )

    fun reopen(saved: SavedQuote, today: LocalDate, nowMillis: Long): SavedQuote {
        if (saved.status.isOpen) return saved
        val wasSent = saved.sentAtMillis != null
        return saved.copy(
            status = if (wasSent) QuoteStatus.SEGUIMIENTO else QuoteStatus.BORRADOR,
            followUpDate = if (wasSent) today.plusDays(DEFAULT_FOLLOW_UP_DAYS) else null,
            lostReason = null,
            convertedToActivity = false,
            updatedAtMillis = nowMillis,
        )
    }

    fun markConverted(saved: SavedQuote, nowMillis: Long): SavedQuote =
        saved.copy(convertedToActivity = true, updatedAtMillis = nowMillis)

    /** The quote as it must be rendered to PDF: bank accounts re-attached from the user's current accounts. */
    fun quoteForPdf(saved: SavedQuote, bankAccounts: List<com.example.domain.model.BankAccount>): EventQuote {
        if (!saved.includeBankAccounts) return saved.quote.copy(bankAccounts = emptyList())
        val byCurrency = bankAccounts.filter { it.currency == saved.currency }
        return saved.quote.copy(bankAccounts = byCurrency.ifEmpty { bankAccounts })
    }

    // ---- Won quote -> agenda activity ----

    /** A quote can become an activity only when it already has an event date. */
    fun canConvertToActivity(saved: SavedQuote): Boolean =
        saved.status == QuoteStatus.GANADA && !saved.convertedToActivity && saved.quote.eventDate != null

    fun toActivityDraft(saved: SavedQuote): ActivityDraft? {
        val q = saved.quote
        val date = q.eventDate ?: return null
        val title = q.eventTitle.trim().ifBlank { q.items.firstOrNull()?.description?.trim().orEmpty() }
        return ActivityDraft(
            supplierName = q.clientName.trim(),
            category = GigCategory.infer(ActivityType.EVENTUAL, title),
            eventDate = date,
            eventTime = q.eventTime,
            venueName = q.venueName.trim(),
            description = title.ifBlank { null },
            amount = saved.amount,
            currency = q.currency,
            isPaid = false,
            notes = "Cotización ${q.quoteNumber} ganada.",
        )
    }
}
