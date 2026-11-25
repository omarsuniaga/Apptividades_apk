package com.example.data

import com.example.data.port.QuoteFollowUpPort
import com.example.data.port.QuoteFunnelPort
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteFunnelCalculator
import com.example.domain.rules.SavedQuote
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/** A quote that already has its final number and record id, ready to be rendered to PDF. */
data class PreparedQuote(
    val id: String,
    val quote: EventQuote,
)

/**
 * Application logic of the quote funnel: saving quotes coming from the creator and moving them
 * through the funnel stages, keeping the follow-up reminders in sync. Free of Android classes so it
 * can be unit tested with fake ports.
 */
class QuoteFunnelManager(
    private val port: QuoteFunnelPort,
    private val followUp: QuoteFollowUpPort,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * Assigns the record id and a unique quote number. A creator session that saves repeatedly keeps
     * the same id and number, so regenerating the PDF updates the quote instead of duplicating it.
     */
    suspend fun prepare(existingId: String?, quote: EventQuote, today: LocalDate): PreparedQuote {
        val existing = existingId?.let { port.getQuote(it) }
        if (existing != null) {
            return PreparedQuote(existing.id, quote.copy(quoteNumber = existing.quote.quoteNumber))
        }
        val taken = port.observeQuotes().first().map { it.quote.quoteNumber }
        return PreparedQuote(
            id = existingId ?: idFactory(),
            quote = quote.copy(quoteNumber = QuoteFunnelCalculator.nextQuoteNumber(today, taken)),
        )
    }

    /** Persists the quote. Status is never downgraded; [markSent] moves it to "Enviada" with a follow-up date. */
    suspend fun save(prepared: PreparedQuote, today: LocalDate, markSent: Boolean): SavedQuote {
        val now = clock()
        val stored = prepared.quote.copy(bankAccounts = emptyList())
        val existing = port.getQuote(prepared.id)
        var saved = existing?.copy(
            quote = stored,
            includeBankAccounts = prepared.quote.bankAccounts.isNotEmpty(),
            updatedAtMillis = now,
        ) ?: SavedQuote(
            id = prepared.id,
            quote = stored,
            includeBankAccounts = prepared.quote.bankAccounts.isNotEmpty(),
            createdAtMillis = now,
        )
        if (markSent) saved = QuoteFunnelCalculator.markSent(saved, today, now)
        persist(saved)
        return saved
    }

    suspend fun markSent(id: String, today: LocalDate) =
        update(id) { QuoteFunnelCalculator.markSent(it, today, clock()) }

    suspend fun scheduleFollowUp(id: String, date: LocalDate) =
        update(id) { QuoteFunnelCalculator.scheduleFollowUp(it, date, clock()) }

    suspend fun markWon(id: String) =
        update(id) { QuoteFunnelCalculator.markWon(it, clock()) }

    suspend fun markLost(id: String, reason: String) =
        update(id) { QuoteFunnelCalculator.markLost(it, reason, clock()) }

    suspend fun reopen(id: String, today: LocalDate) =
        update(id) { QuoteFunnelCalculator.reopen(it, today, clock()) }

    suspend fun markConverted(id: String) =
        update(id) { QuoteFunnelCalculator.markConverted(it, clock()) }

    suspend fun delete(id: String) {
        followUp.cancel(id)
        port.delete(id)
    }

    suspend fun deleteAll() {
        port.observeQuotes().first().forEach { followUp.cancel(it.id) }
        port.deleteAll()
    }

    private suspend fun update(id: String, transform: (SavedQuote) -> SavedQuote) {
        val current = port.getQuote(id) ?: return
        persist(transform(current))
    }

    private suspend fun persist(saved: SavedQuote) {
        port.upsert(saved)
        followUp.schedule(saved)
    }
}
