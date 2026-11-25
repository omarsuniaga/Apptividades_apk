package com.example.data.local.room

import com.example.data.port.CachedAppSnapshot
import com.example.data.port.OfflineCachePort
import com.example.data.port.PendingSyncOpType
import com.example.data.port.PendingSyncOperation
import com.example.domain.model.Activity
import com.example.domain.model.ActivityType
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountType
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import com.example.domain.model.Supplier
import com.example.domain.model.Venue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room-backed local cache repository providing instant offline viewing and
 * an offline payment-status sync queue that replays automatically once connectivity returns.
 */
class OfflineCacheRepository(
    private val dao: OfflineCacheDao,
) : OfflineCachePort {

    override suspend fun loadSnapshot(ownerId: String): CachedAppSnapshot = withContext(Dispatchers.IO) {
        if (ownerId.isBlank()) return@withContext CachedAppSnapshot()
        val activities = dao.getActivitiesOnce(ownerId).mapNotNull { it.toDomain() }
        val suppliers = dao.getSuppliersOnce(ownerId).map { it.toDomain() }
        val venues = dao.getVenuesOnce(ownerId).map { it.toDomain() }
        val bankAccounts = dao.getBankAccountsOnce(ownerId).map { it.toDomain() }
        val lastSynced = dao.getLastSyncedAtOnce(ownerId)
        CachedAppSnapshot(
            activities = activities,
            suppliers = suppliers,
            venues = venues,
            bankAccounts = bankAccounts,
            lastSyncedAtMillis = lastSynced,
        )
    }

    override suspend fun saveSnapshot(
        ownerId: String,
        activities: List<Activity>,
        suppliers: List<Supplier>,
        venues: List<Venue>,
        bankAccounts: List<BankAccount>,
        syncedAtMillis: Long,
    ) = withContext(Dispatchers.IO) {
        if (ownerId.isBlank()) return@withContext
        dao.replaceOwnerSnapshot(
            ownerId = ownerId,
            activities = activities.map { it.toEntity(ownerId) },
            suppliers = suppliers.map { it.toEntity(ownerId) },
            venues = venues.map { it.toEntity(ownerId) },
            bankAccounts = bankAccounts.map { it.toEntity(ownerId) },
            syncedAtMillis = syncedAtMillis,
        )
    }

    override fun observePendingSyncCount(ownerId: String): Flow<Int> =
        dao.observePendingOpCount(ownerId)

    override suspend fun enqueuePaymentStatusSync(
        ownerId: String,
        activityIds: List<String>,
        markAsPaid: Boolean,
        paidAtIso: String?,
    ) = withContext(Dispatchers.IO) {
        val cleanIds = activityIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ownerId.isBlank() || cleanIds.isEmpty()) return@withContext
        val statusDb = if (markAsPaid) PaymentStatus.PAGADO.dbValue else PaymentStatus.PENDIENTE.dbValue
        dao.updateActivitiesPaymentStatus(
            ownerId = ownerId,
            ids = cleanIds,
            statusDb = statusDb,
            paidAt = if (markAsPaid) paidAtIso else null,
        )
        dao.insertPendingOp(
            PendingSyncOpEntity(
                ownerId = ownerId,
                opType = if (markAsPaid) PendingSyncOpType.MARK_PAID.code else PendingSyncOpType.MARK_PENDING.code,
                targetId = cleanIds.first(),
                payloadJson = cleanIds.joinToString(separator = ","),
                createdAtMillis = System.currentTimeMillis(),
            )
        )
        Unit
    }

    override suspend fun getPendingOperations(ownerId: String): List<PendingSyncOperation> =
        withContext(Dispatchers.IO) {
            if (ownerId.isBlank()) return@withContext emptyList()
            dao.getPendingOpsOnce(ownerId).mapNotNull { entity ->
                val opType = PendingSyncOpType.fromCode(entity.opType) ?: return@mapNotNull null
                val ids = entity.payloadJson
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (ids.isEmpty()) return@mapNotNull null
                PendingSyncOperation(
                    id = entity.id,
                    ownerId = entity.ownerId,
                    opType = opType,
                    targetId = entity.targetId,
                    activityIds = ids,
                    createdAtMillis = entity.createdAtMillis,
                )
            }
        }

    override suspend fun removePendingOperation(operationId: Long) = withContext(Dispatchers.IO) {
        dao.deletePendingOp(operationId)
    }

    override suspend fun clearForOwner(ownerId: String) = withContext(Dispatchers.IO) {
        if (ownerId.isBlank()) return@withContext
        dao.clearAllForOwner(ownerId)
    }

    private fun Activity.toEntity(owner: String) = CachedActivityEntity(
        id = id,
        ownerId = ownerId.ifBlank { owner },
        supplierId = supplierId,
        supplierName = supplierName,
        title = title,
        activityTypeDb = activityType.dbValue,
        eventDateIso = eventDate.toString(),
        eventTimeIso = eventTime?.toString(),
        venueId = venueId,
        venueName = venueName,
        venueSubLocation = venueSubLocation,
        venueAddress = venueAddress,
        city = city,
        notes = notes,
        description = description,
        amountDecimal = amount.stripTrailingZeros().toPlainString(),
        currencyCode = currency.code,
        paymentStatusDb = paymentStatus.dbValue,
        paidAt = paidAt,
        tagsJoined = tags.joinToString(separator = TAG_SEPARATOR),
        seriesId = seriesId,
        latitude = latitude,
        longitude = longitude,
        melodyflowId = melodyflowId,
    )

    private fun CachedActivityEntity.toDomain(): Activity? {
        val date = runCatching { LocalDate.parse(eventDateIso) }.getOrNull() ?: return null
        val time = eventTimeIso?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val amount = amountDecimal.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return Activity(
            id = id,
            ownerId = ownerId,
            supplierId = supplierId,
            supplierName = supplierName,
            title = title,
            activityType = ActivityType.fromDb(activityTypeDb),
            eventDate = date,
            eventTime = time,
            venueId = venueId,
            venueName = venueName,
            venueSubLocation = venueSubLocation,
            venueAddress = venueAddress,
            city = city,
            notes = notes,
            description = description,
            amount = amount,
            currency = Currency.fromCode(currencyCode),
            paymentStatus = PaymentStatus.fromDb(paymentStatusDb),
            paidAt = paidAt,
            tags = if (tagsJoined.isBlank()) emptyList() else tagsJoined.split(TAG_SEPARATOR),
            seriesId = seriesId,
            latitude = latitude,
            longitude = longitude,
            melodyflowId = melodyflowId,
        )
    }

    private fun Supplier.toEntity(owner: String) = CachedSupplierEntity(
        id = id,
        ownerId = ownerId.ifBlank { owner },
        name = name,
        phone = phone,
        email = email,
        notes = notes,
    )

    private fun CachedSupplierEntity.toDomain() = Supplier(
        id = id,
        ownerId = ownerId,
        name = name,
        phone = phone,
        email = email,
        notes = notes,
    )

    private fun Venue.toEntity(owner: String) = CachedVenueEntity(
        id = id,
        ownerId = ownerId.ifBlank { owner },
        name = name,
        placeId = placeId,
        address = address,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
    )

    private fun CachedVenueEntity.toDomain() = Venue(
        id = id,
        ownerId = ownerId,
        name = name,
        placeId = placeId,
        address = address,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
    )

    private fun BankAccount.toEntity(owner: String) = CachedBankAccountEntity(
        id = id,
        ownerId = ownerId.ifBlank { owner },
        label = label,
        bankName = bankName,
        holderName = holderName,
        accountNumber = accountNumber,
        accountTypeDb = accountType?.dbValue,
        currencyCode = currency.code,
        isDefault = isDefault,
    )

    private fun CachedBankAccountEntity.toDomain() = BankAccount(
        id = id,
        ownerId = ownerId,
        label = label,
        bankName = bankName,
        holderName = holderName,
        accountNumber = accountNumber,
        accountType = BankAccountType.fromDb(accountTypeDb),
        currency = Currency.fromCode(currencyCode),
        isDefault = isDefault,
    )

    companion object {
        private const val TAG_SEPARATOR = "\u001F"
    }
}
