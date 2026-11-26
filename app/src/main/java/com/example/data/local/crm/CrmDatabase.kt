package com.example.data.local.crm

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "crm_profiles")
data class CrmProfileEntity(
    @PrimaryKey val accountKey: String,
    val type: String,
    val displayName: String,
    val contactPerson: String,
    val contactRole: String,
    val phone: String,
    val email: String,
    val notes: String,
    val nextContactDate: String?,
    val isProspect: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "crm_contacts", indices = [Index("accountKey")])
data class CrmContactEntity(
    @PrimaryKey val id: String,
    val accountKey: String,
    val date: String,
    val channel: String,
    val note: String,
    val createdAtMillis: Long,
)

@Dao
interface CrmDao {
    @Query("SELECT * FROM crm_profiles")
    fun observeProfiles(): Flow<List<CrmProfileEntity>>

    @Query("SELECT * FROM crm_contacts ORDER BY date DESC, createdAtMillis DESC")
    fun observeContacts(): Flow<List<CrmContactEntity>>

    @Query("SELECT * FROM crm_profiles WHERE accountKey = :accountKey LIMIT 1")
    suspend fun getProfile(accountKey: String): CrmProfileEntity?

    @Upsert
    suspend fun upsertProfile(entity: CrmProfileEntity)

    @Upsert
    suspend fun upsertContact(entity: CrmContactEntity)

    @Query("DELETE FROM crm_contacts WHERE id = :id")
    suspend fun deleteContact(id: String)

    @Query("DELETE FROM crm_contacts WHERE accountKey = :accountKey")
    suspend fun deleteContactsFor(accountKey: String)

    @Query("DELETE FROM crm_profiles WHERE accountKey = :accountKey")
    suspend fun deleteProfileRow(accountKey: String)

    @Transaction
    suspend fun deleteProfileCascade(accountKey: String) {
        deleteContactsFor(accountKey)
        deleteProfileRow(accountKey)
    }

    @Query("DELETE FROM crm_contacts")
    suspend fun deleteAllContacts()

    @Query("DELETE FROM crm_profiles")
    suspend fun deleteAllProfiles()

    @Transaction
    suspend fun deleteAll() {
        deleteAllContacts()
        deleteAllProfiles()
    }
}

/**
 * Dedicated database for the hotel/client CRM. Like the quotes database it is separate from the
 * offline cache (destructive migrations): this is user-authored data, so future versions must
 * provide explicit migrations.
 */
@Database(
    entities = [CrmProfileEntity::class, CrmContactEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CrmDatabase : RoomDatabase() {
    abstract fun crmDao(): CrmDao

    companion object {
        @Volatile
        private var INSTANCE: CrmDatabase? = null

        fun getInstance(context: Context): CrmDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CrmDatabase::class.java,
                    "apptividades_crm.db",
                ).build().also { INSTANCE = it }
            }
    }
}
