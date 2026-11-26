package com.example.data

import com.example.data.port.CrmFollowUpPort
import com.example.data.port.CrmPort
import com.example.domain.rules.CrmAccount
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmProfile
import com.example.domain.rules.CrmStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class FakeCrmPort : CrmPort {
    val profiles = MutableStateFlow<Map<String, CrmProfile>>(emptyMap())
    val contacts = MutableStateFlow<List<CrmContact>>(emptyList())
    override fun observeProfiles(): Flow<List<CrmProfile>> = profiles.map { it.values.toList() }
    override fun observeContacts(): Flow<List<CrmContact>> = contacts
    override suspend fun getProfile(accountKey: String) = profiles.value[accountKey]
    override suspend fun upsertProfile(profile: CrmProfile) { profiles.value = profiles.value + (profile.accountKey to profile) }
    override suspend fun addContact(contact: CrmContact) { contacts.value = contacts.value + contact }
    override suspend fun deleteContact(id: String) { contacts.value = contacts.value.filterNot { it.id == id } }
    override suspend fun deleteProfile(accountKey: String) {
        profiles.value = profiles.value - accountKey
        contacts.value = contacts.value.filterNot { it.accountKey == accountKey }
    }
    override suspend fun deleteAll() { profiles.value = emptyMap(); contacts.value = emptyList() }
}

private class FakeCrmReminders : CrmFollowUpPort {
    val scheduled = mutableListOf<CrmProfile>()
    val cancelled = mutableListOf<String>()
    override fun schedule(profile: CrmProfile) { scheduled += profile }
    override fun cancel(accountKey: String) { cancelled += accountKey }
}

class CrmManagerTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val port = FakeCrmPort()
    private val reminders = FakeCrmReminders()
    private var now = 1_000L
    private var ids = 0
    private val manager = CrmManager(port, reminders, clock = { now++ }, idFactory = { "c-${++ids}" })

    private fun derivedAccount(name: String = "Lopesan", type: CrmAccountType = CrmAccountType.HOTEL) = CrmAccount(
        accountKey = "${type.name}:${name.lowercase()}",
        type = type,
        displayName = name,
        profile = null,
        stage = CrmStage.DORMIDO,
        gigCount = 3,
        upcomingGigCount = 0,
        lastGigDate = today.minusDays(120),
        nextGigDate = null,
        daysSinceLastGig = 120,
        revenueLast12Months = emptyMap(),
        openQuotes = 0,
        wonQuotes = 0,
        lostQuotes = 0,
        lastContactDate = null,
        nextContactDate = null,
        contactReason = null,
        contactPerson = "",
        phone = null,
        email = null,
        contacts = emptyList(),
    )

    @Test
    fun loggingAContactCreatesTheProfileLazilyAndSchedulesTheNextOne() = runTest {
        val account = derivedAccount()
        manager.logContact(account, CrmContactChannel.WHATSAPP, "  Le escribí por la temporada ", today, today.plusDays(14))

        val profile = port.profiles.value.getValue(account.accountKey)
        assertEquals("Lopesan", profile.displayName)
        assertEquals(CrmAccountType.HOTEL, profile.type)
        assertEquals(today.plusDays(14), profile.nextContactDate)
        assertEquals(false, profile.isProspect)

        val contact = port.contacts.value.single()
        assertEquals("Le escribí por la temporada", contact.note)
        assertEquals(CrmContactChannel.WHATSAPP, contact.channel)
        assertEquals(account.accountKey, contact.accountKey)
        assertEquals(today.plusDays(14), reminders.scheduled.last().nextContactDate)
    }

    @Test
    fun loggingWithoutNextDateClearsTheReminder() = runTest {
        val account = derivedAccount()
        manager.setNextContact(account, today)
        manager.logContact(account, CrmContactChannel.LLAMADA, "", today, nextContactDate = null)

        assertNull(port.profiles.value.getValue(account.accountKey).nextContactDate)
        assertNull(reminders.scheduled.last().nextContactDate)
        assertEquals(2, port.profiles.value.size + port.contacts.value.size)
    }

    @Test
    fun detailsAreTrimmedAndKeptWithTheSameAccountKey() = runTest {
        val account = derivedAccount()
        manager.updateDetails(account, " María ", " Entretenimiento ", " 809-555-0000 ", " m@x.com ", " Prefiere WhatsApp ")
        val p = port.profiles.value.getValue(account.accountKey)
        assertEquals("María", p.contactPerson)
        assertEquals("Entretenimiento", p.contactRole)
        assertEquals("809-555-0000", p.phone)
        assertEquals("m@x.com", p.email)
        assertEquals("Prefiere WhatsApp", p.notes)

        manager.setNextContact(account.copy(profile = p), today.plusDays(7))
        val again = port.profiles.value.getValue(account.accountKey)
        assertEquals("María", again.contactPerson)
        assertEquals(today.plusDays(7), again.nextContactDate)
        assertEquals(1, port.profiles.value.size)
    }

    @Test
    fun addProspectCreatesOnceAndIgnoresBlankNames() = runTest {
        assertNull(manager.addProspect(CrmAccountType.HOTEL, "   "))
        val first = manager.addProspect(CrmAccountType.HOTEL, "Hotel Riu Palace")
        assertNotNull(first)
        assertTrue(first!!.isProspect)
        assertEquals("HOTEL:riupalace", first.accountKey)

        val second = manager.addProspect(CrmAccountType.HOTEL, "riu palace")
        assertEquals(first.accountKey, second!!.accountKey)
        assertEquals(1, port.profiles.value.size)
    }

    @Test
    fun deletingAccountDataRemovesProfileLogAndReminder() = runTest {
        val account = derivedAccount()
        manager.logContact(account, CrmContactChannel.VISITA, "Visita", today, today.plusDays(7))
        manager.deleteAccountData(account.accountKey)

        assertTrue(port.profiles.value.isEmpty())
        assertTrue(port.contacts.value.isEmpty())
        assertEquals(listOf(account.accountKey), reminders.cancelled)
    }

    @Test
    fun deletingOneContactKeepsTheRest() = runTest {
        val account = derivedAccount()
        manager.logContact(account, CrmContactChannel.CORREO, "a", today, null)
        manager.logContact(account, CrmContactChannel.OTRO, "b", today, null)
        manager.deleteContact("c-1")
        assertEquals(listOf("c-2"), port.contacts.value.map { it.id })
    }

    @Test
    fun deleteAllCancelsEveryReminder() = runTest {
        manager.logContact(derivedAccount("Lopesan"), CrmContactChannel.OTRO, "", today, today.plusDays(3))
        manager.logContact(derivedAccount("FJ", CrmAccountType.CLIENTE), CrmContactChannel.OTRO, "", today, today.plusDays(3))
        manager.deleteAll()
        assertTrue(port.profiles.value.isEmpty())
        assertEquals(2, reminders.cancelled.size)
    }
}
