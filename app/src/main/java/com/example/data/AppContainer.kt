package com.example.data

import android.content.Context
import com.example.alarms.DepartureAlarmScheduler
import com.example.data.local.ActivityFinanceStore
import com.example.data.local.LocalPhotonPlaceCache
import com.example.data.local.LoginHistoryStore
import com.example.data.local.SessionDataStore
import com.example.data.local.room.ApptividadesCacheDatabase
import com.example.data.local.room.OfflineCacheRepository
import com.example.data.port.AccountDeletionPort
import com.example.data.port.ActivityFinancePort
import com.example.data.port.ActivityRepository
import com.example.data.port.AuthRepository
import com.example.data.port.BankAccountRepository
import com.example.data.port.BrandProfilePort
import com.example.data.port.ConnectivityPort
import com.example.data.port.DepartureAlarmPort
import com.example.data.port.GroqAiPort
import com.example.data.port.LoginHistoryRepository
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.OfflineCachePort
import com.example.data.port.PrivacyPreferencesPort
import com.example.data.port.SettingsRepository
import com.example.data.port.SupplierRepository
import com.example.data.port.VenueRepository
import com.example.data.port.WidgetSyncPort
import com.example.data.remote.GroqApiService
import com.example.data.remote.OkHttpGroqApiService
import com.example.data.remote.OpenMapsApiClient
import com.example.data.repository.GroqRepository
import com.example.data.remote.SupabaseAccountDeletionRepository
import com.example.data.remote.SupabaseActivityRepository
import com.example.data.remote.SupabaseAuthRepository
import com.example.data.remote.SupabaseBankAccountRepository
import com.example.data.remote.SupabaseHttpClient
import com.example.data.remote.SupabaseSettingsRepository
import com.example.data.remote.SupabaseSupplierRepository
import com.example.data.remote.SupabaseVenueRepository
import com.example.domain.model.Activity
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.widget.NextGigWidgetStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Single composition root building HTTP/Supabase clients, Maps/Location, Alarms, and repository adapters.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val sessionDataStore = SessionDataStore(appContext)
    private val supabaseHttpClient = SupabaseHttpClient(okHttpClient, moshi)

    val authRepository: AuthRepository = SupabaseAuthRepository(
        httpClient = supabaseHttpClient,
        sessionDataStore = sessionDataStore,
    )

    val activityRepository: ActivityRepository = SupabaseActivityRepository(
        httpClient = supabaseHttpClient,
        authRepository = authRepository,
    )

    val supplierRepository: SupplierRepository = SupabaseSupplierRepository(
        httpClient = supabaseHttpClient,
        authRepository = authRepository,
    )

    val venueRepository: VenueRepository = SupabaseVenueRepository(
        httpClient = supabaseHttpClient,
        authRepository = authRepository,
    )

    val settingsRepository: SettingsRepository = SupabaseSettingsRepository(
        httpClient = supabaseHttpClient,
        authRepository = authRepository,
        sessionDataStore = sessionDataStore,
    )

    val bankAccountRepository: BankAccountRepository = SupabaseBankAccountRepository(
        httpClient = supabaseHttpClient,
        authRepository = authRepository,
    )

    val privacyPreferencesPort: PrivacyPreferencesPort = sessionDataStore

    val brandProfilePort: BrandProfilePort = sessionDataStore

    val accountDeletionPort: AccountDeletionPort = SupabaseAccountDeletionRepository(
        httpClient = supabaseHttpClient,
        authRepository = authRepository,
    )

    val groqApiService: GroqApiService = OkHttpGroqApiService(
        okHttpClient = okHttpClient,
        moshi = moshi,
    )

    val groqRepository: GroqRepository = GroqRepository(
        apiService = groqApiService,
        sessionDataStore = sessionDataStore,
        moshi = moshi,
    )

    val groqAiPort: GroqAiPort = groqRepository

    val widgetSyncPort: WidgetSyncPort = object : WidgetSyncPort {
        override fun updateNextGigWidget(
            activities: List<Activity>,
            departures: Map<String, ScheduledDeparture>,
            today: LocalDate,
            venueDisplay: (String) -> String,
            supplierDisplay: (String) -> String,
        ) {
            val snapshot = NextGigWidgetStore.computeSnapshot(
                activities = activities,
                scheduledDepartures = departures,
                today = today,
                nowTime = LocalTime.now(SANTO_DOMINGO_ZONE),
                venueNameResolver = venueDisplay,
                supplierNameResolver = supplierDisplay,
            )
            NextGigWidgetStore.saveAndRefresh(appContext, snapshot)
        }

        override fun clearNextGigWidget() {
            NextGigWidgetStore.clearAndRefresh(appContext)
        }
    }

    val quoteFunnelPort: com.example.data.port.QuoteFunnelPort =
        com.example.data.local.quotes.RoomQuoteFunnelRepository(
            com.example.data.local.quotes.QuotesDatabase.getInstance(appContext).savedQuoteDao(),
            moshi,
        )

    val quoteFollowUpPort: com.example.data.port.QuoteFollowUpPort =
        com.example.alarms.QuoteFollowUpScheduler(appContext)

    val quoteFunnelManager: QuoteFunnelManager = QuoteFunnelManager(quoteFunnelPort, quoteFollowUpPort)

    val crmPort: com.example.data.port.CrmPort =
        com.example.data.local.crm.RoomCrmRepository(
            com.example.data.local.crm.CrmDatabase.getInstance(appContext).crmDao(),
        )

    val crmFollowUpPort: com.example.data.port.CrmFollowUpPort =
        com.example.alarms.CrmFollowUpScheduler(appContext)

    val crmManager: CrmManager = CrmManager(crmPort, crmFollowUpPort)

    val activityFinancePort: ActivityFinancePort = ActivityFinanceStore(
        context = appContext,
        moshi = moshi,
    )

    private val cacheDatabase: ApptividadesCacheDatabase =
        ApptividadesCacheDatabase.getInstance(appContext)

    val offlineCachePort: OfflineCachePort = OfflineCacheRepository(
        dao = cacheDatabase.offlineCacheDao(),
    )

    val connectivityPort: ConnectivityPort = NetworkConnectivityMonitor(appContext)

    val loginHistoryRepository: LoginHistoryRepository = LoginHistoryStore(
        context = appContext,
        okHttpClient = okHttpClient,
        privacyPreferencesPort = privacyPreferencesPort,
    )

    private val localPhotonPlaceCache = LocalPhotonPlaceCache(appContext, moshi)

    val mapsAndLocationPort: MapsAndLocationPort = OpenMapsApiClient(
        context = appContext,
        okHttpClient = okHttpClient,
        placeCache = localPhotonPlaceCache,
    )

    val departureAlarmScheduler: DepartureAlarmScheduler = DepartureAlarmScheduler(
        context = appContext,
        sessionDataStore = sessionDataStore,
        mapsAndLocationPort = mapsAndLocationPort,
    )

    val departureAlarmPort: DepartureAlarmPort = departureAlarmScheduler

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        /**
         * Returns the process-wide AppContainer shared between UI (MainActivity/ViewModel)
         * and background WorkManager workers so auth refresh serialization uses one Mutex.
         */
        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
