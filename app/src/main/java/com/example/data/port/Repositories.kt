package com.example.data.port

import com.example.domain.model.Activity
import com.example.domain.model.ActivityDraft
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.AiChatMessage
import com.example.domain.model.ArtistBrandProfile
import com.example.domain.model.AuthSession
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountDraft
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.Currency
import com.example.domain.model.GroqAgendaFinanceAnalysisRequest
import com.example.domain.model.GroqAiSettings
import com.example.domain.model.GroqCompletionResult
import com.example.domain.model.GroqModelOption
import com.example.domain.model.LabSettings
import com.example.domain.model.LoginSessionEvent
import com.example.domain.model.ScheduledDeparture
import com.example.domain.model.SignUpOutcome
import com.example.domain.model.Supplier
import com.example.domain.model.Venue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.time.LocalDate

data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)

data class ResolvedPlace(
    val placeId: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class RouteEstimate(
    val travelMinutes: Int,
    val distanceKm: Double? = null,
    val fromOsrm: Boolean = false,
)

interface AuthRepository {
    val sessionFlow: StateFlow<AuthSession?>
    val isInitialized: StateFlow<Boolean>
    val sessionExpiredMessageFlow: StateFlow<String?>

    fun isConfigured(): Boolean
    fun clearSessionExpiredMessage() {}
    suspend fun restoreSession()
    suspend fun signIn(email: String, password: String): Result<AuthSession>
    suspend fun signUp(email: String, password: String, fullName: String? = null): Result<SignUpOutcome>
    suspend fun resendConfirmation(email: String): Result<Unit>
    fun remainingResendCooldownSeconds(): Int = 0
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun getValidAccessToken(): Result<String>
    suspend fun forceRefreshSession(failedAccessToken: String? = null): Result<AuthSession>
}

interface ActivityRepository {
    suspend fun fetchAllActivities(ownerId: String): Result<List<Activity>>
    suspend fun createActivity(ownerId: String, draft: ActivityDraft): Result<Activity>
    suspend fun createWeeklySeries(ownerId: String, draft: ActivityDraft, repeatWeeks: Int): Result<List<Activity>>
    suspend fun updateActivity(ownerId: String, activityId: String, draft: ActivityDraft): Result<Activity>
    suspend fun updateSeries(ownerId: String, seriesId: String, draft: ActivityDraft): Result<List<Activity>>
    suspend fun markActivitiesPaid(ownerId: String, activityIds: List<String>): Result<Unit>
    suspend fun togglePaymentStatus(ownerId: String, activity: Activity): Result<Activity>
    suspend fun deleteActivity(ownerId: String, activityId: String): Result<Unit>
    suspend fun deleteSeries(ownerId: String, seriesId: String): Result<Unit>
}

interface SupplierRepository {
    suspend fun fetchSuppliers(ownerId: String): Result<List<Supplier>>
    suspend fun createSupplier(ownerId: String, name: String, phone: String?, email: String?, notes: String?): Result<Supplier>
    suspend fun updateSupplier(ownerId: String, supplier: Supplier): Result<Supplier>
    suspend fun deleteSupplier(ownerId: String, supplierId: String): Result<Unit>
}

interface VenueRepository {
    suspend fun fetchVenues(ownerId: String): Result<List<Venue>>
    suspend fun upsertVenue(ownerId: String, venue: Venue): Result<Venue>
}

interface SettingsRepository {
    suspend fun fetchSettings(ownerId: String): Result<LabSettings>
    suspend fun saveSettings(ownerId: String, settings: LabSettings): Result<LabSettings>
    fun observeLocalMonthlyGoal(): Flow<BigDecimal>
    suspend fun saveLocalMonthlyGoal(goal: BigDecimal)
}

interface MapsAndLocationPort {
    suspend fun searchPlaces(query: String): Result<List<PlacePrediction>>
    suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace>
    suspend fun getLastKnownOrCurrentLocation(): GeoPoint?
    suspend fun computeRouteEstimate(origin: GeoPoint, destination: GeoPoint): Result<RouteEstimate>
    fun observeCachedPlaces(): Flow<List<Venue>> = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun rememberSelectedPlace(
        name: String,
        address: String?,
        latitude: Double,
        longitude: Double,
        placeId: String? = null,
    ) {}
}

interface DepartureAlarmPort {
    fun observeScheduledDepartures(): Flow<Map<String, ScheduledDeparture>>
    fun observeActivityRoutes(): Flow<Map<String, CachedActivityRoute>>
    fun observeActivityTimings(): Flow<Map<String, ActivityTimelineOverride>> =
        kotlinx.coroutines.flow.flowOf(emptyMap())
    fun observeContingencyUnderControlIds(): Flow<Set<String>> =
        kotlinx.coroutines.flow.flowOf(emptySet())
    fun canScheduleExactAlarms(): Boolean
    suspend fun saveCachedRouteForActivity(activityId: String, route: CachedActivityRoute)
    suspend fun saveActivityTimingOverride(activityId: String, override: ActivityTimelineOverride?) {}
    suspend fun setContingencyUnderControl(activityId: String, underControl: Boolean) {}
    suspend fun syncUpcomingActivities(
        activities: List<Activity>,
        settings: LabSettings,
        overdueSummaryText: String?,
        forceRecalculateRoutes: Boolean = false,
    )
    suspend fun cancelForActivity(activityId: String)
    suspend fun cancelAllScheduledAlarms() {}
    suspend fun rescheduleAllFromStorage()
}

interface BankAccountRepository {
    suspend fun fetchBankAccounts(ownerId: String): Result<List<BankAccount>>
    suspend fun createBankAccount(ownerId: String, draft: BankAccountDraft): Result<BankAccount>
    suspend fun updateBankAccount(ownerId: String, accountId: String, draft: BankAccountDraft): Result<BankAccount>
    suspend fun setDefaultBankAccount(ownerId: String, accountId: String, currency: Currency): Result<Unit>
    suspend fun deleteBankAccount(ownerId: String, accountId: String): Result<Unit>
}

interface LoginHistoryRepository {
    fun observeLoginHistory(): Flow<List<LoginSessionEvent>>
    suspend fun recordEvent(
        userId: String,
        email: String,
        eventType: String,
        success: Boolean,
    )
    suspend fun clearHistory()
}

enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(raw: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue.equals(raw?.trim(), ignoreCase = true) } ?: SYSTEM
    }
}

