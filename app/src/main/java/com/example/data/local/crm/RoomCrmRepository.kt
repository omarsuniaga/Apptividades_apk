package com.example.data.local.crm

import com.example.data.port.CrmPort
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomCrmRepository(private val dao: CrmDao) : CrmPort {

    override fun observeProfiles(): Flow<List<CrmProfile>> =
        dao.observeProfiles().map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override fun observeContacts(): Flow<List<CrmContact>> =
        dao.observeContacts().map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override suspend fun getProfile(accountKey: String): CrmProfile? =
        dao.getProfile(accountKey)?.let { runCatching { it.toDomain() }.getOrNull() }

    override suspend fun upsertProfile(profile: CrmProfile) = dao.upsertProfile(profile.toEntity())

    override suspend fun addContact(contact: CrmContact) = dao.upsertContact(contact.toEntity())

    override suspend fun deleteContact(id: String) = dao.deleteContact(id)

    override suspend fun deleteProfile(accountKey: String) = dao.deleteProfileCascade(accountKey)

    override suspend fun deleteAll() = dao.deleteAll()

    private fun CrmProfile.toEntity() = CrmProfileEntity(
        accountKey = accountKey,
        type = type.name,
        displayName = displayName,
        contactPerson = contactPerson,
        contactRole = contactRole,
        phone = phone,
        email = email,
        notes = notes,
        nextContactDate = nextContactDate?.toString(),
        isProspect = isProspect,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun CrmProfileEntity.toDomain() = CrmProfile(
        accountKey = accountKey,
        type = CrmAccountType.entries.first { it.name == type },
        displayName = displayName,
        contactPerson = contactPerson,
        contactRole = contactRole,
        phone = phone,
        email = email,
        notes = notes,
        nextContactDate = nextContactDate?.let(LocalDate::parse),
        isProspect = isProspect,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun CrmContact.toEntity() = CrmContactEntity(
        id = id,
        accountKey = accountKey,
        date = date.toString(),
        channel = channel.name,
        note = note,
        createdAtMillis = createdAtMillis,
    )

    private fun CrmContactEntity.toDomain() = CrmContact(
        id = id,
        accountKey = accountKey,
        date = LocalDate.parse(date),
        channel = CrmContactChannel.fromName(channel),
        note = note,
        createdAtMillis = createdAtMillis,
    )
}
