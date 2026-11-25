package com.example.data.local.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_activities")
data class CachedActivityEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val supplierId: String?,
    val supplierName: String,
    val title: String,
    val activityTypeDb: String,
    val eventDateIso: String,
    val eventTimeIso: String?,
    val venueId: String?,
    val venueName: String,
    val venueSubLocation: String?,
    val venueAddress: String?,
    val city: String?,
    val notes: String?,
    val description: String?,
    val amountDecimal: String,
    val currencyCode: String,
    val paymentStatusDb: String,
    val paidAt: String?,
    val tagsJoined: String,
    val seriesId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val melodyflowId: String?,
)

@Entity(tableName = "cached_suppliers")
data class CachedSupplierEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val notes: String?,
)

@Entity(tableName = "cached_venues")
data class CachedVenueEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val name: String,
    val placeId: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val notes: String?,
)

@Entity(tableName = "cached_bank_accounts")
data class CachedBankAccountEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val label: String,
    val bankName: String,
    val holderName: String,
    val accountNumber: String,
    val accountTypeDb: String?,
    val currencyCode: String,
    val isDefault: Boolean,
)

@Entity(tableName = "cached_sync_meta")
data class CachedSyncMetaEntity(
    @PrimaryKey val ownerId: String,
    val lastSyncedAtMillis: Long,
)

@Entity(tableName = "pending_sync_ops")
data class PendingSyncOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val opType: String,
    val targetId: String,
    val payloadJson: String,
    val createdAtMillis: Long,
)

@Dao
interface OfflineCacheDao {

    @Query("SELECT * FROM cached_activities WHERE ownerId = :ownerId ORDER BY eventDateIso DESC")
    fun observeActivities(ownerId: String): Flow<List<CachedActivityEntity>>

    @Query("SELECT * FROM cached_suppliers WHERE ownerId = :ownerId ORDER BY name ASC")
    fun observeSuppliers(ownerId: String): Flow<List<CachedSupplierEntity>>

    @Query("SELECT * FROM cached_venues WHERE ownerId = :ownerId ORDER BY name ASC")
    fun observeVenues(ownerId: String): Flow<List<CachedVenueEntity>>

    @Query("SELECT * FROM cached_bank_accounts WHERE ownerId = :ownerId ORDER BY isDefault DESC, label ASC")
    fun observeBankAccounts(ownerId: String): Flow<List<CachedBankAccountEntity>>

    @Query("SELECT lastSyncedAtMillis FROM cached_sync_meta WHERE ownerId = :ownerId")
    fun observeLastSyncedAt(ownerId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM pending_sync_ops WHERE ownerId = :ownerId")
    fun observePendingOpCount(ownerId: String): Flow<Int>

    @Query("SELECT * FROM cached_activities WHERE ownerId = :ownerId")
    suspend fun getActivitiesOnce(ownerId: String): List<CachedActivityEntity>

    @Query("SELECT * FROM cached_suppliers WHERE ownerId = :ownerId")
    suspend fun getSuppliersOnce(ownerId: String): List<CachedSupplierEntity>

    @Query("SELECT * FROM cached_venues WHERE ownerId = :ownerId")
    suspend fun getVenuesOnce(ownerId: String): List<CachedVenueEntity>

    @Query("SELECT * FROM cached_bank_accounts WHERE ownerId = :ownerId")
    suspend fun getBankAccountsOnce(ownerId: String): List<CachedBankAccountEntity>

    @Query("SELECT lastSyncedAtMillis FROM cached_sync_meta WHERE ownerId = :ownerId")
    suspend fun getLastSyncedAtOnce(ownerId: String): Long?

    @Query("SELECT * FROM pending_sync_ops WHERE ownerId = :ownerId ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getPendingOpsOnce(ownerId: String): List<PendingSyncOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(items: List<CachedActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppliers(items: List<CachedSupplierEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVenues(items: List<CachedVenueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBankAccounts(items: List<CachedBankAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMeta(meta: CachedSyncMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingOp(op: PendingSyncOpEntity): Long

    @Query("UPDATE cached_activities SET paymentStatusDb = :statusDb, paidAt = :paidAt WHERE id IN (:ids) AND ownerId = :ownerId")
    suspend fun updateActivitiesPaymentStatus(
        ownerId: String,
        ids: List<String>,
        statusDb: String,
        paidAt: String?,
    )

    @Query("DELETE FROM pending_sync_ops WHERE id = :id")
    suspend fun deletePendingOp(id: Long)

    @Query("DELETE FROM cached_activities WHERE ownerId = :ownerId")
    suspend fun deleteActivitiesForOwner(ownerId: String)

    @Query("DELETE FROM cached_suppliers WHERE ownerId = :ownerId")
    suspend fun deleteSuppliersForOwner(ownerId: String)

    @Query("DELETE FROM cached_venues WHERE ownerId = :ownerId")
    suspend fun deleteVenuesForOwner(ownerId: String)

    @Query("DELETE FROM cached_bank_accounts WHERE ownerId = :ownerId")
    suspend fun deleteBankAccountsForOwner(ownerId: String)

    @Query("DELETE FROM cached_sync_meta WHERE ownerId = :ownerId")
    suspend fun deleteSyncMetaForOwner(ownerId: String)

    @Query("DELETE FROM pending_sync_ops WHERE ownerId = :ownerId")
    suspend fun deletePendingOpsForOwner(ownerId: String)

    @Transaction
    suspend fun replaceOwnerSnapshot(
        ownerId: String,
        activities: List<CachedActivityEntity>,
        suppliers: List<CachedSupplierEntity>,
        venues: List<CachedVenueEntity>,
        bankAccounts: List<CachedBankAccountEntity>,
        syncedAtMillis: Long,
    ) {
        deleteActivitiesForOwner(ownerId)
        deleteSuppliersForOwner(ownerId)
        deleteVenuesForOwner(ownerId)
        deleteBankAccountsForOwner(ownerId)
        if (activities.isNotEmpty()) upsertActivities(activities)
        if (suppliers.isNotEmpty()) upsertSuppliers(suppliers)
        if (venues.isNotEmpty()) upsertVenues(venues)
        if (bankAccounts.isNotEmpty()) upsertBankAccounts(bankAccounts)
        upsertSyncMeta(CachedSyncMetaEntity(ownerId = ownerId, lastSyncedAtMillis = syncedAtMillis))
    }

    @Transaction
    suspend fun clearAllForOwner(ownerId: String) {
        deleteActivitiesForOwner(ownerId)
        deleteSuppliersForOwner(ownerId)
        deleteVenuesForOwner(ownerId)
        deleteBankAccountsForOwner(ownerId)
        deleteSyncMetaForOwner(ownerId)
        deletePendingOpsForOwner(ownerId)
    }
}

@Database(
    entities = [
        CachedActivityEntity::class,
        CachedSupplierEntity::class,
        CachedVenueEntity::class,
        CachedBankAccountEntity::class,
        CachedSyncMetaEntity::class,
        PendingSyncOpEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ApptividadesCacheDatabase : RoomDatabase() {
    abstract fun offlineCacheDao(): OfflineCacheDao

    companion object {
        @Volatile
        private var INSTANCE: ApptividadesCacheDatabase? = null

        fun getInstance(context: Context): ApptividadesCacheDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApptividadesCacheDatabase::class.java,
                    "apptividades_offline_cache.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
