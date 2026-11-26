package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.crm.CrmDatabase
import com.example.data.local.crm.RoomCrmRepository
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomCrmRepositoryTest {

    private lateinit var db: CrmDatabase
    private lateinit var repo: RoomCrmRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CrmDatabase::class.java).allowMainThreadQueries().build()
        repo = RoomCrmRepository(db.crmDao())
    }

    @After
    fun tearDown() = db.close()

    private fun profile(key: String) = CrmProfile(
        accountKey = key,
        type = CrmAccountType.HOTEL,
        displayName = "Lopesan Bávaro",
        contactPerson = "María",
        contactRole = "Entretenimiento",
        phone = "809-555-0000",
        email = "m@x.com",
        notes = "Prefiere WhatsApp",
        nextContactDate = LocalDate.of(2026, 12, 10),
        isProspect = true,
        createdAtMillis = 5,
        updatedAtMillis = 9,
    )

    private fun contact(id: String, key: String, date: LocalDate) = CrmContact(
        id = id,
        accountKey = key,
        date = date,
        channel = CrmContactChannel.VISITA,
        note = "nota $id",
        createdAtMillis = 1,
    )

    @Test
    fun profileRoundTripKeepsEveryField() = runBlocking {
        val original = profile("HOTEL:lopesan")
        repo.upsertProfile(original)
        assertEquals(original, repo.getProfile("HOTEL:lopesan"))
        assertEquals(listOf(original), repo.observeProfiles().first())
    }

    @Test
    fun nullNextContactStaysNull() = runBlocking {
        repo.upsertProfile(profile("HOTEL:a").copy(nextContactDate = null))
        assertNull(repo.getProfile("HOTEL:a")!!.nextContactDate)
    }

    @Test
    fun contactsAreReturnedNewestFirst() = runBlocking {
        repo.addContact(contact("old", "HOTEL:a", LocalDate.of(2026, 10, 1)))
        repo.addContact(contact("new", "HOTEL:a", LocalDate.of(2026, 11, 1)))
        val list = repo.observeContacts().first()
        assertEquals(listOf("new", "old"), list.map { it.id })
        assertEquals(CrmContactChannel.VISITA, list.first().channel)
    }

    @Test
    fun deleteProfileCascadesToItsContactsOnly() = runBlocking {
        repo.upsertProfile(profile("HOTEL:a"))
        repo.upsertProfile(profile("HOTEL:b"))
        repo.addContact(contact("a1", "HOTEL:a", LocalDate.of(2026, 11, 1)))
        repo.addContact(contact("b1", "HOTEL:b", LocalDate.of(2026, 11, 1)))

        repo.deleteProfile("HOTEL:a")

        assertNull(repo.getProfile("HOTEL:a"))
        assertEquals(listOf("b1"), repo.observeContacts().first().map { it.id })
    }

    @Test
    fun deleteAllClearsBothTables() = runBlocking {
        repo.upsertProfile(profile("HOTEL:a"))
        repo.addContact(contact("a1", "HOTEL:a", LocalDate.of(2026, 11, 1)))
        repo.deleteAll()
        assertEquals(0, repo.observeProfiles().first().size)
        assertEquals(0, repo.observeContacts().first().size)
    }
}