data class PrivacyPreferences(
    val screenPrivacyEnabled: Boolean = false,
    val resolveExternalIpInHistory: Boolean = false,
    val appLockEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultUsdToDopRate: BigDecimal = BigDecimal("60.00"),
    val convertMixedCurrencies: Boolean = true,
)

interface PrivacyPreferencesPort {
    fun observePrivacyPreferences(): Flow<PrivacyPreferences>
    suspend fun setScreenPrivacyEnabled(enabled: Boolean)
    suspend fun setResolveExternalIpInHistory(enabled: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDefaultUsdToDopRate(rate: BigDecimal) {}
    suspend fun setConvertMixedCurrencies(enabled: Boolean) {}
}

interface ActivityFinancePort {
    fun observeFinancialRecords(): Flow<Map<String, ActivityFinancialRecord>>
    suspend fun saveFinancialRecord(record: ActivityFinancialRecord)
    suspend fun deleteFinancialRecord(activityId: String)
    suspend fun clearAll() {}
}

enum class PendingSyncOpType(val code: String) {
    MARK_PAID("mark_paid"),
    MARK_PENDING("mark_pending");

    companion object {
        fun fromCode(raw: String): PendingSyncOpType? =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) }
    }
}

data class PendingSyncOperation(
    val id: Long,
    val ownerId: String,
    val opType: PendingSyncOpType,
    val targetId: String,
    val activityIds: List<String>,
    val createdAtMillis: Long,
)

data class CachedAppSnapshot(
    val activities: List<Activity> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val venues: List<Venue> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val lastSyncedAtMillis: Long? = null,
) {
    val hasData: Boolean
        get() = lastSyncedAtMillis != null || activities.isNotEmpty() || suppliers.isNotEmpty()
}

interface OfflineCachePort {
    suspend fun loadSnapshot(ownerId: String): CachedAppSnapshot
    suspend fun saveSnapshot(
        ownerId: String,
        activities: List<Activity>,
        suppliers: List<Supplier>,
        venues: List<Venue>,
        bankAccounts: List<BankAccount>,
        syncedAtMillis: Long = System.currentTimeMillis(),
    )
    fun observePendingSyncCount(ownerId: String): Flow<Int>
    suspend fun enqueuePaymentStatusSync(
        ownerId: String,
        activityIds: List<String>,
        markAsPaid: Boolean,
        paidAtIso: String?,
    )
    suspend fun getPendingOperations(ownerId: String): List<PendingSyncOperation>
    suspend fun removePendingOperation(operationId: Long)
    suspend fun clearForOwner(ownerId: String)
}

interface ConnectivityPort {
    val isOnline: StateFlow<Boolean>
    fun checkCurrentlyOnline(): Boolean
}

interface BrandProfilePort {
    fun observeBrandProfile(): Flow<ArtistBrandProfile>
    suspend fun saveBrandProfile(profile: ArtistBrandProfile)
    suspend fun clearBrandProfile()
}

interface AccountDeletionPort {
    suspend fun deleteAllUserCloudData(ownerId: String): Result<Unit>
}

interface WidgetSyncPort {
    fun updateNextGigWidget(
        activities: List<Activity>,
        departures: Map<String, ScheduledDeparture>,
        today: LocalDate,
        venueDisplay: (String) -> String,
        supplierDisplay: (String) -> String,
    )
    fun clearNextGigWidget()
}

interface GroqAiPort {
    fun observeAiSettings(): Flow<GroqAiSettings>
    suspend fun saveApiKey(rawKey: String): Result<Unit>
    suspend fun clearApiKey()
    suspend fun setSelectedModel(model: GroqModelOption)
    suspend fun setAnonymizeClientNames(enabled: Boolean)
    suspend fun testConnection(overrideKey: String? = null): Result<String>
    suspend fun analyzeAgendaAndFinancialHistory(
        request: GroqAgendaFinanceAnalysisRequest,
        conversation: List<AiChatMessage> = emptyList(),
        userPrompt: String,
    ): Result<GroqCompletionResult>
    suspend fun analyze(
        systemPrompt: String,
        contextData: String,
        conversation: List<AiChatMessage>,
        userPrompt: String,
    ): Result<GroqCompletionResult>
}


