package com.example.domain

import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.model.Supplier
import com.example.domain.rules.CrmAccount
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmContactReason
import com.example.domain.rules.CrmMessageBuilder
import com.example.domain.rules.CrmProfile
import com.example.domain.rules.CrmStage
import com.example.domain.rules.EventQuote
import com.example.domain.rules.HotelCrmCalculator
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

class HotelCrmCalculatorTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun act(
        id: String,
        client: String,
        venue: String,
        date: LocalDate,
        amount: Int = 7000,
        currency: Currency = Currency.DOP,
    ) = Activity(
        id = id,
        ownerId = "o",
        supplierName = client,
        title = client,
        eventDate = date,
        venueName = venue,
        amount = BigDecimal(amount),
        currency = currency,
    )

    private fun quote(id: String, client: String, venue: String, status: QuoteStatus) = SavedQuote(
        id = id,
        quote = EventQuote(
            quoteNumber = "COT-$id",
            clientName = client,
            eventTitle = "Boda",
            eventDate = null,
            eventTime = null,
            venueName = venue,
            issueDate = today,
            validUntilDate = today.plusDays(15),
            currency = Currency.DOP,
            items = listOf(QuoteLineItem("1", "Show", unitPrice = BigDecimal(5000))),
            issuerName = "Omar",
        ),
        status = status,
        createdAtMillis = 1,
    )

    private fun profile(
        key: String,
        type: CrmAccountType,
        name: String,
        next: LocalDate? = null,
        prospect: Boolean = false,
        updated: Long = 1,
    ) = CrmProfile(
        accountKey = key,
        type = type,
        displayName = name,
        nextContactDate = next,
        isProspect = prospect,
        createdAtMillis = 1,
        updatedAtMillis = updated,
    )

    private fun contact(id: String, key: String, date: LocalDate) = CrmContact(
        id = id,
        accountKey = key,
        date = date,
        channel = CrmContactChannel.WHATSAPP,
        createdAtMillis = 1,
    )

    private fun build(
        activities: List<Activity> = emptyList(),
        suppliers: List<Supplier> = emptyList(),
        quotes: List<SavedQuote> = emptyList(),
        profiles: List<CrmProfile> = emptyList(),
        contacts: List<CrmContact> = emptyList(),
    ) = HotelCrmCalculator.buildAccounts(activities, suppliers, quotes, profiles, contacts, today)

    private fun List<CrmAccount>.hotel(name: String) = first { it.type == CrmAccountType.HOTEL && it.displayName == name }
    private fun List<CrmAccount>.client(name: String) = first { it.type == CrmAccountType.CLIENTE && it.displayName == name }

    @Test
    fun stageDependsOnHowRecentlyYouWorkedTogether() {
        assertEquals(CrmStage.PROSPECTO, HotelCrmCalculator.stageOf(0, 0, null))
        assertEquals(CrmStage.ACTIVO, HotelCrmCalculator.stageOf(5, 0, 90))
        assertEquals(CrmStage.ACTIVO, HotelCrmCalculator.stageOf(5, 1, 400))
        assertEquals(CrmStage.DORMIDO, HotelCrmCalculator.stageOf(5, 0, 91))
        assertEquals(CrmStage.DORMIDO, HotelCrmCalculator.stageOf(5, 0, 365))
        assertEquals(CrmStage.INACTIVO, HotelCrmCalculator.stageOf(5, 0, 366))
    }

    @Test
    fun hotelVariantsAreGroupedAndStatsAreDerivedFromActivities() {
        val accounts = build(
            activities = listOf(
                act("1", "FJ", "Lopesan", today.minusDays(23)),
                act("2", "FJ", "Hotel Lopesan", today.minusDays(10)),
                act("3", "FJ", "Lopesan, Bávaro", today.plusDays(5)),
                act("4", "FJ", "Iberostar Bavaro", today.minusDays(400)),
            ),
        )
        val hotels = accounts.filter { it.type == CrmAccountType.HOTEL }
        assertEquals(2, hotels.size)

        val lopesan = hotels.first { it.displayName.contains("Lopesan") }
        assertEquals(3, lopesan.gigCount)
        assertEquals(1, lopesan.upcomingGigCount)
        assertEquals(today.minusDays(10), lopesan.lastGigDate)
        assertEquals(today.plusDays(5), lopesan.nextGigDate)
        assertEquals(10L, lopesan.daysSinceLastGig)
        assertEquals(CrmStage.ACTIVO, lopesan.stage)
        assertEquals(0, BigDecimal(14000).compareTo(lopesan.revenueLast12Months.getValue(Currency.DOP)))

        val ibero = hotels.first { it.displayName.contains("Iberostar") }
        assertEquals(CrmStage.INACTIVO, ibero.stage)
        assertTrue(ibero.revenueLast12Months.isEmpty())
    }

    @Test
    fun clientsAreGroupedBySupplierNameAndGetPhoneFromSupplierList() {
        val accounts = build(
            activities = listOf(
                act("1", "FJ", "Lopesan", today.minusDays(5)),
                act("2", "fj ", "Iberostar", today.minusDays(40)),
            ),
            suppliers = listOf(Supplier("s1", "o", "FJ", phone = "809-555-1234", email = "fj@mail.com")),
        )
        val fj = accounts.client("FJ")
        assertEquals(2, fj.gigCount)
        assertEquals("809-555-1234", fj.phone)
        assertEquals("fj@mail.com", fj.email)
    }

    @Test
    fun profilePhoneOverridesSupplierPhone() {
        val p = profile("CLIENTE:fj", CrmAccountType.CLIENTE, "FJ").copy(phone = "829-000-0000")
        val accounts = build(
            activities = listOf(act("1", "FJ", "Lopesan", today.minusDays(5))),
            suppliers = listOf(Supplier("s1", "o", "FJ", phone = "809-555-1234")),
            profiles = listOf(p),
        )
        assertEquals("829-000-0000", accounts.client("FJ").phone)
    }

    @Test
    fun supplierWithoutGigsIsAProspect() {
        val accounts = build(suppliers = listOf(Supplier("s1", "o", "Nuevo Cliente")))
        assertEquals(CrmStage.PROSPECTO, accounts.client("Nuevo Cliente").stage)
    }

    @Test
    fun quotesAreCountedPerHotelAndPerClient() {
        val accounts = build(
            quotes = listOf(
                quote("a", "FJ", "Lopesan", QuoteStatus.ENVIADA),
                quote("b", "FJ", "Hotel Lopesan", QuoteStatus.GANADA),
                quote("c", "Crisbel", "Lopesan", QuoteStatus.PERDIDA),
            ),
        )
        val lopesan = accounts.filter { it.type == CrmAccountType.HOTEL }.single()
        assertEquals(1, lopesan.openQuotes)
        assertEquals(1, lopesan.wonQuotes)
        assertEquals(1, lopesan.lostQuotes)
        val fj = accounts.client("FJ")
        assertEquals(1, fj.openQuotes)
        assertEquals(1, fj.wonQuotes)
    }

    @Test
    fun dormantAccountNeedsContactUnlessContactedRecently() {
        val dormantActs = listOf(act("1", "Crisbel", "Catalonia", today.minusDays(150)))
        val notContacted = build(activities = dormantActs).hotel("Catalonia")
        assertEquals(CrmStage.DORMIDO, notContacted.stage)
        assertEquals(CrmContactReason.DORMANT_NOT_CONTACTED, notContacted.contactReason)

        val p = profile("HOTEL:catalonia", CrmAccountType.HOTEL, "Catalonia")
        val recently = build(
            activities = dormantActs,
            profiles = listOf(p),
            contacts = listOf(contact("c1", p.accountKey, today.minusDays(10))),
        ).hotel("Catalonia")
        assertNull(recently.contactReason)
        assertEquals(today.minusDays(10), recently.lastContactDate)

        val longAgo = build(
            activities = dormantActs,
            profiles = listOf(p),
            contacts = listOf(contact("c1", p.accountKey, today.minusDays(45))),
        ).hotel("Catalonia")
        assertEquals(CrmContactReason.DORMANT_NOT_CONTACTED, longAgo.contactReason)
    }

    @Test
    fun scheduledContactDueTakesPriorityAndFutureOneSilencesAlerts() {
        val acts = listOf(act("1", "Crisbel", "Catalonia", today.minusDays(150)))
        val due = build(
            activities = acts,
            profiles = listOf(profile("HOTEL:catalonia", CrmAccountType.HOTEL, "Catalonia", next = today)),
        ).hotel("Catalonia")
        assertEquals(CrmContactReason.SCHEDULED_DUE, due.contactReason)

        val future = build(
            activities = acts,
            profiles = listOf(profile("HOTEL:catalonia", CrmAccountType.HOTEL, "Catalonia", next = today.plusDays(7))),
        ).hotel("Catalonia")
        assertNull(future.contactReason)
        assertEquals(today.plusDays(7), future.nextContactDate)
    }

    @Test
    fun activeAccountsDoNotNeedContactButManualProspectsDoUntilContacted() {
        val active = build(activities = listOf(act("1", "FJ", "Lopesan", today.minusDays(3)))).hotel("Lopesan")
        assertFalse(active.needsContact)

        val p = profile("HOTEL:riu", CrmAccountType.HOTEL, "Riu Palace", prospect = true)
        val prospect = build(profiles = listOf(p)).hotel("Riu Palace")
        assertEquals(CrmStage.PROSPECTO, prospect.stage)
        assertEquals(CrmContactReason.PROSPECT_NEVER_CONTACTED, prospect.contactReason)

        val contacted = build(
            profiles = listOf(p),
            contacts = listOf(contact("c1", p.accountKey, today.minusDays(2))),
        ).hotel("Riu Palace")
        assertNull(contacted.contactReason)
    }

    @Test
    fun contactsAreSortedNewestFirstAndAttachedToTheAccount() {
        val p = profile("HOTEL:lopesan", CrmAccountType.HOTEL, "Lopesan")
        val account = build(
            activities = listOf(act("1", "FJ", "Lopesan", today.minusDays(3))),
            profiles = listOf(p),
            contacts = listOf(
                contact("old", p.accountKey, today.minusDays(30)),
                contact("new", p.accountKey, today.minusDays(2)),
                contact("other", "HOTEL:otro", today),
            ),
        ).hotel("Lopesan")
        assertEquals(listOf("new", "old"), account.contacts.map { it.id })
    }

    @Test
    fun profileIsStillFoundWhenAHotelNameGetsMergedLater() {
        // Profile was created when only "Lopesan" existed; later "Lopesan, Bávaro" appears and becomes the canonical name.
        val p = profile("HOTEL:lopesan", CrmAccountType.HOTEL, "Lopesan", next = today)
        val account = build(
            activities = listOf(
                act("1", "FJ", "Lopesan", today.minusDays(100)),
                act("2", "FJ", "Lopesan, Bávaro", today.minusDays(120)),
            ),
            profiles = listOf(p),
        ).filter { it.type == CrmAccountType.HOTEL }.single()
        assertNotNull(account.profile)
        assertEquals("HOTEL:lopesan", account.accountKey)
        assertEquals(CrmContactReason.SCHEDULED_DUE, account.contactReason)
    }

    @Test
    fun accountsNeedingContactComeFirstAndSummaryCountsThem() {
        val accounts = build(
            activities = listOf(
                act("1", "FJ", "Lopesan", today.minusDays(3)),
                act("2", "Crisbel", "Catalonia", today.minusDays(150)),
            ),
        )
        val hotels = accounts.filter { it.type == CrmAccountType.HOTEL }
        assertEquals("Catalonia", hotels.first().displayName)
        val summary = HotelCrmCalculator.summarize(accounts)
        assertEquals(accounts.size, summary.totalCount)
        assertEquals(accounts.count { it.needsContact }, summary.toContactCount)
        assertEquals(1, accounts.count { it.type == CrmAccountType.HOTEL && it.stage == CrmStage.DORMIDO })
    }

    @Test
    fun messagesAreAdaptedToTheRelationshipStage() {
        val dormant = build(activities = listOf(act("1", "Crisbel", "Catalonia", today.minusDays(150)))).hotel("Catalonia")
        val dormantMsg = CrmMessageBuilder.build(dormant, "Omar")
        assertTrue(dormantMsg.contains("Hola Catalonia,"))
        assertTrue(dormantMsg.contains("Hace un tiempo que no coincidimos en Catalonia"))
        assertTrue(dormantMsg.contains("28 de junio de 2026"))

        val withContact = dormant.copy(contactPerson = "María")
        assertTrue(CrmMessageBuilder.build(withContact, "Omar").startsWith("Hola María,"))

        val prospect = build(
            profiles = listOf(profile("HOTEL:riu", CrmAccountType.HOTEL, "Riu Palace", prospect = true)),
        ).hotel("Riu Palace")
        val prospectMsg = CrmMessageBuilder.build(prospect, "Omar")
        assertTrue(prospectMsg.contains("Soy Omar, violinista"))
        assertTrue(prospectMsg.contains("portafolio y tarifas"))

        val active = build(activities = listOf(act("1", "FJ", "Lopesan", today.minusDays(3)))).hotel("Lopesan")
        assertTrue(CrmMessageBuilder.build(active, "Omar").contains("gracias por la confianza"))
    }
}
