package com.example.data.local

import com.example.domain.rules.TrackedRoute
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.onStart
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.port.AppThemeMode
import com.example.data.port.BrandProfilePort
import com.example.data.port.PrivacyPreferences
import com.example.data.port.PrivacyPreferencesPort
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.ArtistBrandProfile
import com.example.domain.model.AuthSession
import com.example.domain.model.CachedActivityRoute
import com.example.domain.rules.RouteOriginKind
import com.example.domain.model.GroqModelOption
import com.example.domain.model.ScheduledDeparture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

private val Context.apptividadesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "apptividades_prefs"
)

data class LocalDeparturePrefs(
    val setupMinutes: Int = 45,
    val prepMinutes: Int = 60,
    val travelBufferMinutes: Int = 15,
    val internalTransferInMinutes: Int = 15,
    val preStartMarginMinutes: Int = 15,
    val internalTransferOutMinutes: Int = 15,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val scoreReminderDays: Int = 3,
    val contingencyEnabled: Boolean = true,
    val contingencyLeadMinutes: Int = 30,
)

interface SessionStorage {
    suspend fun loadSession(): AuthSession?
    suspend fun saveSession(session: AuthSession)
    suspend fun clearSession()
}

class SessionDataStore(
    private val context: Context,
    private val tokenCipher: TokenCipher = AndroidKeystoreTokenCipher(),
) : SessionStorage, PrivacyPreferencesPort, BrandProfilePort {

    private object Keys {
        val USER_ID = stringPreferencesKey("auth_user_id")
        val EMAIL = stringPreferencesKey("auth_email")
        val ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        val REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
        val EXPIRES_AT = longPreferencesKey("auth_expires_at")
        val MONTHLY_GOAL = stringPreferencesKey("local_monthly_goal")
        val SETUP_MINUTES = intPreferencesKey("local_setup_minutes")
        val PREP_MINUTES = intPreferencesKey("local_prep_minutes")
        val TRAVEL_BUFFER_MINUTES = intPreferencesKey("local_travel_buffer_minutes")
        val INTERNAL_TRANSFER_IN_MINUTES = intPreferencesKey("local_internal_transfer_in_minutes")
        val PRE_START_MARGIN_MINUTES = intPreferencesKey("local_pre_start_margin_minutes")
        val INTERNAL_TRANSFER_OUT_MINUTES = intPreferencesKey("local_internal_transfer_out_minutes")
        val HOME_LAT = doublePreferencesKey("local_home_lat")
        val HOME_LNG = doublePreferencesKey("local_home_lng")
        val SCORE_REMINDER_DAYS = intPreferencesKey("local_score_reminder_days")
        val CONTINGENCY_ENABLED = booleanPreferencesKey("local_contingency_enabled")
        val CONTINGENCY_LEAD_MINUTES = intPreferencesKey("local_contingency_lead_minutes")
        val SCHEDULED_DEPARTURES_JSON = stringPreferencesKey("scheduled_departures_json")
        val ACTIVITY_ROUTES_JSON = stringPreferencesKey("activity_routes_cache_json")
        val ACTIVITY_TIMINGS_JSON = stringPreferencesKey("activity_timings_override_json")
        val CONTINGENCY_UNDER_CONTROL_JSON = stringPreferencesKey("contingency_under_control_json")
        val DAILY_OVERDUE_SUMMARY = stringPreferencesKey("daily_overdue_summary")
        val NOTIFIED_DEPARTURE_KEYS_JSON = stringPreferencesKey("notified_departure_keys_json")
        val PRIVACY_SCREEN_SECURE = booleanPreferencesKey("privacy_screen_secure")
        val PRIVACY_RESOLVE_EXTERNAL_IP = booleanPreferencesKey("privacy_resolve_external_ip")
        val PRIVACY_APP_LOCK_ENABLED = booleanPreferencesKey("privacy_app_lock_enabled")
        val APPEARANCE_THEME_MODE = stringPreferencesKey("appearance_theme_mode")
        val DEFAULT_USD_TO_DOP_RATE = stringPreferencesKey("default_usd_to_dop_rate")
        val CONVERT_MIXED_CURRENCIES = booleanPreferencesKey("convert_mixed_currencies")
        val BRAND_ARTIST_NAME = stringPreferencesKey("brand_artist_name")
        val BRAND_SUBTITLE = stringPreferencesKey("brand_subtitle")
        val BRAND_CONTACT_LINE = stringPreferencesKey("brand_contact_line")
        val BRAND_LOGO_FILE = stringPreferencesKey("brand_logo_file")
        val BRAND_SIGNATURE_FILE = stringPreferencesKey("brand_signature_file")
        val GROQ_API_KEY_ENC = stringPreferencesKey("groq_api_key_encrypted")
        val GROQ_API_KEY_MASKED = stringPreferencesKey("groq_api_key_masked")
        val GROQ_MODEL_ID = stringPreferencesKey("groq_model_id")
        val GROQ_ANONYMIZE_CLIENTS = booleanPreferencesKey("groq_anonymize_clients")
    }

    override suspend fun loadSession(): AuthSession? {
        val prefs = context.apptividadesDataStore.data.first()
        val userId = prefs[Keys.USER_ID] ?: return null
        val rawAccess = prefs[Keys.ACCESS_TOKEN] ?: return null
        val rawRefresh = prefs[Keys.REFRESH_TOKEN] ?: return null

        val accessToken = tokenCipher.decrypt(rawAccess) ?: return null
        val refreshToken = tokenCipher.decrypt(rawRefresh) ?: return null
        val email = prefs[Keys.EMAIL].orEmpty()
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: 0L

        // Transparently migrate legacy unencrypted tokens into AES-GCM Keystore encryption
        if (!tokenCipher.isEncrypted(rawAccess) || !tokenCipher.isEncrypted(rawRefresh)) {
            runCatching {
                context.apptividadesDataStore.edit { editPrefs ->
                    editPrefs[Keys.ACCESS_TOKEN] = tokenCipher.encrypt(accessToken)
                    editPrefs[Keys.REFRESH_TOKEN] = tokenCipher.encrypt(refreshToken)
                }
            }
        }

        return AuthSession(
            userId = userId,
            email = email,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
        )
    }

    override suspend fun saveSession(session: AuthSession) {
        val encryptedAccess = tokenCipher.encrypt(session.accessToken)
        val encryptedRefresh = tokenCipher.encrypt(session.refreshToken)
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.EMAIL] = session.email
            prefs[Keys.ACCESS_TOKEN] = encryptedAccess
            prefs[Keys.REFRESH_TOKEN] = encryptedRefresh
            prefs[Keys.EXPIRES_AT] = session.expiresAtEpochSeconds
        }
    }

    override suspend fun clearSession() {
        context.apptividadesDataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.EMAIL)
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.EXPIRES_AT)
            prefs.remove(Keys.SCHEDULED_DEPARTURES_JSON)
            prefs.remove(Keys.ACTIVITY_ROUTES_JSON)
            prefs.remove(Keys.ACTIVITY_TIMINGS_JSON)
            prefs.remove(Keys.CONTINGENCY_UNDER_CONTROL_JSON)
            prefs.remove(Keys.DAILY_OVERDUE_SUMMARY)
            prefs.remove(Keys.NOTIFIED_DEPARTURE_KEYS_JSON)
        }
    }

    fun observeMonthlyGoal(): Flow<BigDecimal> =
        context.apptividadesDataStore.data.map { prefs ->
            prefs[Keys.MONTHLY_GOAL]?.toBigDecimalOrNull() ?: DEFAULT_MONTHLY_GOAL
        }

    suspend fun loadMonthlyGoal(): BigDecimal = observeMonthlyGoal().first()

    suspend fun saveMonthlyGoal(goal: BigDecimal) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.MONTHLY_GOAL] = goal.stripTrailingZeros().toPlainString()
        }
    }

    override fun observePrivacyPreferences(): Flow<PrivacyPreferences> =
        context.apptividadesDataStore.data.map { prefs ->
            PrivacyPreferences(
                screenPrivacyEnabled = prefs[Keys.PRIVACY_SCREEN_SECURE] ?: false,
                resolveExternalIpInHistory = prefs[Keys.PRIVACY_RESOLVE_EXTERNAL_IP] ?: false,
                appLockEnabled = prefs[Keys.PRIVACY_APP_LOCK_ENABLED] ?: false,
                themeMode = AppThemeMode.fromStorage(prefs[Keys.APPEARANCE_THEME_MODE]),
                defaultUsdToDopRate = prefs[Keys.DEFAULT_USD_TO_DOP_RATE]
                    ?.toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?: BigDecimal("60.00"),
                convertMixedCurrencies = prefs[Keys.CONVERT_MIXED_CURRENCIES] ?: true,
            )
        }

    override suspend fun setScreenPrivacyEnabled(enabled: Boolean) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_SCREEN_SECURE] = enabled
        }
    }

    override suspend fun setResolveExternalIpInHistory(enabled: Boolean) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_RESOLVE_EXTERNAL_IP] = enabled
        }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_APP_LOCK_ENABLED] = enabled
        }
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.APPEARANCE_THEME_MODE] = mode.storageValue
        }
    }

    override suspend fun setDefaultUsdToDopRate(rate: BigDecimal) {
        if (rate <= BigDecimal.ZERO) return
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_USD_TO_DOP_RATE] = rate.stripTrailingZeros().toPlainString()
        }
    }

    override suspend fun setConvertMixedCurrencies(enabled: Boolean) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.CONVERT_MIXED_CURRENCIES] = enabled
        }
    }

    override fun observeBrandProfile(): Flow<ArtistBrandProfile> =
        context.apptividadesDataStore.data.map { prefs ->
            ArtistBrandProfile(
                artistName = prefs[Keys.BRAND_ARTIST_NAME].orEmpty(),
                subtitle = prefs[Keys.BRAND_SUBTITLE].orEmpty(),
                contactLine = prefs[Keys.BRAND_CONTACT_LINE].orEmpty(),
                logoFileName = prefs[Keys.BRAND_LOGO_FILE]?.takeIf { it.isNotBlank() },
                signatureFileName = prefs[Keys.BRAND_SIGNATURE_FILE]?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun saveBrandProfile(profile: ArtistBrandProfile) {
        context.apptividadesDataStore.edit { prefs ->
            val cleanName = profile.artistName.trim()
            val cleanSub = profile.subtitle.trim()
            val cleanContact = profile.contactLine.trim()
            if (cleanName.isBlank()) prefs.remove(Keys.BRAND_ARTIST_NAME) else prefs[Keys.BRAND_ARTIST_NAME] = cleanName
            if (cleanSub.isBlank()) prefs.remove(Keys.BRAND_SUBTITLE) else prefs[Keys.BRAND_SUBTITLE] = cleanSub
            if (cleanContact.isBlank()) prefs.remove(Keys.BRAND_CONTACT_LINE) else prefs[Keys.BRAND_CONTACT_LINE] = cleanContact
            val logo = profile.logoFileName?.trim()?.takeIf { it.isNotEmpty() }
            if (logo == null) prefs.remove(Keys.BRAND_LOGO_FILE) else prefs[Keys.BRAND_LOGO_FILE] = logo
            val sig = profile.signatureFileName?.trim()?.takeIf { it.isNotEmpty() }
            if (sig == null) prefs.remove(Keys.BRAND_SIGNATURE_FILE) else prefs[Keys.BRAND_SIGNATURE_FILE] = sig
        }
    }

    override suspend fun clearBrandProfile() {
        context.apptividadesDataStore.edit { prefs ->
            prefs.remove(Keys.BRAND_ARTIST_NAME)
            prefs.remove(Keys.BRAND_SUBTITLE)
            prefs.remove(Keys.BRAND_CONTACT_LINE)
            prefs.remove(Keys.BRAND_LOGO_FILE)
            prefs.remove(Keys.BRAND_SIGNATURE_FILE)
        }
    }

    data class StoredGroqPrefs(
        val hasEncryptedUserKey: Boolean = false,
        val keyNeedsReentry: Boolean = false,
        val maskedKeyPreview: String? = null,
        val selectedModel: GroqModelOption = GroqModelOption.GPT_OSS_120B,
        val anonymizeClientNames: Boolean = false,
    )

    /*
     * The Groq key belongs to the signed-in account. Keys saved before accounts were
     * separated live under the unscoped names and are moved to the first account that reads them.
     */
    private fun groqKeyName(userId: String) = stringPreferencesKey("groq_api_key_encrypted_$userId")

    private fun groqMaskName(userId: String) = stringPreferencesKey("groq_api_key_masked_$userId")

    private suspend fun adoptLegacyGroqKey() {
        val current = context.apptividadesDataStore.data.first()
        if (current[Keys.USER_ID] == null || current[Keys.GROQ_API_KEY_ENC] == null) return
        context.apptividadesDataStore.edit { prefs ->
            val userId = prefs[Keys.USER_ID] ?: return@edit
            val legacy = prefs[Keys.GROQ_API_KEY_ENC] ?: return@edit
            if (prefs[groqKeyName(userId)] == null) {
                prefs[groqKeyName(userId)] = legacy
                prefs[Keys.GROQ_API_KEY_MASKED]?.let { prefs[groqMaskName(userId)] = it }
            }
            prefs.remove(Keys.GROQ_API_KEY_ENC)
            prefs.remove(Keys.GROQ_API_KEY_MASKED)
        }
    }

    /** Null unless the stored value is really encrypted and can be decrypted on this device. */
    private fun readGroqKey(stored: String): String? =
        stored.takeIf { tokenCipher.isEncrypted(it) }
            ?.let { tokenCipher.decrypt(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun observeGroqPrefs(): Flow<StoredGroqPrefs> =
        context.apptividadesDataStore.data
            .onStart { adoptLegacyGroqKey() }
            .map { prefs ->
                val userId = prefs[Keys.USER_ID]
                // The unscoped fallback covers a sign-in that happens while this flow is collected.
                val stored = userId?.let { prefs[groqKeyName(it)] ?: prefs[Keys.GROQ_API_KEY_ENC] }
                val readable = stored != null && readGroqKey(stored) != null
                val mask = userId?.let { prefs[groqMaskName(it)] ?: prefs[Keys.GROQ_API_KEY_MASKED] }
                StoredGroqPrefs(
                    hasEncryptedUserKey = readable,
                    keyNeedsReentry = stored != null && !readable,
                    maskedKeyPreview = mask?.takeIf { readable && it.isNotBlank() },
                    selectedModel = GroqModelOption.fromId(prefs[Keys.GROQ_MODEL_ID]),
                    anonymizeClientNames = prefs[Keys.GROQ_ANONYMIZE_CLIENTS] ?: false,
                )
            }

    suspend fun loadDecryptedGroqApiKey(): String? {
        adoptLegacyGroqKey()
        val prefs = context.apptividadesDataStore.data.first()
        val userId = prefs[Keys.USER_ID] ?: return null
        return prefs[groqKeyName(userId)]?.let(::readGroqKey)
    }

    /** Fails, storing nothing, without a signed-in account or when encryption does not work. */
    suspend fun saveEncryptedGroqApiKey(rawKey: String, maskedPreview: String) {
        val encrypted = tokenCipher.encrypt(rawKey.trim())
        check(tokenCipher.isEncrypted(encrypted)) {
            "No pudimos cifrar la clave en este teléfono, así que no se guardó."
        }
        context.apptividadesDataStore.edit { prefs ->
            val userId = checkNotNull(prefs[Keys.USER_ID]) { "Inicia sesión para guardar tu clave." }
            prefs[groqKeyName(userId)] = encrypted
            prefs[groqMaskName(userId)] = maskedPreview
            prefs.remove(Keys.GROQ_API_KEY_ENC)
            prefs.remove(Keys.GROQ_API_KEY_MASKED)
        }
    }

    suspend fun clearGroqApiKey() {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.USER_ID]?.let { userId ->
                prefs.remove(groqKeyName(userId))
                prefs.remove(groqMaskName(userId))
            }
            prefs.remove(Keys.GROQ_API_KEY_ENC)
            prefs.remove(Keys.GROQ_API_KEY_MASKED)
        }
    }

    @VisibleForTesting
    internal suspend fun writeLegacyGroqKeyForTest(stored: String, maskedPreview: String) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.GROQ_API_KEY_ENC] = stored
            prefs[Keys.GROQ_API_KEY_MASKED] = maskedPreview
        }
    }

    suspend fun setGroqModel(model: GroqModelOption) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.GROQ_MODEL_ID] = model.modelId
        }
    }

    suspend fun setGroqAnonymizeClientNames(enabled: Boolean) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.GROQ_ANONYMIZE_CLIENTS] = enabled
        }
    }

    suspend fun loadDeparturePrefs(): LocalDeparturePrefs {
        val prefs = context.apptividadesDataStore.data.first()
        return LocalDeparturePrefs(
            setupMinutes = prefs[Keys.SETUP_MINUTES] ?: 45,
            prepMinutes = prefs[Keys.PREP_MINUTES] ?: 60,
            travelBufferMinutes = prefs[Keys.TRAVEL_BUFFER_MINUTES] ?: 15,
            internalTransferInMinutes = prefs[Keys.INTERNAL_TRANSFER_IN_MINUTES] ?: 15,
            preStartMarginMinutes = prefs[Keys.PRE_START_MARGIN_MINUTES] ?: 15,
            internalTransferOutMinutes = prefs[Keys.INTERNAL_TRANSFER_OUT_MINUTES] ?: 15,
            homeLatitude = prefs[Keys.HOME_LAT],
            homeLongitude = prefs[Keys.HOME_LNG],
            scoreReminderDays = prefs[Keys.SCORE_REMINDER_DAYS] ?: 3,
            contingencyEnabled = prefs[Keys.CONTINGENCY_ENABLED] ?: true,
            contingencyLeadMinutes = prefs[Keys.CONTINGENCY_LEAD_MINUTES] ?: 30,
        )
    }

    suspend fun saveDeparturePrefs(departurePrefs: LocalDeparturePrefs) {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.SETUP_MINUTES] = departurePrefs.setupMinutes
            prefs[Keys.PREP_MINUTES] = departurePrefs.prepMinutes
            prefs[Keys.TRAVEL_BUFFER_MINUTES] = departurePrefs.travelBufferMinutes
            prefs[Keys.INTERNAL_TRANSFER_IN_MINUTES] = departurePrefs.internalTransferInMinutes
            prefs[Keys.PRE_START_MARGIN_MINUTES] = departurePrefs.preStartMarginMinutes
            prefs[Keys.INTERNAL_TRANSFER_OUT_MINUTES] = departurePrefs.internalTransferOutMinutes
            prefs[Keys.SCORE_REMINDER_DAYS] = departurePrefs.scoreReminderDays.coerceIn(0, 14)
            prefs[Keys.CONTINGENCY_ENABLED] = departurePrefs.contingencyEnabled
            prefs[Keys.CONTINGENCY_LEAD_MINUTES] = departurePrefs.contingencyLeadMinutes.coerceIn(5, 120)
            if (departurePrefs.homeLatitude != null && departurePrefs.homeLongitude != null) {
                prefs[Keys.HOME_LAT] = departurePrefs.homeLatitude
                prefs[Keys.HOME_LNG] = departurePrefs.homeLongitude
            } else {
                prefs.remove(Keys.HOME_LAT)
                prefs.remove(Keys.HOME_LNG)
            }
        }
    }

    fun observeScheduledDepartures(): Flow<Map<String, ScheduledDeparture>> =
        context.apptividadesDataStore.data.map { prefs ->
            parseScheduledDepartures(prefs[Keys.SCHEDULED_DEPARTURES_JSON])
        }

    suspend fun loadScheduledDepartures(): Map<String, ScheduledDeparture> =
        observeScheduledDepartures().first()

    suspend fun saveScheduledDepartures(departures: Map<String, ScheduledDeparture>) {
        val arr = JSONArray()
        for (item in departures.values) {
            arr.put(
                JSONObject().apply {
                    put("activityId", item.activityId)
                    put("venueName", item.venueName)
                    put("eventDate", item.eventDate.toString())
                    put("eventTime", item.eventTime.toString())
                    if (item.latitude != null) put("latitude", item.latitude)
                    if (item.longitude != null) put("longitude", item.longitude)
                    put("travelMinutes", item.travelMinutes)
                    if (item.distanceKm != null) put("distanceKm", item.distanceKm)
                    put("prepareAtEpochMillis", item.prepareAtEpochMillis)
                    put("leaveAtEpochMillis", item.leaveAtEpochMillis)
                    put("estimatedArrivalTime", item.estimatedArrivalTime.toString())
                    put("isOsrmEstimated", item.isOsrmEstimated)
                    put("alarmsScheduled", item.alarmsScheduled)
                    item.supplierName?.let { put("supplierName", it) }
                    item.description?.let { put("description", it) }
                    item.contingencyAtEpochMillis?.let { put("contingencyAtEpochMillis", it) }
                    item.contingencyFinalAtEpochMillis?.let { put("contingencyFinalAtEpochMillis", it) }
                    item.originKind?.let { put("originKind", it.name) }
                    item.originVenueName?.let { put("originVenueName", it) }
                    item.originLatitude?.let { put("originLatitude", it) }
                    item.originLongitude?.let { put("originLongitude", it) }
                    put("shortfallMinutes", item.shortfallMinutes)
                    item.unpinnedPreviousVenueName?.let { put("unpinnedPreviousVenueName", it) }
                    put("isTrackedDrive", item.isTrackedDrive)
                    item.latestLeaveAtEpochMillis?.let { put("latestLeaveAtEpochMillis", it) }
                }
            )
        }
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.SCHEDULED_DEPARTURES_JSON] = arr.toString()
        }
    }

    fun observeActivityRoutes(): Flow<Map<String, CachedActivityRoute>> =
        context.apptividadesDataStore.data.map { prefs ->
            parseActivityRoutes(prefs[Keys.ACTIVITY_ROUTES_JSON])
        }

    suspend fun loadActivityRoutes(): Map<String, CachedActivityRoute> =
        observeActivityRoutes().first()

    suspend fun saveActivityRoutes(routes: Map<String, CachedActivityRoute>) {
        val arr = JSONArray()
        for (item in routes.values) {
            arr.put(
                JSONObject().apply {
                    put("activityId", item.activityId)
                    put("originLat", item.originLat)
                    put("originLng", item.originLng)
                    put("destLat", item.destLat)
                    put("destLng", item.destLng)
                    put("travelMinutes", item.travelMinutes)
                    if (item.distanceKm != null) put("distanceKm", item.distanceKm)
                    put("fromOsrm", item.fromOsrm)
                    item.originKind?.let { put("originKind", it.name) }
                    item.originVenueName?.let { put("originVenueName", it) }
                    put("shortfallMinutes", item.shortfallMinutes)
                    item.unpinnedPreviousVenueName?.let { put("unpinnedPreviousVenueName", it) }
                    item.trackedDriveMinutes?.let { put("trackedDriveMinutes", it) }
                    item.trackedWalkMinutes?.let { put("trackedWalkMinutes", it) }
                    item.trackedSetupMinutes?.let { put("trackedSetupMinutes", it) }
                    item.trackedMarginMinutes?.let { put("trackedMarginMinutes", it) }
                }
            )
        }
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.ACTIVITY_ROUTES_JSON] = arr.toString()
        }
    }

    suspend fun saveSingleActivityRoute(activityId: String, route: CachedActivityRoute) {
        val current = loadActivityRoutes().toMutableMap()
        current[activityId] = route.copy(activityId = activityId)
        saveActivityRoutes(current)
    }

    suspend fun removeActivityRoute(activityId: String) {
        val current = loadActivityRoutes().toMutableMap()
        if (current.remove(activityId) != null) {
            saveActivityRoutes(current)
        }
    }

    fun observeActivityTimings(): Flow<Map<String, ActivityTimelineOverride>> =
        context.apptividadesDataStore.data.map { prefs ->
            parseActivityTimings(prefs[Keys.ACTIVITY_TIMINGS_JSON])
        }

    suspend fun loadActivityTimings(): Map<String, ActivityTimelineOverride> =
        observeActivityTimings().first()

    suspend fun saveActivityTimingOverride(activityId: String, override: ActivityTimelineOverride?) {
        val current = loadActivityTimings().toMutableMap()
        if (override == null || !override.hasAnyOverride) {
            current.remove(activityId)
        } else {
            current[activityId] = override.copy(activityId = activityId)
        }
        val arr = JSONArray()
        for (item in current.values) {
            arr.put(
                JSONObject().apply {
                    put("activityId", item.activityId)
                    item.prepMinutes?.let { put("prepMinutes", it) }
                    item.travelMinutes?.let { put("travelMinutes", it) }
                    item.travelBufferMinutes?.let { put("travelBufferMinutes", it) }
                    item.internalTransferInMinutes?.let { put("internalTransferInMinutes", it) }
                    item.setupMinutes?.let { put("setupMinutes", it) }
                    item.preStartMarginMinutes?.let { put("preStartMarginMinutes", it) }
                    item.eventMinutes?.let { put("eventMinutes", it) }
                    item.teardownMinutes?.let { put("teardownMinutes", it) }
                    item.internalTransferOutMinutes?.let { put("internalTransferOutMinutes", it) }
                }
            )
        }
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.ACTIVITY_TIMINGS_JSON] = arr.toString()
        }
    }

    suspend fun saveDailyOverdueSummary(summary: String?) {
        context.apptividadesDataStore.edit { prefs ->
            if (summary.isNullOrBlank()) prefs.remove(Keys.DAILY_OVERDUE_SUMMARY)
            else prefs[Keys.DAILY_OVERDUE_SUMMARY] = summary
        }
    }

    suspend fun loadDailyOverdueSummary(): String? =
        context.apptividadesDataStore.data.first()[Keys.DAILY_OVERDUE_SUMMARY]

    fun observeContingencyUnderControlIds(): Flow<Set<String>> =
        context.apptividadesDataStore.data.map { prefs ->
            parseStringList(prefs[Keys.CONTINGENCY_UNDER_CONTROL_JSON]).toSet()
        }

    suspend fun loadContingencyUnderControlIds(): Set<String> =
        observeContingencyUnderControlIds().first()

    suspend fun setContingencyUnderControl(activityId: String, underControl: Boolean) {
        if (activityId.isBlank()) return
        context.apptividadesDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.CONTINGENCY_UNDER_CONTROL_JSON]).toMutableSet()
            if (underControl) current.add(activityId) else current.remove(activityId)
            val arr = JSONArray()
            current.toList().takeLast(MAX_NOTIFIED_DEDUP_KEYS).forEach { arr.put(it) }
            prefs[Keys.CONTINGENCY_UNDER_CONTROL_JSON] = arr.toString()
        }
    }

    suspend fun pruneContingencyUnderControlIds(validActivityIds: Set<String>) {
        context.apptividadesDataStore.edit { prefs ->
            val current = parseStringList(prefs[Keys.CONTINGENCY_UNDER_CONTROL_JSON])
            val filtered = current.filter { it in validActivityIds }
            if (filtered.size != current.size) {
                val arr = JSONArray()
                filtered.forEach { arr.put(it) }
                prefs[Keys.CONTINGENCY_UNDER_CONTROL_JSON] = arr.toString()
            }
        }
    }

    suspend fun markNotificationDeliveredIfNew(dedupKey: String): Boolean {
        if (dedupKey.isBlank()) return true
        var wasNew = false
        context.apptividadesDataStore.edit { prefs ->
            val existing = parseStringList(prefs[Keys.NOTIFIED_DEPARTURE_KEYS_JSON])
            if (dedupKey in existing) {
                wasNew = false
            } else {
                wasNew = true
                val updated = (existing + dedupKey).takeLast(MAX_NOTIFIED_DEDUP_KEYS)
                val arr = JSONArray()
                updated.forEach { arr.put(it) }
                prefs[Keys.NOTIFIED_DEPARTURE_KEYS_JSON] = arr.toString()
            }
        }
        return wasNew
    }

    private fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseScheduledDepartures(raw: String?): Map<String, ScheduledDeparture> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = linkedMapOf<String, ScheduledDeparture>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("activityId")
                val dist = if (obj.has("distanceKm") && !obj.isNull("distanceKm")) {
                    obj.optDouble("distanceKm", Double.NaN).takeIf { !it.isNaN() }
                } else null
                val lat = if (obj.has("latitude") && !obj.isNull("latitude")) {
                    obj.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() }
                } else null
                val lng = if (obj.has("longitude") && !obj.isNull("longitude")) {
                    obj.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() }
                } else null
                val contingencyAt = if (obj.has("contingencyAtEpochMillis") && !obj.isNull("contingencyAtEpochMillis")) {
                    obj.optLong("contingencyAtEpochMillis", -1L).takeIf { it > 0L }
                } else null
                val contingencyFinalAt = if (obj.has("contingencyFinalAtEpochMillis") && !obj.isNull("contingencyFinalAtEpochMillis")) {
                    obj.optLong("contingencyFinalAtEpochMillis", -1L).takeIf { it > 0L }
                } else null
                val supplierName = obj.optString("supplierName").takeIf { it.isNotBlank() }
                val description = obj.optString("description").takeIf { it.isNotBlank() }
                map[id] = ScheduledDeparture(
                    activityId = id,
                    venueName = obj.getString("venueName"),
                    eventDate = LocalDate.parse(obj.getString("eventDate")),
                    eventTime = LocalTime.parse(obj.getString("eventTime")),
                    latitude = lat,
                    longitude = lng,
                    travelMinutes = obj.getInt("travelMinutes"),
                    distanceKm = dist,
                    prepareAtEpochMillis = obj.getLong("prepareAtEpochMillis"),
                    leaveAtEpochMillis = obj.getLong("leaveAtEpochMillis"),
                    estimatedArrivalTime = LocalTime.parse(obj.getString("estimatedArrivalTime")),
                    isOsrmEstimated = obj.optBoolean("isOsrmEstimated", false),
                    alarmsScheduled = obj.optBoolean("alarmsScheduled", false),
                    supplierName = supplierName,
                    description = description,
                    contingencyAtEpochMillis = contingencyAt,
                    contingencyFinalAtEpochMillis = contingencyFinalAt,
                    originKind = obj.optRouteOriginKind(),
                    originVenueName = obj.optTextOrNull("originVenueName"),
                    originLatitude = obj.optDouble("originLatitude", Double.NaN).takeIf { !it.isNaN() },
                    originLongitude = obj.optDouble("originLongitude", Double.NaN).takeIf { !it.isNaN() },
                    shortfallMinutes = obj.optInt("shortfallMinutes", 0),
                    unpinnedPreviousVenueName = obj.optTextOrNull("unpinnedPreviousVenueName"),
                    isTrackedDrive = obj.optBoolean("isTrackedDrive", false),
                    latestLeaveAtEpochMillis = obj.optLong("latestLeaveAtEpochMillis", -1L).takeIf { it > 0L },
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun trackedRoutesName(userId: String) = stringPreferencesKey("tracked_routes_json_$userId")

    /** Routes the artist measured. They belong to the account and survive signing out. */
    fun observeTrackedRoutes(): Flow<Map<String, TrackedRoute>> =
        context.apptividadesDataStore.data.map { prefs ->
            val userId = prefs[Keys.USER_ID]
            parseTrackedRoutes(userId?.let { prefs[trackedRoutesName(it)] })
        }

    suspend fun loadTrackedRoutes(): Map<String, TrackedRoute> = observeTrackedRoutes().first()

    /** A null [route] removes the saved route of [venueKey]. */
    suspend fun saveTrackedRoute(venueKey: String, route: TrackedRoute?) {
        context.apptividadesDataStore.edit { prefs ->
            val userId = prefs[Keys.USER_ID] ?: return@edit
            val routes = parseTrackedRoutes(prefs[trackedRoutesName(userId)]).toMutableMap()
            if (route == null) routes.remove(venueKey) else routes[venueKey] = route
            val arr = JSONArray()
            routes.values.forEach { item ->
                val drives = JSONObject()
                item.driveMinutesByOrigin.forEach { (origin, minutes) -> drives.put(origin, minutes) }
                arr.put(
                    JSONObject()
                        .put("venueKey", item.venueKey)
                        .put("walkMinutes", item.walkMinutes)
                        .put("setupMinutes", item.setupMinutes)
                        .put("marginMinutes", item.marginMinutes)
                        .put("drives", drives)
                )
            }
            prefs[trackedRoutesName(userId)] = arr.toString()
        }
    }

    suspend fun clearTrackedRoutes() {
        context.apptividadesDataStore.edit { prefs ->
            prefs[Keys.USER_ID]?.let { prefs.remove(trackedRoutesName(it)) }
        }
    }

    private fun parseTrackedRoutes(raw: String?): Map<String, TrackedRoute> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).associate { index ->
                val obj = arr.getJSONObject(index)
                val drives = obj.getJSONObject("drives")
                val venueKey = obj.getString("venueKey")
                venueKey to TrackedRoute(
                    venueKey = venueKey,
                    walkMinutes = obj.getInt("walkMinutes"),
                    setupMinutes = obj.getInt("setupMinutes"),
                    marginMinutes = obj.getInt("marginMinutes"),
                    driveMinutesByOrigin = drives.keys().asSequence().associateWith { drives.getInt(it) },
                )
            }
        }.getOrDefault(emptyMap())
    }

    private fun JSONObject.optMinutesOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optTextOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optRouteOriginKind(): RouteOriginKind? =
        RouteOriginKind.entries.firstOrNull { it.name == optString("originKind") }

    private fun parseActivityRoutes(raw: String?): Map<String, CachedActivityRoute> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = linkedMapOf<String, CachedActivityRoute>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("activityId")
                val dist = if (obj.has("distanceKm") && !obj.isNull("distanceKm")) {
                    obj.optDouble("distanceKm", Double.NaN).takeIf { !it.isNaN() }
                } else null
                map[id] = CachedActivityRoute(
                    activityId = id,
                    originLat = obj.getDouble("originLat"),
                    originLng = obj.getDouble("originLng"),
                    destLat = obj.getDouble("destLat"),
                    destLng = obj.getDouble("destLng"),
                    travelMinutes = obj.getInt("travelMinutes"),
                    distanceKm = dist,
                    fromOsrm = obj.optBoolean("fromOsrm", true),
                    originKind = obj.optRouteOriginKind(),
                    originVenueName = obj.optTextOrNull("originVenueName"),
                    shortfallMinutes = obj.optInt("shortfallMinutes", 0),
                    unpinnedPreviousVenueName = obj.optTextOrNull("unpinnedPreviousVenueName"),
                    trackedDriveMinutes = obj.optMinutesOrNull("trackedDriveMinutes"),
                    trackedWalkMinutes = obj.optMinutesOrNull("trackedWalkMinutes"),
                    trackedSetupMinutes = obj.optMinutesOrNull("trackedSetupMinutes"),
                    trackedMarginMinutes = obj.optMinutesOrNull("trackedMarginMinutes"),
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun parseActivityTimings(raw: String?): Map<String, ActivityTimelineOverride> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = linkedMapOf<String, ActivityTimelineOverride>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("activityId")
                fun optIntOrNull(key: String): Int? =
                    if (obj.has(key) && !obj.isNull(key)) obj.getInt(key) else null
                map[id] = ActivityTimelineOverride(
                    activityId = id,
                    prepMinutes = optIntOrNull("prepMinutes"),
                    travelMinutes = optIntOrNull("travelMinutes"),
                    travelBufferMinutes = optIntOrNull("travelBufferMinutes"),
                    internalTransferInMinutes = optIntOrNull("internalTransferInMinutes"),
                    setupMinutes = optIntOrNull("setupMinutes"),
                    preStartMarginMinutes = optIntOrNull("preStartMarginMinutes"),
                    eventMinutes = optIntOrNull("eventMinutes"),
                    teardownMinutes = optIntOrNull("teardownMinutes"),
                    internalTransferOutMinutes = optIntOrNull("internalTransferOutMinutes"),
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    companion object {
        val DEFAULT_MONTHLY_GOAL: BigDecimal = BigDecimal("100000")
        private const val MAX_NOTIFIED_DEDUP_KEYS = 100
    }
}
