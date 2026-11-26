package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.domain.rules.CrmAccount
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmContactReason
import com.example.domain.rules.CrmProfile
import com.example.domain.rules.CrmStage
import com.example.ui.screens.CrmScreen
import com.example.ui.theme.ApptividadesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrmScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 25)

    private fun account(
        name: String,
        type: CrmAccountType = CrmAccountType.HOTEL,
        stage: CrmStage = CrmStage.DORMIDO,
        reason: CrmContactReason? = null,
        lastGig: LocalDate? = today.minusDays(150),
        phone: String? = "809-555-1234",
        profile: CrmProfile? = null,
        contacts: List<CrmContact> = emptyList(),
    ) = CrmAccount(
        accountKey = "${type.name}:${name.lowercase()}",
        type = type,
        displayName = name,
        profile = profile,
        stage = stage,
        gigCount = if (lastGig == null) 0 else 4,
        upcomingGigCount = 0,
        lastGigDate = lastGig,
        nextGigDate = null,
        daysSinceLastGig = lastGig?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) },
        revenueLast12Months = emptyMap(),
        openQuotes = 0,
        wonQuotes = 0,
        lostQuotes = 0,
        lastContactDate = contacts.firstOrNull()?.date,
        nextContactDate = profile?.nextContactDate,
        contactReason = reason,
        contactPerson = profile?.contactPerson.orEmpty(),
        phone = phone,
        email = null,
        contacts = contacts,
    )

    private class Calls {
        var whatsapp: Pair<String, String>? = null
        var logged: List<Any?>? = null
        var details: List<String>? = null
        var next: Pair<String, LocalDate?>? = null
        var prospect: Pair<CrmAccountType, String>? = null
        var deletedContact: String? = null
        var deletedAccount: String? = null
    }

    private fun show(accounts: List<CrmAccount>): Calls {
        val calls = Calls()
        composeTestRule.setContent {
            ApptividadesTheme {
                CrmScreen(
                    accounts = accounts,
                    today = today,
                    issuerName = "Omar",
                    onBack = {},
                    onSendWhatsApp = { a, m -> calls.whatsapp = a.accountKey to m },
                    onLogContact = { a, c, n, d, nx -> calls.logged = listOf(a.accountKey, c, n, d, nx) },
                    onSaveDetails = { _, p, r, ph, e, n -> calls.details = listOf(p, r, ph, e, n) },
                    onSetNextContact = { a, d -> calls.next = a.accountKey to d },
                    onAddProspect = { t, n -> calls.prospect = t to n },
                    onDeleteContact = { calls.deletedContact = it.id },
                    onDeleteAccountData = { calls.deletedAccount = it.accountKey },
                )
            }
        }
        return calls
    }

    private fun scrollToAccount(key: String) {
        composeTestRule.onNodeWithTag("crm_list").performScrollToNode(hasTestTag("crm_account_$key"))
    }

    @Test
    fun `empty crm invites to add a prospect`() {
        val calls = show(emptyList())
        composeTestRule.onNodeWithTag("crm_tile_to_contact").assertTextContains("0")
        composeTestRule.onNodeWithTag("crm_add_prospect_button").performClick()
        composeTestRule.onNodeWithTag("crm_prospect_name").performTextInput("Hotel Riu Palace")
        composeTestRule.onNodeWithTag("crm_prospect_confirm").performClick()
        assertEquals(CrmAccountType.HOTEL to "Hotel Riu Palace", calls.prospect)
    }

    @Test
    fun `summary counts accounts to contact and shows the banner`() {
        show(
            listOf(
                account("Catalonia", reason = CrmContactReason.DORMANT_NOT_CONTACTED),
                account("Lopesan", stage = CrmStage.ACTIVO, lastGig = today.minusDays(3)),
            ),
        )
        composeTestRule.onNodeWithTag("crm_tile_to_contact").assertTextContains("1")
        composeTestRule.onNodeWithTag("crm_tile_active").assertTextContains("1")
        composeTestRule.onNodeWithTag("crm_tile_dormant").assertTextContains("1")
        composeTestRule.onNodeWithTag("crm_to_contact_banner").assertIsDisplayed()
    }

    @Test
    fun `switching to clients shows only client accounts`() {
        show(
            listOf(
                account("Catalonia"),
                account("FJ", type = CrmAccountType.CLIENTE, stage = CrmStage.ACTIVO, lastGig = today.minusDays(3)),
            ),
        )
        composeTestRule.onNodeWithTag("crm_account_HOTEL:catalonia").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crm_type_cliente").performClick()
        composeTestRule.onNodeWithTag("crm_account_CLIENTE:fj").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crm_account_HOTEL:catalonia").assertDoesNotExist()
    }

    @Test
    fun `to contact filter hides accounts that do not need a contact`() {
        show(
            listOf(
                account("Catalonia", reason = CrmContactReason.SCHEDULED_DUE),
                account("Lopesan", stage = CrmStage.ACTIVO, lastGig = today.minusDays(3)),
            ),
        )
        composeTestRule.onNodeWithTag("crm_filter_to_contact").performClick()
        scrollToAccount("HOTEL:catalonia")
        composeTestRule.onNodeWithTag("crm_account_HOTEL:catalonia").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crm_account_HOTEL:lopesan").assertDoesNotExist()
    }

    @Test
    fun `search filters by name`() {
        show(listOf(account("Catalonia"), account("Lopesan")))
        composeTestRule.onNodeWithTag("crm_search").performTextInput("lope")
        composeTestRule.onNodeWithTag("crm_account_HOTEL:lopesan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crm_account_HOTEL:catalonia").assertDoesNotExist()
    }

    @Test
    fun `whatsapp button sends a reconnection message for a dormant hotel`() {
        val calls = show(listOf(account("Catalonia", reason = CrmContactReason.DORMANT_NOT_CONTACTED)))
        scrollToAccount("HOTEL:catalonia")
        composeTestRule.onNodeWithTag("crm_whatsapp_HOTEL:catalonia").performScrollTo().performClick()
        val (key, message) = calls.whatsapp!!
        assertEquals("HOTEL:catalonia", key)
        assertTrue(message.startsWith("Hola Catalonia,"))
        assertTrue(message.contains("Hace un tiempo que no coincidimos en Catalonia"))
    }

    @Test
    fun `logging a contact sends channel note and a default follow up in 14 days`() {
        val calls = show(listOf(account("Catalonia")))
        composeTestRule.onNodeWithTag("crm_log_HOTEL:catalonia").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("crm_log_channel_llamada").performClick()
        composeTestRule.onNodeWithTag("crm_log_note").performTextInput("Hablé con María")
        composeTestRule.onNodeWithTag("crm_log_confirm").performClick()

        val logged = calls.logged!!
        assertEquals("HOTEL:catalonia", logged[0])
        assertEquals(CrmContactChannel.LLAMADA, logged[1])
        assertEquals("Hablé con María", logged[2])
        assertEquals(today, logged[3])
        assertEquals(today.plusDays(14), logged[4])
    }

    @Test
    fun `logging a contact without reminder passes a null next date`() {
        val calls = show(listOf(account("Catalonia")))
        composeTestRule.onNodeWithTag("crm_log_HOTEL:catalonia").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("crm_log_next_none").performClick()
        composeTestRule.onNodeWithTag("crm_log_confirm").performClick()
        assertNull(calls.logged!![4])
    }

    @Test
    fun `detail dialog saves contact data and schedules the next contact`() {
        val calls = show(listOf(account("Catalonia")))
        composeTestRule.onNodeWithTag("crm_details_HOTEL:catalonia").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("crm_detail_dialog").assertIsDisplayed()

        composeTestRule.onNodeWithTag("crm_detail_person").performTextInput("María")
        composeTestRule.onNodeWithTag("crm_detail_role").performTextInput("Entretenimiento")
        composeTestRule.onNodeWithTag("crm_detail_save").performScrollTo().performClick()
        val saved = calls.details!!
        assertEquals("María", saved[0])
        assertEquals("Entretenimiento", saved[1])
        assertEquals("809-555-1234", saved[2])

        composeTestRule.onNodeWithTag("crm_next_7d").performScrollTo().performClick()
        assertEquals("HOTEL:catalonia" to today.plusDays(7), calls.next)
    }

    @Test
    fun `detail dialog lists contact history and can delete an entry`() {
        val contact = CrmContact("k1", "HOTEL:catalonia", today.minusDays(5), CrmContactChannel.VISITA, "Visité el lobby", 1)
        val calls = show(listOf(account("Catalonia", contacts = listOf(contact))))
        composeTestRule.onNodeWithTag("crm_details_HOTEL:catalonia").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("crm_contact_k1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("crm_contact_delete_k1").performScrollTo().performClick()
        assertEquals("k1", calls.deletedContact)
    }

    @Test
    fun `deleting crm data asks for confirmation and only exists when there is a profile`() {
        val profile = CrmProfile("HOTEL:catalonia", CrmAccountType.HOTEL, "Catalonia", createdAtMillis = 1)
        val calls = show(listOf(account("Catalonia", profile = profile)))
        composeTestRule.onNodeWithTag("crm_details_HOTEL:catalonia").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("crm_delete_account_data").performScrollTo().performClick()
        assertNull(calls.deletedAccount)
        composeTestRule.onNodeWithTag("crm_delete_account_confirm").performClick()
        assertEquals("HOTEL:catalonia", calls.deletedAccount)
    }

    @Test
    fun `account without stored profile has nothing to delete`() {
        show(listOf(account("Catalonia", profile = null)))
        composeTestRule.onNodeWithTag("crm_details_HOTEL:catalonia").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("crm_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("crm_delete_account_data").assertDoesNotExist()
        assertNotNull(composeTestRule)
    }
}
