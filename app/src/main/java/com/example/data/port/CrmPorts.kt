package com.example.data.port

import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmProfile
import kotlinx.coroutines.flow.Flow

/** Local-only persistence of CRM profiles and contact logs. */
interface CrmPort {
    fun observeProfiles(): Flow<List<CrmProfile>>
    fun observeContacts(): Flow<List<CrmContact>>
    suspend fun getProfile(accountKey: String): CrmProfile?
    suspend fun upsertProfile(profile: CrmProfile)
    suspend fun addContact(contact: CrmContact)
    suspend fun deleteContact(id: String)

    /** Removes the profile together with its whole contact log. */
    suspend fun deleteProfile(accountKey: String)
    suspend fun deleteAll()
}

/** Schedules / cancels the "time to contact this account" reminder. */
interface CrmFollowUpPort {
    fun schedule(profile: CrmProfile)
    fun cancel(accountKey: String)
}
