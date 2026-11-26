package com.example.data

import com.example.data.port.CrmFollowUpPort
import com.example.data.port.CrmPort
import com.example.domain.rules.CrmAccount
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmProfile
import com.example.domain.rules.HotelCrmCalculator
import com.example.domain.rules.NameNormalizer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/**
 * Application logic of the CRM. Accounts derived from activities get a stored profile lazily, the first
 * time the user adds information to them; the "next contact" reminder is kept in sync on every change.
 * Free of Android classes so it can be unit tested with fake ports.
 */
class CrmManager(
    private val port: CrmPort,
    private val reminders: CrmFollowUpPort,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun updateDetails(
        account: CrmAccount,
        contactPerson: String,
        contactRole: String,
        phone: String,
        email: String,
        notes: String,
    ) {
        val base = existingOrNew(account)
        persist(
            base.copy(
                contactPerson = contactPerson.trim(),
                contactRole = contactRole.trim(),
                phone = phone.trim(),
                email = email.trim(),
                notes = notes.trim(),
                updatedAtMillis = clock(),
            ),
        )
    }

    /** Records a contact and sets (or clears, when [nextContactDate] is null) the next scheduled contact. */
    suspend fun logContact(
        account: CrmAccount,
        channel: CrmContactChannel,
        note: String,
        date: LocalDate,
        nextContactDate: LocalDate?,
    ) {
        val now = clock()
        val base = existingOrNew(account)
        port.addContact(
            CrmContact(
                id = idFactory(),
                accountKey = base.accountKey,
                date = date,
                channel = channel,
                note = note.trim(),
                createdAtMillis = now,
            ),
        )
        persist(base.copy(nextContactDate = nextContactDate, updatedAtMillis = now))
    }

    suspend fun setNextContact(account: CrmAccount, date: LocalDate?) {
        val base = existingOrNew(account)
        persist(base.copy(nextContactDate = date, updatedAtMillis = clock()))
    }

    /** Adds a hotel/client you want to win. Returns null for a blank name. Reuses an existing profile. */
    suspend fun addProspect(type: CrmAccountType, name: String): CrmProfile? {
        val clean = name.trim()
        val canonical = NameNormalizer.normalizeKey(clean)
        if (clean.isEmpty() || canonical.isEmpty()) return null
        val key = HotelCrmCalculator.accountKey(type, canonical)
        port.getProfile(key)?.let { return it }
        val now = clock()
        val profile = CrmProfile(
            accountKey = key,
            type = type,
            displayName = clean,
            isProspect = true,
            createdAtMillis = now,
        )
        persist(profile)
        return profile
    }

    suspend fun deleteContact(id: String) = port.deleteContact(id)

    /** Removes only the CRM data (profile, log, reminder); activities and quotes are untouched. */
    suspend fun deleteAccountData(accountKey: String) {
        reminders.cancel(accountKey)
        port.deleteProfile(accountKey)
    }

    suspend fun deleteAll() {
        port.observeProfiles().first().forEach { reminders.cancel(it.accountKey) }
        port.deleteAll()
    }

    private suspend fun existingOrNew(account: CrmAccount): CrmProfile {
        port.getProfile(account.accountKey)?.let { return it }
        account.profile?.let { return it }
        val now = clock()
        return CrmProfile(
            accountKey = account.accountKey,
            type = account.type,
            displayName = account.displayName,
            createdAtMillis = now,
        )
    }

    private suspend fun persist(profile: CrmProfile) {
        port.upsertProfile(profile)
        reminders.schedule(profile)
    }
}
