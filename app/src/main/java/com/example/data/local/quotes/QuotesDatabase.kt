package com.example.data.local.quotes

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_quotes")
data class SavedQuoteEntity(
    @PrimaryKey val id: String,
    val quoteNumber: String,
    val clientName: String,
    val eventTitle: String,
    val eventDate: String?,
    val eventTime: String?,
    val venueName: String,
    val issueDate: String,
    val validUntilDate: String,
    val currencyCode: String,
    val depositPercent: Int,
    val itemsJson: String,
    val notes: String,
    val terms: String,
    val issuerName: String,
    val includeBankAccounts: Boolean,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sentAtMillis: Long?,
    val followUpDate: String?,
    val lostReason: String?,
    val convertedToActivity: Boolean,
)

@Dao
interface SavedQuoteDao {
    @Query("SELECT * FROM saved_quotes ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<SavedQuoteEntity>>

    @Query("SELECT * FROM saved_quotes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SavedQuoteEntity?

    @Upsert
    suspend fun upsert(entity: SavedQuoteEntity)

    @Query("DELETE FROM saved_quotes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_quotes")
    suspend fun deleteAll()
}

/**
 * Dedicated database for the quote funnel. It is intentionally separate from the offline cache
 * database (which uses destructive migrations): saved quotes are user-authored data and must
 * survive schema changes, so future versions must provide explicit migrations.
 */
@Database(
    entities = [SavedQuoteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class QuotesDatabase : RoomDatabase() {
    abstract fun savedQuoteDao(): SavedQuoteDao

    companion object {
        @Volatile
        private var INSTANCE: QuotesDatabase? = null

        fun getInstance(context: Context): QuotesDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuotesDatabase::class.java,
                    "apptividades_quotes.db",
                ).build().also { INSTANCE = it }
            }
    }
}
