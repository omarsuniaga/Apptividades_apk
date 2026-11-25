package com.example.data

import com.example.data.port.QuoteFollowUpPort
import com.example.data.port.QuoteFunnelPort
import com.example.domain.model.BankAccount
import com.example.domain.model.Currency
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteLineItem
import com.example.domain.rules.QuoteStatus
import com.example.domain.rules.SavedQuote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

private class FakeQuotePort : QuoteFunnelPort {
    val rows = MutableStateFlow<Map<String, SavedQuote>>(emptyMap())
    override fun observeQuotes(): Flow<List<SavedQuote>> = rows.map { it.values.toList() }
    override suspend fun getQuote(id: String) = rows.value[id]
    override suspend fun upsert(quote: SavedQuote) { rows.value = rows.value + (quote.id to quote) }
    override suspend fun delete(id: String) { rows.value = rows.value - id }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

private class FakeFollowUp : QuoteFollowUpPort {
    val scheduled = mutableListOf<SavedQuote>()
    val cancelled = mutableListOf<String>()
    override fun schedule(quote: SavedQuote) { scheduled += quote }
    override fun cancel(quoteId: String) { cancelled += quoteId }
}

class QuoteFunnelManagerTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val port = FakeQuotePort()
    private val followUp = FakeFollowUp()
    private var ids = 0
    private var now = 1_000L
    private val manager = QuoteFunnelManager(
        port = port,
        followUp = followUp,
        clock = { now++ },
        idFactory = { "id-${++ids}" },
    )

    private fun quote(withBank: Boolean = true) = EventQuote(
        quoteNumber = "COT-20261125-01",
        clientName = "Iberostar",
        eventTitle = "Boda",
        eventDate = LocalDate.of(2026, 12, 1),
        eventTime = null,
        venueName = "Salón",
        issueDate = today,
        validUntilDate = today.plusDays(15),
        currency = Currency.DOP,
        items = listOf(QuoteLineItem("1", "Show", unitPrice = BigDecimal(5000))),
        issuerName = "Omar",
        bankAccounts = if (withBank) {
            listOf(BankAccount(id = "b1", ownerId = "o", label = "Principal", bankName = "Popular", holderName = "Omar", accountNumber = "1"))
        } else {
            emptyList()
        },
    )

    @Test
    fun differentQuotesOfTheSameDayGetDifferentNumbers() = runTest {
        val first = manager.prepare(null, quote(), today)
        manager.save(first, today, markSent = false)
        val second = manager.prepare(null, quote(), today)

        assertEquals("COT-20261125-01", first.quote.quoteNumber)
        assertEquals("COT-20261125-02", second.quote.quoteNumber)
        assertFalse(first.id == second.id)
    }

    @Test
    fun savingAgainInTheSameSessionUpdatesInsteadOfDuplicating() = runTest {
        val first = manager.prepare(null, quote(), today)
        manager.save(first, today, markSent = false)

        val again = manager.prepare(first.id, quote().copy(eventTitle = "Boda editada"), today)
        manager.save(again, today, markSent = false)

        assertEquals(1, port.rows.value.size)
        assertEquals(first.quote.quoteNumber, again.quote.quoteNumber)
        assertEquals("Boda editada", port.rows.value.getValue(first.id).quote.eventTitle)
    }

    @Test
    fun sharingMarksSentWithFollowUpAndSchedulesReminder() = runTest {
        val p = manager.prepare(null, quote(), today)
        val saved = manager.save(p, today, markSent = true)

        assertEquals(QuoteStatus.ENVIADA, saved.status)
        assertEquals(today.plusDays(3), saved.followUpDate)
        assertEquals(saved.id, followUp.scheduled.last().id)
        assertEquals(QuoteStatus.ENVIADA, followUp.scheduled.last().status)
    }

    @Test
    fun resavingNeverDowngradesAnAlreadySentQuote() = runTest {
        val p = manager.prepare(null, quote(), today)
        manager.save(p, today, markSent = true)
        val resaved = manager.save(p, today, markSent = false)
        assertEquals(QuoteStatus.ENVIADA, resaved.status)
    }

    @Test
    fun bankAccountsAreNotStoredButTheChoiceIsRemembered() = runTest {
        val withBank = manager.save(manager.prepare(null, quote(withBank = true), today), today, false)
        val withoutBank = manager.save(manager.prepare(null, quote(withBank = false), today), today, false)

        assertTrue(withBank.includeBankAccounts)
        assertTrue(withBank.quote.bankAccounts.isEmpty())
        assertFalse(withoutBank.includeBankAccounts)
    }

    @Test
    fun followUpDateMovesQuoteToFollowUpStage() = runTest {
        val saved = manager.save(manager.prepare(null, quote(), today), today, markSent = true)
        manager.scheduleFollowUp(saved.id, today.plusDays(7))

        val updated = port.rows.value.getValue(saved.id)
        assertEquals(QuoteStatus.SEGUIMIENTO, updated.status)
        assertEquals(today.plusDays(7), updated.followUpDate)
    }

    @Test
    fun wonAndLostClearTheReminder() = runTest {
        val a = manager.save(manager.prepare(null, quote(), today), today, markSent = true)
        manager.markWon(a.id)
        assertEquals(QuoteStatus.GANADA, followUp.scheduled.last().status)
        assertNull(followUp.scheduled.last().followUpDate)

        val b = manager.save(manager.prepare(null, quote(), today), today, markSent = true)
        manager.markLost(b.id, "Precio")
        assertEquals(QuoteStatus.PERDIDA, port.rows.value.getValue(b.id).status)
        assertEquals("Precio", port.rows.value.getValue(b.id).lostReason)
    }

    @Test
    fun reopenRestoresFollowUpAndConvertedFlagIsTracked() = runTest {
        val s = manager.save(manager.prepare(null, quote(), today), today, markSent = true)
        manager.markLost(s.id, "Precio")
        manager.reopen(s.id, today)
        assertEquals(QuoteStatus.SEGUIMIENTO, port.rows.value.getValue(s.id).status)

        manager.markWon(s.id)
        manager.markConverted(s.id)
        assertTrue(port.rows.value.getValue(s.id).convertedToActivity)
    }

    @Test
    fun deleteRemovesQuoteAndCancelsReminder() = runTest {
        val s = manager.save(manager.prepare(null, quote(), today), today, markSent = true)
        manager.delete(s.id)
        assertTrue(port.rows.value.isEmpty())
        assertEquals(listOf(s.id), followUp.cancelled)
    }

    @Test
    fun transitionsOnUnknownIdAreIgnored() = runTest {
        manager.markWon("missing")
        assertTrue(port.rows.value.isEmpty())
        assertTrue(followUp.scheduled.isEmpty())
    }
}
