package com.example.domain

import com.example.domain.model.Currency
import com.example.domain.model.GigCategory
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteFunnelCalculator
import com.example.domain.rules.QuoteLineItem
import com.example.domain.rules.QuoteStatus
import com.example.domain.rules.SavedQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class QuoteFunnelCalculatorTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun quote(
        number: String = "COT-20261125-01",
        client: String = "Hotel Lopesan",
        title: String = "Boda - Ceremonia",
        price: String = "7000",
        currency: Currency = Currency.DOP,
        eventDate: LocalDate? = LocalDate.of(2026, 12, 20),
        validUntil: LocalDate = today.plusDays(15),
    ) = EventQuote(
        quoteNumber = number,
        clientName = client,
        eventTitle = title,
        eventDate = eventDate,
        eventTime = LocalTime.of(17, 0),
        venueName = "Lopesan",
        issueDate = today,
        validUntilDate = validUntil,
        currency = currency,
        items = listOf(QuoteLineItem("1", "Presentación", unitPrice = BigDecimal(price))),
        issuerName = "Omar",
    )

    private fun saved(
        id: String,
        status: QuoteStatus = QuoteStatus.BORRADOR,
        q: EventQuote = quote(),
        followUp: LocalDate? = null,
        updated: Long = 1_000,
    ) = SavedQuote(
        id = id,
        quote = q,
        status = status,
        createdAtMillis = 1_000,
        updatedAtMillis = updated,
        followUpDate = followUp,
    )

    @Test
    fun closeRateIsWonOverDecidedAndNullWhenNothingDecided() {
        assertNull(QuoteFunnelCalculator.closeRatePercent(0, 0))
        assertEquals(67, QuoteFunnelCalculator.closeRatePercent(2, 1))
        assertEquals(0, QuoteFunnelCalculator.closeRatePercent(0, 3))
        assertEquals(100, QuoteFunnelCalculator.closeRatePercent(4, 0))
    }

    @Test
    fun summaryCountsStatusesAndSumsAmountsPerCurrency() {
        val quotes = listOf(
            saved("a", QuoteStatus.BORRADOR, quote(price = "1000")),
            saved("b", QuoteStatus.ENVIADA, quote(price = "2000"), followUp = today.minusDays(1)),
            saved("c", QuoteStatus.SEGUIMIENTO, quote(price = "500", currency = Currency.USD), followUp = today),
            saved("d", QuoteStatus.GANADA, quote(price = "7000")),
            saved("e", QuoteStatus.GANADA, quote(price = "3000")),
            saved("f", QuoteStatus.PERDIDA, quote(price = "4000")),
        )
        val s = QuoteFunnelCalculator.summarize(quotes, today)

        assertEquals(6, s.totalCount)
        assertEquals(2, s.countsByStatus.getValue(QuoteStatus.GANADA))
        assertEquals(0, BigDecimal(3000).compareTo(s.openAmountByCurrency.getValue(Currency.DOP)))
        assertEquals(0, BigDecimal(500).compareTo(s.openAmountByCurrency.getValue(Currency.USD)))
        assertEquals(0, BigDecimal(10000).compareTo(s.wonAmountByCurrency.getValue(Currency.DOP)))
        assertEquals(0, BigDecimal(4000).compareTo(s.lostAmountByCurrency.getValue(Currency.DOP)))
        assertEquals(67, s.closeRatePercent)
        assertEquals(2, s.followUpsDue)
    }

    @Test
    fun followUpIsDueOnlyForAwaitingQuotesWhoseDateArrived() {
        assertTrue(QuoteFunnelCalculator.isFollowUpDue(saved("a", QuoteStatus.ENVIADA, followUp = today), today))
        assertFalse(QuoteFunnelCalculator.isFollowUpDue(saved("b", QuoteStatus.ENVIADA, followUp = today.plusDays(1)), today))
        assertFalse(QuoteFunnelCalculator.isFollowUpDue(saved("c", QuoteStatus.GANADA, followUp = today.minusDays(5)), today))
        assertFalse(QuoteFunnelCalculator.isFollowUpDue(saved("d", QuoteStatus.BORRADOR, followUp = today.minusDays(5)), today))
        assertFalse(QuoteFunnelCalculator.isFollowUpDue(saved("e", QuoteStatus.ENVIADA, followUp = null), today))
    }

    @Test
    fun expiredOnlyWhenAwaitingAnswerAndValidityPassed() {
        val old = quote(validUntil = today.minusDays(1))
        assertTrue(QuoteFunnelCalculator.isExpired(saved("a", QuoteStatus.ENVIADA, old), today))
        assertFalse(QuoteFunnelCalculator.isExpired(saved("b", QuoteStatus.GANADA, old), today))
        assertFalse(QuoteFunnelCalculator.isExpired(saved("c", QuoteStatus.ENVIADA, quote()), today))
    }

    @Test
    fun nextQuoteNumberSkipsTakenNumbersOfTheSameDay() {
        assertEquals("COT-20261125-01", QuoteFunnelCalculator.nextQuoteNumber(today, emptyList()))
        assertEquals(
            "COT-20261125-03",
            QuoteFunnelCalculator.nextQuoteNumber(today, listOf("COT-20261125-01", "COT-20261125-02")),
        )
        assertEquals("COT-20261125-01", QuoteFunnelCalculator.nextQuoteNumber(today, listOf("COT-20261124-01")))
    }

    @Test
    fun markSentSetsDefaultFollowUpAndKeepsExistingOne() {
        val sent = QuoteFunnelCalculator.markSent(saved("a"), today, nowMillis = 5_000)
        assertEquals(QuoteStatus.ENVIADA, sent.status)
        assertEquals(today.plusDays(3), sent.followUpDate)
        assertEquals(5_000L, sent.sentAtMillis)

        val custom = QuoteFunnelCalculator.markSent(
            saved("b", QuoteStatus.SEGUIMIENTO, followUp = today.plusDays(10)), today, 6_000,
        )
        assertEquals(QuoteStatus.SEGUIMIENTO, custom.status)
        assertEquals(today.plusDays(10), custom.followUpDate)
    }

    @Test
    fun closedQuotesIgnoreSendAndFollowUpTransitions() {
        val won = saved("a", QuoteStatus.GANADA)
        assertEquals(won, QuoteFunnelCalculator.markSent(won, today, 1))
        assertEquals(won, QuoteFunnelCalculator.scheduleFollowUp(won, today.plusDays(2), 1))
    }

    @Test
    fun wonAndLostClearFollowUpAndLostKeepsReason() {
        val sent = saved("a", QuoteStatus.ENVIADA, followUp = today)
        val won = QuoteFunnelCalculator.markWon(sent, 9)
        assertEquals(QuoteStatus.GANADA, won.status)
        assertNull(won.followUpDate)

        val lost = QuoteFunnelCalculator.markLost(sent, "  Precio  ", 9)
        assertEquals(QuoteStatus.PERDIDA, lost.status)
        assertEquals("Precio", lost.lostReason)
        assertNull(lost.followUpDate)

        assertEquals(
            QuoteFunnelCalculator.CLOSED_QUOTE_DEFAULT_REASON,
            QuoteFunnelCalculator.markLost(sent, "   ", 9).lostReason,
        )
    }

    @Test
    fun reopenGoesBackToFollowUpWhenItWasSentOtherwiseDraft() {
        val lostAfterSend = saved("a", QuoteStatus.PERDIDA).copy(sentAtMillis = 10, lostReason = "Precio")
        val reopened = QuoteFunnelCalculator.reopen(lostAfterSend, today, 20)
        assertEquals(QuoteStatus.SEGUIMIENTO, reopened.status)
        assertEquals(today.plusDays(3), reopened.followUpDate)
        assertNull(reopened.lostReason)

        val neverSent = QuoteFunnelCalculator.reopen(saved("b", QuoteStatus.PERDIDA), today, 20)
        assertEquals(QuoteStatus.BORRADOR, neverSent.status)
        assertNull(neverSent.followUpDate)
    }

    @Test
    fun wonQuoteWithEventDateConvertsToActivityDraft() {
        val won = saved("a", QuoteStatus.GANADA, quote(title = "Boda - Ceremonia", price = "14000"))
        assertTrue(QuoteFunnelCalculator.canConvertToActivity(won))
        val draft = QuoteFunnelCalculator.toActivityDraft(won)
        assertNotNull(draft)
        draft!!
        assertEquals("Hotel Lopesan", draft.supplierName)
        assertEquals(LocalDate.of(2026, 12, 20), draft.eventDate)
        assertEquals(LocalTime.of(17, 0), draft.eventTime)
        assertEquals(0, BigDecimal(14000).compareTo(draft.amount))
        assertEquals(Currency.DOP, draft.currency)
        assertEquals(GigCategory.BODA, draft.category)
        assertFalse(draft.isPaid)
        assertTrue(draft.notes!!.contains("COT-20261125-01"))
    }

    @Test
    fun cannotConvertWithoutEventDateOrWhenNotWonOrAlreadyConverted() {
        val noDate = saved("a", QuoteStatus.GANADA, quote(eventDate = null))
        assertFalse(QuoteFunnelCalculator.canConvertToActivity(noDate))
        assertNull(QuoteFunnelCalculator.toActivityDraft(noDate))
        assertFalse(QuoteFunnelCalculator.canConvertToActivity(saved("b", QuoteStatus.ENVIADA)))
        assertFalse(
            QuoteFunnelCalculator.canConvertToActivity(saved("c", QuoteStatus.GANADA).copy(convertedToActivity = true)),
        )
    }

    @Test
    fun clientStatsGroupNamesIgnoringCaseAndAccentsAndRankBestClosers() {
        val quotes = listOf(
            saved("1", QuoteStatus.GANADA, quote(client = "Hotel Lopesan", price = "7000")),
            saved("2", QuoteStatus.GANADA, quote(client = "hotel lopesan ", price = "3000")),
            saved("3", QuoteStatus.PERDIDA, quote(client = "HOTEL LOPESAN")),
            saved("4", QuoteStatus.ENVIADA, quote(client = "Iberostar")),
            saved("5", QuoteStatus.PERDIDA, quote(client = "Iberostar")),
        )
        val stats = QuoteFunnelCalculator.statsByClient(quotes)
        assertEquals(2, stats.size)
        val lopesan = stats.first()
        assertEquals(3, lopesan.totalQuotes)
        assertEquals(2, lopesan.wonCount)
        assertEquals(67, lopesan.closeRatePercent)
        assertEquals(0, BigDecimal(10000).compareTo(lopesan.wonAmountByCurrency.getValue(Currency.DOP)))
        val ibero = stats.last()
        assertEquals(0, ibero.closeRatePercent)
        assertEquals(1, ibero.openCount)
    }
}
