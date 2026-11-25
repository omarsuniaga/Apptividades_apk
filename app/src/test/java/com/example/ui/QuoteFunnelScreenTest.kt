package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.Currency
import com.example.domain.rules.EventQuote
import com.example.domain.rules.QuoteLineItem
import com.example.domain.rules.QuoteStatus
import com.example.domain.rules.SavedQuote
import com.example.ui.screens.QuoteFunnelScreen
import com.example.ui.theme.ApptividadesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuoteFunnelScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)

    private fun saved(
        id: String,
        status: QuoteStatus,
        client: String = "Hotel Lopesan",
        price: String = "7000",
        followUp: LocalDate? = null,
        eventDate: LocalDate? = LocalDate.of(2026, 12, 20),
    ) = SavedQuote(
        id = id,
        quote = EventQuote(
            quoteNumber = "COT-$id",
            clientName = client,
            eventTitle = "Boda",
            eventDate = eventDate,
            eventTime = null,
            venueName = "Lopesan",
            issueDate = today,
            validUntilDate = today.plusDays(15),
            currency = Currency.DOP,
            items = listOf(QuoteLineItem("1", "Show", unitPrice = BigDecimal(price))),
            issuerName = "Omar",
        ),
        status = status,
        createdAtMillis = 1,
        updatedAtMillis = 1,
        followUpDate = followUp,
    )

    private class Calls {
        var sent: String? = null
        var won: String? = null
        var lost: Pair<String, String>? = null
        var followUp: Pair<String, LocalDate>? = null
        var reopened: String? = null
        var activity: String? = null
        var deleted: String? = null
        var newQuote = false
    }

    private fun show(quotes: List<SavedQuote>, calls: Calls = Calls()): Calls {
        composeTestRule.setContent {
            ApptividadesTheme {
                QuoteFunnelScreen(
                    quotes = quotes,
                    today = today,
                    onBack = {},
                    onNewQuote = { calls.newQuote = true },
                    onSendQuote = { calls.sent = it.id },
                    onSetFollowUp = { q, d -> calls.followUp = q.id to d },
                    onMarkWon = { calls.won = it.id },
                    onMarkLost = { q, r -> calls.lost = q.id to r },
                    onReopen = { calls.reopened = it.id },
                    onCreateActivity = { calls.activity = it.id },
                    onDelete = { calls.deleted = it.id },
                )
            }
        }
        return calls
    }

    @Test
    fun `empty funnel invites to create the first quote`() {
        val calls = show(emptyList())
        composeTestRule.onNodeWithTag("funnel_close_rate").assertTextContains("—")
        composeTestRule.onNodeWithTag("funnel_new_quote_button").performClick()
        assertEquals(true, calls.newQuote)
    }

    @Test
    fun `summary shows close rate and banner for due follow ups`() {
        show(
            listOf(
                saved("a", QuoteStatus.GANADA),
                saved("b", QuoteStatus.GANADA),
                saved("c", QuoteStatus.PERDIDA),
                saved("d", QuoteStatus.ENVIADA, followUp = today),
            ),
        )
        composeTestRule.onNodeWithTag("funnel_close_rate").assertTextContains("67%")
        composeTestRule.onNodeWithTag("funnel_followups_due_banner").assertIsDisplayed()
    }

    @Test
    fun `draft can be sent to the client`() {
        val calls = show(listOf(saved("draft", QuoteStatus.BORRADOR)))
        composeTestRule.onNodeWithTag("funnel_send_draft").performScrollTo().performClick()
        assertEquals("draft", calls.sent)
    }

    @Test
    fun `sent quote can be marked as won`() {
        val calls = show(listOf(saved("sent", QuoteStatus.ENVIADA, followUp = today.plusDays(3))))
        composeTestRule.onNodeWithTag("funnel_won_sent").performScrollTo().performClick()
        assertEquals("sent", calls.won)
    }

    @Test
    fun `marking lost asks for a reason`() {
        val calls = show(listOf(saved("sent", QuoteStatus.ENVIADA, followUp = today.plusDays(3))))
        composeTestRule.onNodeWithTag("funnel_lost_sent").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("funnel_lost_custom").performTextInput("Eligió a otro violinista")
        composeTestRule.onNodeWithTag("funnel_lost_confirm").performClick()
        assertEquals("sent" to "Eligió a otro violinista", calls.lost)
    }

    @Test
    fun `quick follow up option schedules tomorrow`() {
        val calls = show(listOf(saved("sent", QuoteStatus.ENVIADA, followUp = today.plusDays(3))))
        composeTestRule.onNodeWithTag("funnel_followup_sent").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("funnel_followup_tomorrow").performClick()
        assertEquals("sent" to today.plusDays(1), calls.followUp)
    }

    @Test
    fun `won quote with event date can become an activity`() {
        val calls = show(listOf(saved("withDate", QuoteStatus.GANADA)))
        composeTestRule.onNodeWithTag("funnel_activity_withDate").performScrollTo().performClick()
        assertEquals("withDate", calls.activity)
    }

    @Test
    fun `won quote without event date cannot become an activity`() {
        show(listOf(saved("noDate", QuoteStatus.GANADA, eventDate = null)))
        composeTestRule.onNodeWithTag("funnel_quote_noDate").assertIsDisplayed()
        composeTestRule.onNodeWithTag("funnel_activity_noDate").assertDoesNotExist()
    }

    @Test
    fun `lost quote can be reopened and delete needs confirmation`() {
        val calls = show(listOf(saved("lost", QuoteStatus.PERDIDA)))
        composeTestRule.onNodeWithTag("funnel_reopen_lost").performScrollTo().performClick()
        assertEquals("lost", calls.reopened)

        composeTestRule.onNodeWithTag("funnel_delete_lost").performScrollTo().performClick()
        assertNull(calls.deleted)
        composeTestRule.onNodeWithTag("funnel_delete_confirm").performClick()
        assertEquals("lost", calls.deleted)
    }

    @Test
    fun `status filter narrows the list`() {
        show(
            listOf(
                saved("a", QuoteStatus.GANADA, client = "Iberostar"),
                saved("b", QuoteStatus.PERDIDA, client = "Catalonia"),
            ),
        )
        composeTestRule.onNodeWithTag("funnel_filter_perdida").performClick()
        composeTestRule.onNodeWithTag("funnel_quote_b").assertIsDisplayed()
        composeTestRule.onNodeWithTag("funnel_quote_a").assertDoesNotExist()
    }
}
