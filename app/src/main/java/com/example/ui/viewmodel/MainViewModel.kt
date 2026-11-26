package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.EventRouteResolver
import com.example.domain.rules.EventChainPlanner
import com.example.data.local.LoginHistoryStore
import com.example.data.port.AccountDeletionPort
import com.example.data.port.ActivityFinancePort
import com.example.data.port.ActivityRepository
import com.example.data.port.AppThemeMode
import com.example.data.port.AuthRepository
import com.example.data.port.BankAccountRepository
import com.example.data.port.BrandProfilePort
import com.example.data.port.ConnectivityPort
import com.example.data.port.DepartureAlarmPort
import com.example.data.port.GeoPoint
import com.example.data.port.GroqAiPort
import com.example.data.port.LoginHistoryRepository
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.OfflineCachePort
import com.example.data.port.PendingSyncOpType
import com.example.data.port.PlacePrediction
import com.example.data.port.PrivacyPreferences
import com.example.data.port.PrivacyPreferencesPort
import com.example.data.port.ResolvedPlace
import com.example.data.port.SettingsRepository
import com.example.data.port.SupplierRepository
import com.example.data.port.VenueRepository
import com.example.data.port.WidgetSyncPort
import com.example.domain.model.Activity
import com.example.domain.model.ActivityDraft
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.AiAnalysisPreset
import com.example.domain.model.AiChatMessage
import com.example.domain.model.AppError
import com.example.domain.model.ArtistBrandProfile
import com.example.domain.model.AuthSession
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountDraft
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.Currency
import com.example.domain.model.GroqAiSettings
import com.example.domain.model.GroqModelOption
import com.example.domain.model.LabSettings
import com.example.domain.model.LoginSessionEvent
import com.example.domain.model.PaymentStatus
import com.example.domain.model.ScheduledDeparture
import com.example.domain.model.SignUpOutcome
import com.example.domain.model.Supplier
import com.example.domain.model.Venue
import com.example.domain.rules.AccountStatement
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.ActivityFinanceCalculator
import com.example.domain.rules.AuthInputValidator
import com.example.domain.rules.DepartureTimeCalculator
import com.example.domain.rules.Balance12Months
import com.example.domain.rules.BalanceAndOverviewCalculator
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.FrequentSupplier
import com.example.domain.rules.GroqAnalyticsContextBuilder
import com.example.domain.rules.MonthOverview
import com.example.domain.rules.NameNormalizer
import com.example.domain.rules.NetProfitSummary12Months
import com.example.domain.rules.ReceivablesCalculator
import com.example.domain.rules.ReceivablesSummary
import com.example.domain.rules.SavedVenueSuggestion
import com.example.domain.rules.ScheduleConflictChecker
import com.example.data.remote.SupabaseAuthErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

sealed interface AuthUiState {
    data object Checking : AuthUiState
    data class SignedOut(
        val isConfigured: Boolean,
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null,
        val infoMessage: String? = null,
        val unconfirmedEmail: String? = null,
        val resendCooldownSeconds: Int = 0,
        val isResendingConfirmation: Boolean = false,
        val signUpConfirmationSentCount: Int = 0,
    ) : AuthUiState

    data class SignedIn(val session: AuthSession) : AuthUiState
}

data class AppDataState(
    val today: LocalDate = DominicanFormatter.todaySantoDomingo(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val activities: List<Activity> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val bankAccountsFeaturePending: Boolean = false,
    val loginHistory: List<LoginSessionEvent> = emptyList(),
    val venues: List<Venue> = emptyList(),
    val cachedPhotonVenues: List<Venue> = emptyList(),
    val venuesFeaturePending: Boolean = false,
    val settings: LabSettings = LabSettings(),
    val scheduledDepartures: Map<String, ScheduledDeparture> = emptyMap(),
    val activityRoutes: Map<String, CachedActivityRoute> = emptyMap(),
    val activityTimings: Map<String, ActivityTimelineOverride> = emptyMap(),
    val activityFinancialRecords: Map<String, ActivityFinancialRecord> = emptyMap(),
    val contingencyUnderControlIds: Set<String> = emptySet(),
    val privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    val canScheduleExactAlarms: Boolean = true,
    val isRecalculatingRoutes: Boolean = false,
    val displayedMonth: YearMonth = YearMonth.from(DominicanFormatter.todaySantoDomingo()),
    val selectedAgendaDate: LocalDate = DominicanFormatter.todaySantoDomingo(),
    val selectedReceivableGroupIds: Set<String> = emptySet(),
    val selectedBalanceMonth: YearMonth? = null,
    val reportsMonth: YearMonth = YearMonth.from(DominicanFormatter.todaySantoDomingo()),
    val isOnline: Boolean = true,
    val isOfflineCacheActive: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val pendingSyncCount: Int = 0,
    val isSyncingPending: Boolean = false,
    val brandProfile: ArtistBrandProfile = ArtistBrandProfile(),
    val aiSettings: GroqAiSettings = GroqAiSettings(),
    val aiMessages: List<AiChatMessage> = emptyList(),
    val isAiAnalyzing: Boolean = false,
    val aiErrorMessage: String? = null,
) {
    val monthOverview: MonthOverview by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BalanceAndOverviewCalculator.monthOverview(
            activities = activities,
            today = today,
            monthlyGoal = settings.monthlyGoal,
            defaultCurrency = settings.defaultCurrency,
            financialRecords = activityFinancialRecords,
            convertMixedCurrencies = privacyPreferences.convertMixedCurrencies,
            defaultUsdToDopRate = privacyPreferences.defaultUsdToDopRate,
        )
    }

    val receivablesSummary: ReceivablesSummary by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ReceivablesCalculator.calculate(
            activities = activities,
            today = today,
            defaultCurrency = settings.defaultCurrency,
            financialRecords = activityFinancialRecords,
        )
    }

    val balance12Months: Balance12Months by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BalanceAndOverviewCalculator.balance12Months(
            activities = activities,
            today = today,
            monthlyGoal = settings.monthlyGoal,
            defaultCurrency = settings.defaultCurrency,
            financialRecords = activityFinancialRecords,
            convertMixedCurrencies = privacyPreferences.convertMixedCurrencies,
            defaultUsdToDopRate = privacyPreferences.defaultUsdToDopRate,
        )
    }

    val netProfit12Months: NetProfitSummary12Months by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ActivityFinanceCalculator.summarize12Months(
            activities = activities,
            financialRecords = activityFinancialRecords,
            today = today,
            defaultCurrency = settings.defaultCurrency,
            convertMixedCurrencies = privacyPreferences.convertMixedCurrencies,
            defaultUsdToDopRate = privacyPreferences.defaultUsdToDopRate,
        )
    }

    val frequentSuppliers: List<FrequentSupplier> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BalanceAndOverviewCalculator.frequentSuppliers(
            activities = activities,
            today = today,
            limit = 3,
        )
    }

    val venueSuggestions: List<SavedVenueSuggestion> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BalanceAndOverviewCalculator.venueSuggestions(
            activities = activities,
            today = today,
        )
    }

    val upcomingActivities: List<Activity> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val currentMonth = YearMonth.from(today)
        activities
            .filter { !it.eventDate.isBefore(today) && YearMonth.from(it.eventDate) == currentMonth }
            .sortedWith(compareBy<Activity> { it.eventDate }.thenBy { it.eventTime })
    }

    val freeWeekendsInDisplayedMonth: List<LocalDate> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BalanceAndOverviewCalculator.freeWeekendDays(
            activities = activities,
            month = displayedMonth,
            today = today,
        )
    }

    val activitiesByDate: Map<LocalDate, List<Activity>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        activities
            .groupBy { it.eventDate }
            .mapValues { (_, list) -> list.sortedBy { it.eventTime } }
    }

    val selectedDayActivities: List<Activity> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        activitiesByDate[selectedAgendaDate].orEmpty()
    }

    val supplierResolution by lazy(LazyThreadSafetyMode.PUBLICATION) {
        NameNormalizer.buildSupplierResolution(activities.map { it.supplierName } + suppliers.map { it.name })
    }

    val venueResolution by lazy(LazyThreadSafetyMode.PUBLICATION) {
        NameNormalizer.buildVenueResolution(activities.map { it.venueName })
    }

    /**
     * Merges Supabase saved venues with locally cached frequent Photon hotels (deduplicated
     * by placeId and normalized name) so repeat hotel selections never need another Photon query.
     */
    val allSavedVenues: List<Venue> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val seenPlaceIds = mutableSetOf<String>()
        val seenNameKeys = mutableSetOf<String>()
        val merged = mutableListOf<Venue>()
        (venues + cachedPhotonVenues).forEach { v ->
            val pid = v.placeId?.trim()?.takeIf { it.isNotEmpty() }
            val nameKey = NameNormalizer.normalizeKey(v.name)
            if ((pid != null && pid in seenPlaceIds) || (nameKey.isNotEmpty() && nameKey in seenNameKeys)) {
                return@forEach
            }
            if (pid != null) seenPlaceIds.add(pid)
            if (nameKey.isNotEmpty()) seenNameKeys.add(nameKey)
            merged.add(v)
        }
        merged
    }
}

class MainViewModel(
    private val authRepository: AuthRepository,
    private val activityRepository: ActivityRepository,
    private val supplierRepository: SupplierRepository,
    private val settingsRepository: SettingsRepository,
    private val venueRepository: VenueRepository,
    private val mapsAndLocationPort: MapsAndLocationPort,
    private val departureAlarmPort: DepartureAlarmPort,
    private val bankAccountRepository: BankAccountRepository? = null,
    private val loginHistoryRepository: LoginHistoryRepository? = null,
    private val privacyPreferencesPort: PrivacyPreferencesPort? = null,
    private val activityFinancePort: ActivityFinancePort? = null,
    private val offlineCachePort: OfflineCachePort? = null,
    private val connectivityPort: ConnectivityPort? = null,
    private val brandProfilePort: BrandProfilePort? = null,
    private val accountDeletionPort: AccountDeletionPort? = null,
    private val widgetSyncPort: WidgetSyncPort? = null,
    private val groqAiPort: GroqAiPort? = null,
    private val todayProvider: () -> LocalDate = { DominicanFormatter.todaySantoDomingo() },
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Checking)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private var resendCooldownJob: Job? = null
    private var pendingSyncObserverJob: Job? = null
    private var signUpConfirmationSeq: Int = 0
    private var lastKnownOnline: Boolean = connectivityPort?.checkCurrentlyOnline() ?: true

    private val _dataState = MutableStateFlow(
        AppDataState(
            today = todayProvider(),
            canScheduleExactAlarms = departureAlarmPort.canScheduleExactAlarms(),
            displayedMonth = YearMonth.from(todayProvider()),
            selectedAgendaDate = todayProvider(),
            reportsMonth = YearMonth.from(todayProvider()),
            isOnline = lastKnownOnline,
        )
    )
    val dataState: StateFlow<AppDataState> = _dataState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.restoreSession()
        }
        viewModelScope.launch {
            departureAlarmPort.observeScheduledDepartures().collect { scheduled ->
                _dataState.update { it.copy(scheduledDepartures = scheduled) }
                if (currentOwnerId() != null) {
                    pushWidgetUpdate()
                }
            }
        }
        viewModelScope.launch {
            departureAlarmPort.observeActivityRoutes().collect { routes ->
                _dataState.update { it.copy(activityRoutes = routes) }
            }
        }
        viewModelScope.launch {
            departureAlarmPort.observeActivityTimings().collect { timings ->
                _dataState.update { it.copy(activityTimings = timings) }
            }
        }
        viewModelScope.launch {
            departureAlarmPort.observeContingencyUnderControlIds().collect { ids ->
                _dataState.update { it.copy(contingencyUnderControlIds = ids) }
            }
        }
        viewModelScope.launch {
            mapsAndLocationPort.observeCachedPlaces().collect { cached ->
                _dataState.update { it.copy(cachedPhotonVenues = cached) }
            }
        }
        viewModelScope.launch {
            loginHistoryRepository?.observeLoginHistory()?.collect { history ->
                _dataState.update { it.copy(loginHistory = history) }
            }
        }
        viewModelScope.launch {
            privacyPreferencesPort?.observePrivacyPreferences()?.collect { privacy ->
                _dataState.update { it.copy(privacyPreferences = privacy) }
            }
        }
        viewModelScope.launch {
            activityFinancePort?.observeFinancialRecords()?.collect { records ->
                _dataState.update { it.copy(activityFinancialRecords = records) }
            }
        }
        viewModelScope.launch {
            brandProfilePort?.observeBrandProfile()?.collect { profile ->
                _dataState.update { it.copy(brandProfile = profile) }
            }
        }
        viewModelScope.launch {
            groqAiPort?.observeAiSettings()?.collect { aiSettings ->
                _dataState.update { it.copy(aiSettings = aiSettings) }
            }
        }
        viewModelScope.launch {
            connectivityPort?.isOnline?.collect { online ->
                val cameBackOnline = !lastKnownOnline && online
                lastKnownOnline = online
                _dataState.update { it.copy(isOnline = online) }
                if (cameBackOnline) {
                    val ownerId = currentOwnerId()
                    if (ownerId != null) {
                        syncNow(ownerId)
                    }
                }
            }
        }
        viewModelScope.launch {
            authRepository.isInitialized.collect { ready ->
                if (!ready) return@collect
                val session = authRepository.sessionFlow.value
                if (session != null) {
                    _authState.value = AuthUiState.SignedIn(session)
                    startObservingPendingSync(session.userId)
                    refreshAll(session.userId)
                } else {
                    _authState.value = AuthUiState.SignedOut(
                        isConfigured = authRepository.isConfigured(),
                        errorMessage = authRepository.sessionExpiredMessageFlow.value,
                    )
                }
            }
        }
        viewModelScope.launch {
            authRepository.sessionFlow.collect { session ->
                if (!authRepository.isInitialized.value) return@collect
                if (session != null) {
                    val wasSignedIn = _authState.value is AuthUiState.SignedIn
                    _authState.value = AuthUiState.SignedIn(session)
                    if (!wasSignedIn) {
                        startObservingPendingSync(session.userId)
                        refreshAll(session.userId)
                    }
                } else {
                    pendingSyncObserverJob?.cancel()
                    pendingSyncObserverJob = null
                    widgetSyncPort?.clearNextGigWidget()
                    _authState.value = AuthUiState.SignedOut(
                        isConfigured = authRepository.isConfigured(),
                        errorMessage = authRepository.sessionExpiredMessageFlow.value,
                    )
                    _dataState.update { prev ->
                        AppDataState(
                            today = todayProvider(),
                            canScheduleExactAlarms = departureAlarmPort.canScheduleExactAlarms(),
                            displayedMonth = YearMonth.from(todayProvider()),
                            selectedAgendaDate = todayProvider(),
                            reportsMonth = YearMonth.from(todayProvider()),
                            isOnline = connectivityPort?.checkCurrentlyOnline() ?: true,
                            privacyPreferences = prev.privacyPreferences,
                            brandProfile = prev.brandProfile,
                            aiSettings = prev.aiSettings,
                        )
                    }
                }
            }
        }
    }

    private fun startObservingPendingSync(ownerId: String) {
        pendingSyncObserverJob?.cancel()
        val cache = offlineCachePort ?: return
        pendingSyncObserverJob = viewModelScope.launch {
            cache.observePendingSyncCount(ownerId).collect { count ->
                _dataState.update { it.copy(pendingSyncCount = count) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!AuthInputValidator.isValidEmail(email) || password.isBlank()) {
            _authState.value = AuthUiState.SignedOut(
                isConfigured = authRepository.isConfigured(),
                errorMessage = "Ingresa un correo y contraseña válidos.",
            )
            return
        }
        authRepository.clearSessionExpiredMessage()
        viewModelScope.launch {
            _authState.value = AuthUiState.SignedOut(
                isConfigured = authRepository.isConfigured(),
                isSubmitting = true,
            )
            val result = authRepository.signIn(email, password)
            result.onSuccess { session ->
                resendCooldownJob?.cancel()
                launch {
                    loginHistoryRepository?.recordEvent(
                        userId = session.userId,
                        email = session.email,
                        eventType = LoginHistoryStore.EVENT_LOGIN,
                        success = true,
                    )
                }
            }.onFailure { err ->
                launch {
                    loginHistoryRepository?.recordEvent(
                        userId = "",
                        email = email.trim(),
                        eventType = LoginHistoryStore.EVENT_LOGIN,
                        success = false,
                    )
                }
                val isUnconfirmed = err is AppError.EmailNotConfirmed
                val remaining = if (isUnconfirmed) authRepository.remainingResendCooldownSeconds() else 0
                _authState.value = AuthUiState.SignedOut(
                    isConfigured = authRepository.isConfigured(),
                    isSubmitting = false,
                    errorMessage = err.message ?: SupabaseAuthErrorMapper.MSG_GENERIC_AUTH,
                    unconfirmedEmail = if (isUnconfirmed) email.trim() else null,
                    resendCooldownSeconds = remaining,
                )
                if (remaining > 0) startResendCooldownTicker()
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        confirmPassword: String,
        fullName: String?,
    ) {
        val validationError = AuthInputValidator.validateSignUp(email, password, confirmPassword)
        if (validationError != null) {
            _authState.value = AuthUiState.SignedOut(
                isConfigured = authRepository.isConfigured(),
                errorMessage = validationError,
            )
            return
        }
        authRepository.clearSessionExpiredMessage()
        viewModelScope.launch {
            _authState.value = AuthUiState.SignedOut(
                isConfigured = authRepository.isConfigured(),
                isSubmitting = true,
            )
            val result = authRepository.signUp(email, password, fullName)
            result.onSuccess { outcome ->
                when (outcome) {
                    is SignUpOutcome.Authenticated -> {
                        resendCooldownJob?.cancel()
                        launch {
                            loginHistoryRepository?.recordEvent(
                                userId = outcome.session.userId,
                                email = outcome.session.email,
                                eventType = LoginHistoryStore.EVENT_SIGNUP,
                                success = true,
                            )
                        }
                    }

                    is SignUpOutcome.EmailConfirmationRequired -> {
                        launch {
                            loginHistoryRepository?.recordEvent(
                                userId = "",
                                email = outcome.email,
                                eventType = LoginHistoryStore.EVENT_SIGNUP,
                                success = true,
                            )
                        }
                        signUpConfirmationSeq += 1
                        _authState.value = AuthUiState.SignedOut(
                            isConfigured = authRepository.isConfigured(),
                            isSubmitting = false,
                            infoMessage = SupabaseAuthErrorMapper.SIGNUP_CONFIRMATION_MESSAGE,
                            signUpConfirmationSentCount = signUpConfirmationSeq,
                        )
                    }
                }
            }.onFailure { err ->
                launch {
                    loginHistoryRepository?.recordEvent(
                        userId = "",
                        email = email.trim(),
                        eventType = LoginHistoryStore.EVENT_SIGNUP,
                        success = false,
                    )
                }
                _authState.value = AuthUiState.SignedOut(
                    isConfigured = authRepository.isConfigured(),
                    isSubmitting = false,
                    errorMessage = err.message ?: SupabaseAuthErrorMapper.MSG_GENERIC_AUTH,
                )
            }
        }
    }

    fun resendConfirmation(email: String) {
        val cleanEmail = email.trim()
        if (!AuthInputValidator.isValidEmail(cleanEmail)) {
            _authState.update { current ->
                (current as? AuthUiState.SignedOut)?.copy(
                    errorMessage = "Ingresa un correo electrónico válido.",
                ) ?: current
            }
            return
        }
        viewModelScope.launch {
            _authState.update { current ->
                (current as? AuthUiState.SignedOut)?.copy(
                    isResendingConfirmation = true,
                    errorMessage = null,
                ) ?: current
            }
            val result = authRepository.resendConfirmation(cleanEmail)
            val remaining = authRepository.remainingResendCooldownSeconds()
            result.onSuccess {
                _authState.update { current ->
                    (current as? AuthUiState.SignedOut)?.copy(
                        isResendingConfirmation = false,
                        unconfirmedEmail = cleanEmail,
                        resendCooldownSeconds = remaining,
                        infoMessage = SupabaseAuthErrorMapper.SIGNUP_CONFIRMATION_MESSAGE,
                        errorMessage = null,
                    ) ?: current
                }
                if (remaining > 0) startResendCooldownTicker()
            }.onFailure { err ->
                _authState.update { current ->
                    (current as? AuthUiState.SignedOut)?.copy(
                        isResendingConfirmation = false,
                        unconfirmedEmail = cleanEmail,
                        resendCooldownSeconds = remaining,
                        errorMessage = err.message ?: SupabaseAuthErrorMapper.MSG_GENERIC_AUTH,
                    ) ?: current
                }
                if (remaining > 0) startResendCooldownTicker()
            }
        }
    }

    private fun startResendCooldownTicker() {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            while (true) {
                val remaining = authRepository.remainingResendCooldownSeconds()
                _authState.update { current ->
                    (current as? AuthUiState.SignedOut)?.copy(
                        resendCooldownSeconds = remaining,
                    ) ?: current
                }
                if (remaining <= 0) break
                delay(1_000L)
            }
        }
    }

    fun clearLoginHistory() {
        viewModelScope.launch {
            loginHistoryRepository?.clearHistory()
            _dataState.update { it.copy(snackbarMessage = "Historial de inicios de sesión limpiado.") }
        }
    }

    fun resetPassword(email: String) {
        if (!AuthInputValidator.isValidEmail(email)) {
            _authState.value = AuthUiState.SignedOut(
                isConfigured = authRepository.isConfigured(),
                errorMessage = "Ingresa un correo electrónico válido.",
            )
            return
        }
        authRepository.clearSessionExpiredMessage()
        viewModelScope.launch {
            _authState.value = AuthUiState.SignedOut(
                isConfigured = authRepository.isConfigured(),
                isSubmitting = true,
            )
            val result = authRepository.resetPassword(email)
            result.onSuccess {
                _authState.value = AuthUiState.SignedOut(
                    isConfigured = authRepository.isConfigured(),
                    isSubmitting = false,
                    infoMessage = "${SupabaseAuthErrorMapper.RECOVER_NEUTRAL_MESSAGE} ${SupabaseAuthErrorMapper.RECOVER_BROWSER_HINT}",
                )
            }.onFailure { err ->
                _authState.value = AuthUiState.SignedOut(
                    isConfigured = authRepository.isConfigured(),
                    isSubmitting = false,
                    errorMessage = err.message ?: SupabaseAuthErrorMapper.MSG_GENERIC_AUTH,
                )
            }
        }
    }

    fun changePassword(
        newPassword: String,
        confirmPassword: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val validationError = AuthInputValidator.validatePasswordChange(newPassword, confirmPassword)
        if (validationError != null) {
            onResult(Result.failure(AppError.Validation(validationError)))
            return
        }
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = authRepository.updatePassword(newPassword)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update { it.copy(snackbarMessage = "Contraseña actualizada correctamente.") }
                onResult(Result.success(Unit))
            }.onFailure { err ->
                onResult(Result.failure(err))
            }
        }
    }

    fun signOut() {
        val ownerId = currentOwnerId()
        viewModelScope.launch {
            runCatching { departureAlarmPort.cancelAllScheduledAlarms() }
            if (ownerId != null) {
                runCatching { offlineCachePort?.clearForOwner(ownerId) }
            }
            runCatching { widgetSyncPort?.clearNextGigWidget() }
            authRepository.signOut()
        }
    }

    fun deleteAccount(onResult: (Result<Unit>) -> Unit) {
        val ownerId = currentOwnerId()
        if (ownerId == null) {
            onResult(Result.failure(AppError.Unauthorized("Sesión inválida para eliminar la cuenta.")))
            return
        }
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val cloudResult = accountDeletionPort?.deleteAllUserCloudData(ownerId) ?: Result.success(Unit)
            if (cloudResult.isFailure) {
                _dataState.update { it.copy(isMutating = false) }
                onResult(cloudResult)
                return@launch
            }
            runCatching { departureAlarmPort.cancelAllScheduledAlarms() }
            runCatching { offlineCachePort?.clearForOwner(ownerId) }
            runCatching { activityFinancePort?.clearAll() }
            runCatching { brandProfilePort?.clearBrandProfile() }
            runCatching { groqAiPort?.clearApiKey() }
            runCatching { loginHistoryRepository?.clearHistory() }
            runCatching { widgetSyncPort?.clearNextGigWidget() }
            authRepository.signOut()
            _dataState.update { it.copy(isMutating = false) }
            onResult(Result.success(Unit))
        }
    }

    private fun pushWidgetUpdate() {
        val state = _dataState.value
        runCatching {
            widgetSyncPort?.updateNextGigWidget(
                activities = state.activities,
                departures = state.scheduledDepartures,
                today = state.today,
                venueDisplay = { state.venueResolution.displayName(it) },
                supplierDisplay = { state.supplierResolution.displayName(it) },
            )
        }
    }

    fun syncNow(explicitOwnerId: String? = null) {
        val ownerId = explicitOwnerId ?: currentOwnerId() ?: return
        refreshAll(ownerId)
    }

    private suspend fun flushPendingSyncQueue(ownerId: String): Int {
        val cache = offlineCachePort ?: return 0
        val ops = runCatching { cache.getPendingOperations(ownerId) }.getOrDefault(emptyList())
        if (ops.isEmpty()) return 0

        _dataState.update { it.copy(isSyncingPending = true) }
        var syncedCount = 0
        for (op in ops) {
            val res: Result<Unit> = when (op.opType) {
                PendingSyncOpType.MARK_PAID ->
                    activityRepository.markActivitiesPaid(ownerId, op.activityIds)
                PendingSyncOpType.MARK_PENDING -> {
                    val target = _dataState.value.activities.firstOrNull { it.id == op.targetId }
                    if (target != null) {
                        val forcedPaid = target.copy(paymentStatus = PaymentStatus.PAGADO)
                        activityRepository.togglePaymentStatus(ownerId, forcedPaid).map { Unit }
                    } else {
                        Result.success(Unit)
                    }
                }
            }
            if (res.isSuccess) {
                runCatching { cache.removePendingOperation(op.id) }
                syncedCount++
            } else if (res.exceptionOrNull() is AppError.Network) {
                break
            }
        }
        _dataState.update { it.copy(isSyncingPending = false) }
        return syncedCount
    }

    fun refreshAll(explicitOwnerId: String? = null) {
        val ownerId = explicitOwnerId ?: (authState.value as? AuthUiState.SignedIn)?.session?.userId ?: return
        viewModelScope.launch {
            val today = todayProvider()
            val currentlyOnline = connectivityPort?.checkCurrentlyOnline() ?: true

            // Instant local Room cache hydration on cold start / offline launch
            var hydratedSnapshotHasData = _dataState.value.activities.isNotEmpty() || _dataState.value.lastSyncedAtMillis != null
            if (_dataState.value.activities.isEmpty() && offlineCachePort != null) {
                val cached = runCatching { offlineCachePort.loadSnapshot(ownerId) }.getOrNull()
                if (cached != null && cached.hasData) {
                    hydratedSnapshotHasData = true
                    _dataState.update { state ->
                        state.copy(
                            activities = cached.activities,
                            suppliers = cached.suppliers,
                            venues = cached.venues,
                            bankAccounts = cached.bankAccounts,
                            lastSyncedAtMillis = cached.lastSyncedAtMillis,
                            isOfflineCacheActive = !currentlyOnline,
                        )
                    }
                }
            }

            _dataState.update {
                it.copy(
                    today = today,
                    isOnline = currentlyOnline,
                    isLoading = currentlyOnline,
                    errorMessage = null,
                    canScheduleExactAlarms = departureAlarmPort.canScheduleExactAlarms(),
                )
            }

            if (!currentlyOnline && hydratedSnapshotHasData) {
                _dataState.update { it.copy(isLoading = false, isOfflineCacheActive = true) }
                pushWidgetUpdate()
                return@launch
            }

            // Flush any queued offline mutations before pulling fresh remote snapshot
            val flushedCount = if (currentlyOnline) flushPendingSyncQueue(ownerId) else 0

            val activitiesDeferred = async { activityRepository.fetchAllActivities(ownerId) }
            val suppliersDeferred = async { supplierRepository.fetchSuppliers(ownerId) }
            val settingsDeferred = async { settingsRepository.fetchSettings(ownerId) }
            val venuesDeferred = async { venueRepository.fetchVenues(ownerId) }
            val bankAccountsDeferred = async {
                bankAccountRepository?.fetchBankAccounts(ownerId) ?: Result.success(emptyList())
            }

            val activitiesResult = activitiesDeferred.await()
            val suppliersResult = suppliersDeferred.await()
            val settingsResult = settingsDeferred.await()
            val venuesResult = venuesDeferred.await()
            val bankAccountsResult = bankAccountsDeferred.await()

            val firstError = activitiesResult.exceptionOrNull()
                ?: suppliersResult.exceptionOrNull()
                ?: settingsResult.exceptionOrNull()

            if (firstError is AppError.Unauthorized && firstError.refreshTokenRejected) {
                return@launch
            }

            val venuesPending = venuesResult.exceptionOrNull() is AppError.ServerFeaturePending
            val bankAccountsPending = bankAccountsResult.exceptionOrNull() is AppError.ServerFeaturePending
            val mergedActivities = activitiesResult.getOrDefault(_dataState.value.activities)
            val mergedSuppliers = suppliersResult.getOrDefault(_dataState.value.suppliers)
            val mergedVenues = venuesResult.getOrDefault(_dataState.value.venues)
            val mergedBankAccounts = bankAccountsResult.getOrDefault(_dataState.value.bankAccounts)
            val mergedSettings = settingsResult.getOrDefault(_dataState.value.settings)

            val networkFailed = firstError is AppError.Network
            val fallbackToOfflineCache = networkFailed && (hydratedSnapshotHasData || mergedActivities.isNotEmpty())
            val syncedNowMillis = if (activitiesResult.isSuccess) System.currentTimeMillis() else null

            if (syncedNowMillis != null) {
                runCatching {
                    offlineCachePort?.saveSnapshot(
                        ownerId = ownerId,
                        activities = mergedActivities,
                        suppliers = mergedSuppliers,
                        venues = mergedVenues,
                        bankAccounts = mergedBankAccounts,
                        syncedAtMillis = syncedNowMillis,
                    )
                }
            }

            _dataState.update { state ->
                state.copy(
                    isLoading = false,
                    isOnline = if (networkFailed) false else (connectivityPort?.checkCurrentlyOnline() ?: true),
                    isOfflineCacheActive = fallbackToOfflineCache,
                    lastSyncedAtMillis = syncedNowMillis ?: state.lastSyncedAtMillis,
                    activities = mergedActivities,
                    suppliers = mergedSuppliers,
                    bankAccounts = mergedBankAccounts,
                    bankAccountsFeaturePending = bankAccountsPending,
                    venues = mergedVenues,
                    venuesFeaturePending = venuesPending,
                    settings = mergedSettings,
                    errorMessage = if (fallbackToOfflineCache) null else firstError?.message,
                    snackbarMessage = if (flushedCount > 0) {
                        if (flushedCount == 1) "1 cambio pendiente sincronizado."
                        else "$flushedCount cambios pendientes sincronizados."
                    } else {
                        state.snackbarMessage
                    },
                )
            }

            pushWidgetUpdate()
            syncDeparturesInternal(
                activities = mergedActivities,
                settings = mergedSettings,
                forceRecalculateRoutes = false,
            )
        }
    }

    fun clearSnackbar() {
        _dataState.update { it.copy(snackbarMessage = null) }
    }

    fun clearError() {
        _dataState.update { it.copy(errorMessage = null) }
    }

    // Agenda navigation
    fun selectAgendaDate(date: LocalDate) {
        _dataState.update {
            it.copy(
                selectedAgendaDate = date,
                displayedMonth = YearMonth.from(date),
            )
        }
    }

    fun navigateAgendaMonth(deltaMonths: Long) {
        _dataState.update { state ->
            val nextMonth = state.displayedMonth.plusMonths(deltaMonths)
            val nextSelected = if (nextMonth == YearMonth.from(state.today)) {
                state.today
            } else {
                nextMonth.atDay(1)
            }
            state.copy(
                displayedMonth = nextMonth,
                selectedAgendaDate = nextSelected,
            )
        }
    }

    fun selectBalanceMonth(month: YearMonth?) {
        _dataState.update { it.copy(selectedBalanceMonth = month) }
    }

    fun navigateReportsMonth(deltaMonths: Long) {
        _dataState.update { it.copy(reportsMonth = it.reportsMonth.plusMonths(deltaMonths)) }
    }

    // Domain helpers for UI form
    fun usualRateFor(supplierName: String, currency: Currency): BigDecimal? =
        BalanceAndOverviewCalculator.usualRate(_dataState.value.activities, supplierName, currency)

    fun checkLocalConflict(
        date: LocalDate,
        time: LocalTime?,
        excludeActivityId: String? = null,
        excludeSeriesId: String? = null,
        candidateEventMinutes: Int? = null,
    ): Activity? = ScheduleConflictChecker.findConflict(
        candidateDate = date,
        candidateTime = time,
        existingActivities = _dataState.value.activities,
        settings = _dataState.value.settings,
        excludeActivityId = excludeActivityId,
        excludeSeriesId = excludeSeriesId,
        candidateEventMinutes = candidateEventMinutes,
        activityEventMinutesProvider = { _dataState.value.activityTimings[it.id]?.eventMinutes },
    )

    fun setContingencyUnderControl(activityId: String, underControl: Boolean) {
        viewModelScope.launch {
            departureAlarmPort.setContingencyUnderControl(activityId, underControl)
            _dataState.update {
                it.copy(
                    snackbarMessage = if (underControl) {
                        "Confirmado: todo bajo control. Modo Contingencia silenciado."
                    } else {
                        "Modo Contingencia reactivado para esta actividad."
                    }
                )
            }
        }
    }

    fun findSupplierPhone(supplierDisplayName: String): String? {
        val targetKey = NameNormalizer.normalizeKey(supplierDisplayName)
        if (targetKey.isEmpty()) return null
        return _dataState.value.suppliers.firstOrNull {
            NameNormalizer.normalizeKey(it.name) == targetKey && !it.phone.isNullOrBlank()
        }?.phone
    }

    // Activity CRUD
    fun saveNewActivity(
        draft: ActivityDraft,
        repeatWeeks: Int,
        cachedRoute: CachedActivityRoute? = null,
        timingOverride: ActivityTimelineOverride? = null,
        financialRecord: ActivityFinancialRecord? = null,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val ownerId = currentOwnerId() ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val createdIdsResult: Result<List<String>> = if (repeatWeeks > 1) {
                activityRepository.createWeeklySeries(ownerId, draft, repeatWeeks).map { list -> list.map { it.id } }
            } else {
                activityRepository.createActivity(ownerId, draft).map { listOf(it.id) }
            }
            _dataState.update { it.copy(isMutating = false) }
            createdIdsResult.onSuccess { createdIds ->
                if (cachedRoute != null && draft.latitude != null && draft.longitude != null) {
                    createdIds.forEach { newId ->
                        departureAlarmPort.saveCachedRouteForActivity(
                            activityId = newId,
                            route = cachedRoute.copy(activityId = newId),
                        )
                    }
                }
                if (timingOverride != null && timingOverride.hasAnyOverride) {
                    createdIds.forEach { newId ->
                        departureAlarmPort.saveActivityTimingOverride(
                            activityId = newId,
                            override = timingOverride.copy(activityId = newId),
                        )
                    }
                }
                if (financialRecord != null && !financialRecord.isEmpty) {
                    createdIds.firstOrNull()?.let { firstId ->
                        activityFinancePort?.saveFinancialRecord(financialRecord.copy(activityId = firstId))
                    }
                }
                _dataState.update {
                    it.copy(
                        selectedAgendaDate = draft.eventDate,
                        displayedMonth = YearMonth.from(draft.eventDate),
                        snackbarMessage = if (repeatWeeks > 1) {
                            "Serie de $repeatWeeks actividades guardada."
                        } else {
                            "Actividad guardada."
                        },
                    )
                }
                refreshAll(ownerId)
            }
            onResult(createdIdsResult.map { Unit })
        }
    }

    fun updateExistingActivity(
        activityId: String,
        seriesId: String?,
        updateWholeSeries: Boolean,
        draft: ActivityDraft,
        cachedRoute: CachedActivityRoute? = null,
        timingOverride: ActivityTimelineOverride? = null,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val ownerId = currentOwnerId() ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val updatedIdsResult: Result<List<String>> = if (updateWholeSeries && !seriesId.isNullOrBlank()) {
                activityRepository.updateSeries(ownerId, seriesId, draft).map { list -> list.map { it.id } }
            } else {
                activityRepository.updateActivity(ownerId, activityId, draft).map { listOf(it.id) }
            }
            _dataState.update { it.copy(isMutating = false) }
            updatedIdsResult.onSuccess { updatedIds ->
                if (cachedRoute != null && draft.latitude != null && draft.longitude != null) {
                    updatedIds.forEach { id ->
                        departureAlarmPort.saveCachedRouteForActivity(
                            activityId = id,
                            route = cachedRoute.copy(activityId = id),
                        )
                    }
                }
                updatedIds.forEach { id ->
                    departureAlarmPort.saveActivityTimingOverride(
                        activityId = id,
                        override = timingOverride?.takeIf { it.hasAnyOverride }?.copy(activityId = id),
                    )
                }
                _dataState.update { it.copy(snackbarMessage = "Cambios guardados.") }
                refreshAll(ownerId)
            }
            onResult(updatedIdsResult.map { Unit })
        }
    }

    fun saveActivityTimingOverride(
        activityId: String,
        override: ActivityTimelineOverride?,
        showSnackbar: Boolean = true,
    ) {
        viewModelScope.launch {
            departureAlarmPort.saveActivityTimingOverride(
                activityId = activityId,
                override = override?.takeIf { it.hasAnyOverride }?.copy(activityId = activityId),
            )
            val state = _dataState.value
            syncDeparturesInternal(
                activities = state.activities,
                settings = state.settings,
                forceRecalculateRoutes = false,
            )
            if (showSnackbar) {
                _dataState.update {
                    it.copy(
                        snackbarMessage = if (override?.hasAnyOverride == true) {
                            "Tiempos y alarmas de esta actividad actualizados."
                        } else {
                            "Se restauraron los tiempos y alarmas predeterminados."
                        }
                    )
                }
            }
        }
    }

    fun deleteActivity(
        activityId: String,
        seriesId: String?,
        deleteWholeSeries: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val ownerId = currentOwnerId() ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = if (deleteWholeSeries && !seriesId.isNullOrBlank()) {
                activityRepository.deleteSeries(ownerId, seriesId)
            } else {
                activityRepository.deleteActivity(ownerId, activityId)
            }
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                if (!deleteWholeSeries) {
                    departureAlarmPort.cancelForActivity(activityId)
                    activityFinancePort?.deleteFinancialRecord(activityId)
                }
                _dataState.update {
                    it.copy(
                        snackbarMessage = if (deleteWholeSeries) "Serie eliminada." else "Actividad eliminada."
                    )
                }
                refreshAll(ownerId)
            }
            onResult(result)
        }
    }

    fun togglePaymentStatus(activity: Activity) {
        val ownerId = currentOwnerId() ?: return
        val nextPaid = activity.paymentStatus != PaymentStatus.PAGADO
        val nextStatus = if (nextPaid) PaymentStatus.PAGADO else PaymentStatus.PENDIENTE
        val nextPaidAt = if (nextPaid) todayProvider().toString() else null

        viewModelScope.launch {
            val onlineNow = connectivityPort?.checkCurrentlyOnline() ?: true
            if (!onlineNow && offlineCachePort != null) {
                val updated = activity.copy(paymentStatus = nextStatus, paidAt = nextPaidAt)
                offlineCachePort.enqueuePaymentStatusSync(
                    ownerId = ownerId,
                    activityIds = listOf(activity.id),
                    markAsPaid = nextPaid,
                    paidAtIso = nextPaidAt,
                )
                _dataState.update { state ->
                    state.copy(
                        isOnline = false,
                        isOfflineCacheActive = true,
                        activities = state.activities.map { if (it.id == updated.id) updated else it },
                        snackbarMessage = "Guardado sin conexión · se sincronizará al volver la señal.",
                    )
                }
                return@launch
            }

            _dataState.update { it.copy(isMutating = true) }
            val result = activityRepository.togglePaymentStatus(ownerId, activity)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess { updated ->
                _dataState.update { state ->
                    state.copy(
                        activities = state.activities.map { if (it.id == updated.id) updated else it },
                        snackbarMessage = "Estado de pago actualizado.",
                    )
                }
            }.onFailure { err ->
                if (err is AppError.Network && offlineCachePort != null) {
                    val updated = activity.copy(paymentStatus = nextStatus, paidAt = nextPaidAt)
                    offlineCachePort.enqueuePaymentStatusSync(
                        ownerId = ownerId,
                        activityIds = listOf(activity.id),
                        markAsPaid = nextPaid,
                        paidAtIso = nextPaidAt,
                    )
                    _dataState.update { state ->
                        state.copy(
                            isOfflineCacheActive = true,
                            activities = state.activities.map { if (it.id == updated.id) updated else it },
                            snackbarMessage = "Guardado sin conexión · se sincronizará al volver la señal.",
                        )
                    }
                } else {
                    _dataState.update { it.copy(snackbarMessage = err.message) }
                }
            }
        }
    }

    // Receivables bulk mark-paid
    fun toggleReceivableGroupSelection(groupId: String) {
        _dataState.update { state ->
            val next = if (groupId in state.selectedReceivableGroupIds) {
                state.selectedReceivableGroupIds - groupId
            } else {
                state.selectedReceivableGroupIds + groupId
            }
            state.copy(selectedReceivableGroupIds = next)
        }
    }

    fun markSelectedReceivablesPaid() {
        val ownerId = currentOwnerId() ?: return
        val selectedIds = _dataState.value.selectedReceivableGroupIds
        val groups = _dataState.value.receivablesSummary.groups.filter { it.groupId in selectedIds }
        val activityIds = groups.flatMap { it.activityIds }
        if (activityIds.isEmpty()) return
        val paidAtIso = todayProvider().toString()
        val idSet = activityIds.toSet()

        viewModelScope.launch {
            val onlineNow = connectivityPort?.checkCurrentlyOnline() ?: true
            if (!onlineNow && offlineCachePort != null) {
                offlineCachePort.enqueuePaymentStatusSync(
                    ownerId = ownerId,
                    activityIds = activityIds,
                    markAsPaid = true,
                    paidAtIso = paidAtIso,
                )
                _dataState.update { state ->
                    state.copy(
                        isOnline = false,
                        isOfflineCacheActive = true,
                        selectedReceivableGroupIds = emptySet(),
                        activities = state.activities.map {
                            if (it.id in idSet) it.copy(paymentStatus = PaymentStatus.PAGADO, paidAt = paidAtIso) else it
                        },
                        snackbarMessage = "Guardado sin conexión · se sincronizará al volver la señal.",
                    )
                }
                return@launch
            }

            _dataState.update { it.copy(isMutating = true) }
            val result = activityRepository.markActivitiesPaid(ownerId, activityIds)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update {
                    it.copy(
                        selectedReceivableGroupIds = emptySet(),
                        snackbarMessage = if (activityIds.size == 1) {
                            "1 toque marcado como pagado."
                        } else {
                            "${activityIds.size} toques marcados como pagados."
                        },
                    )
                }
                refreshAll(ownerId)
            }.onFailure { err ->
                if (err is AppError.Network && offlineCachePort != null) {
                    offlineCachePort.enqueuePaymentStatusSync(
                        ownerId = ownerId,
                        activityIds = activityIds,
                        markAsPaid = true,
                        paidAtIso = paidAtIso,
                    )
                    _dataState.update { state ->
                        state.copy(
                            isOfflineCacheActive = true,
                            selectedReceivableGroupIds = emptySet(),
                            activities = state.activities.map {
                                if (it.id in idSet) it.copy(paymentStatus = PaymentStatus.PAGADO, paidAt = paidAtIso) else it
                            },
                            snackbarMessage = "Guardado sin conexión · se sincronizará al volver la señal.",
                        )
                    }
                } else {
                    _dataState.update { it.copy(snackbarMessage = err.message) }
                }
            }
        }
    }

    // Suppliers CRUD
    fun saveSupplier(
        existing: Supplier?,
        name: String,
        phone: String?,
        email: String?,
        notes: String?,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val ownerId = currentOwnerId() ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = if (existing == null) {
                supplierRepository.createSupplier(ownerId, name, phone, email, notes).map { Unit }
            } else {
                supplierRepository.updateSupplier(
                    ownerId,
                    existing.copy(name = name, phone = phone, email = email, notes = notes),
                ).map { Unit }
            }
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update { it.copy(snackbarMessage = "Proveedor guardado.") }
                refreshAll(ownerId)
            }
            onResult(result)
        }
    }

    fun deleteSupplier(supplierId: String, onResult: (Result<Unit>) -> Unit) {
        val ownerId = currentOwnerId() ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = supplierRepository.deleteSupplier(ownerId, supplierId)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update { it.copy(snackbarMessage = "Proveedor eliminado.") }
                refreshAll(ownerId)
            }
            onResult(result)
        }
    }

    // Bank Accounts CRUD (Phase 3)
    fun saveBankAccount(
        existing: BankAccount?,
        draft: BankAccountDraft,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val ownerId = currentOwnerId() ?: return
        val repo = bankAccountRepository ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = if (existing == null) {
                repo.createBankAccount(ownerId, draft).map { Unit }
            } else {
                repo.updateBankAccount(ownerId, existing.id, draft).map { Unit }
            }
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update { it.copy(snackbarMessage = "Cuenta bancaria guardada.") }
                refreshAll(ownerId)
            }.onFailure { err ->
                _dataState.update { it.copy(snackbarMessage = err.message) }
            }
            onResult(result)
        }
    }

    fun setDefaultBankAccount(account: BankAccount) {
        val ownerId = currentOwnerId() ?: return
        val repo = bankAccountRepository ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = repo.setDefaultBankAccount(ownerId, account.id, account.currency)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update {
                    it.copy(snackbarMessage = "${account.label} ahora es tu cuenta principal en ${account.currency.code}.")
                }
                refreshAll(ownerId)
            }.onFailure { err ->
                _dataState.update { it.copy(snackbarMessage = err.message) }
            }
        }
    }

    fun deleteBankAccount(accountId: String, onResult: (Result<Unit>) -> Unit) {
        val ownerId = currentOwnerId() ?: return
        val repo = bankAccountRepository ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = repo.deleteBankAccount(ownerId, accountId)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess {
                _dataState.update { it.copy(snackbarMessage = "Cuenta bancaria eliminada.") }
                refreshAll(ownerId)
            }.onFailure { err ->
                _dataState.update { it.copy(snackbarMessage = err.message) }
            }
            onResult(result)
        }
    }

    fun buildAccountStatement(
        supplierName: String,
        currency: Currency,
        issuerFallback: String,
    ): Result<AccountStatement> = runCatching {
        val state = _dataState.value
        val effectiveIssuer = state.brandProfile.artistName.trim().ifBlank { issuerFallback }
        AccountStatementBuilder.build(
            supplierName = supplierName,
            currency = currency,
            activities = state.activities,
            bankAccounts = state.bankAccounts,
            today = state.today,
            issuerFallback = effectiveIssuer,
            financialRecords = state.activityFinancialRecords,
        )
    }

    fun saveBrandProfile(profile: ArtistBrandProfile, showSnackbar: Boolean = true) {
        _dataState.update {
            it.copy(
                brandProfile = profile,
                snackbarMessage = if (showSnackbar) "Marca personal guardada." else it.snackbarMessage,
            )
        }
        viewModelScope.launch {
            brandProfilePort?.saveBrandProfile(profile)
        }
    }

    fun saveActivityFinancialRecord(
        activityId: String,
        record: ActivityFinancialRecord,
        showSnackbar: Boolean = false,
    ) {
        if (activityId.isBlank()) return
        val normalized = record.copy(activityId = activityId)
        _dataState.update { state ->
            val updatedMap = if (normalized.isEmpty) {
                state.activityFinancialRecords - activityId
            } else {
                state.activityFinancialRecords + (activityId to normalized)
            }
            state.copy(
                activityFinancialRecords = updatedMap,
                snackbarMessage = if (showSnackbar) "Registro financiero actualizado." else state.snackbarMessage,
            )
        }
        viewModelScope.launch {
            activityFinancePort?.saveFinancialRecord(normalized)
        }
    }

    // Settings & inline monthly goal
    fun updateMonthlyGoal(newGoal: BigDecimal) {
        val ownerId = currentOwnerId() ?: return
        val updated = _dataState.value.settings.copy(monthlyGoal = newGoal)
        saveSettings(updated) {}
    }

    fun saveSettings(settings: LabSettings, onResult: (Result<Unit>) -> Unit) {
        val ownerId = currentOwnerId() ?: return
        viewModelScope.launch {
            _dataState.update { it.copy(isMutating = true) }
            val result = settingsRepository.saveSettings(ownerId, settings)
            _dataState.update { it.copy(isMutating = false) }
            result.onSuccess { saved ->
                _dataState.update {
                    it.copy(
                        settings = saved,
                        snackbarMessage = "Ajustes guardados.",
                    )
                }
                syncDeparturesInternal(
                    activities = _dataState.value.activities,
                    settings = saved,
                    forceRecalculateRoutes = false,
                )
            }.onFailure { err ->
                _dataState.update { it.copy(snackbarMessage = err.message) }
            }
            onResult(result.map { Unit })
        }
    }

    // OpenStreetMap Photon search, OSRM route estimate (cached per activity), saved venues & home location
    suspend fun searchPlaces(query: String): Result<List<PlacePrediction>> =
        mapsAndLocationPort.searchPlaces(query)

    suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace> =
        mapsAndLocationPort.fetchPlaceDetails(placeId)

    /**
     * Returns an existing cached OSRM route for the activity when coordinates match,
     * or fetches a fresh estimate from OSRM (falling back gracefully to offline Haversine).
     */
    suspend fun estimateRouteForDestination(
        destLat: Double,
        destLng: Double,
        existingActivityId: String? = null,
    ): CachedActivityRoute {
        val state = _dataState.value
        val resolver = EventRouteResolver(mapsAndLocationPort)
        val existing = state.activities.firstOrNull { it.id == existingActivityId }
        if (existing != null) {
            // The pin being edited may differ from the saved one.
            resolver.resolve(
                target = existing.copy(latitude = destLat, longitude = destLng),
                activities = state.activities,
                settings = state.settings,
                overrides = state.activityTimings,
                cached = state.activityRoutes[existing.id],
                forceRefresh = false,
            )?.let { return it }
        }

        // A new activity has no saved date or time yet, so it starts from home.
        val current = runCatching { mapsAndLocationPort.getLastKnownOrCurrentLocation() }.getOrNull()
        val origin = EventChainPlanner.fallbackOrigin(
            homeLatitude = state.settings.homeLatitude,
            homeLongitude = state.settings.homeLongitude,
            currentLatitude = current?.latitude,
            currentLongitude = current?.longitude,
        )
        return resolver.measure(
            activityId = existingActivityId.orEmpty(),
            origin = GeoPoint(origin.latitude, origin.longitude),
            destination = GeoPoint(destLat, destLng),
            cached = null,
        ).copy(originKind = origin.kind)
    }

    fun upsertVenue(venue: Venue, onResult: (Venue) -> Unit) {
        viewModelScope.launch {
            // Always persist selected hotel coordinates in local DataStore cache for instant reuse
            runCatching {
                mapsAndLocationPort.rememberSelectedPlace(
                    name = venue.name,
                    address = venue.address,
                    latitude = venue.latitude,
                    longitude = venue.longitude,
                    placeId = venue.placeId,
                )
            }
            val ownerId = currentOwnerId()
            if (ownerId == null) {
                onResult(venue)
                return@launch
            }
            val result = venueRepository.upsertVenue(ownerId, venue)
            result.onSuccess { saved ->
                _dataState.update { state ->
                    val existing = state.venues.filterNot {
                        (saved.id.isNotBlank() && it.id == saved.id) ||
                            (!saved.placeId.isNullOrBlank() && it.placeId == saved.placeId)
                    }
                    state.copy(
                        venues = (listOf(saved) + existing).sortedBy { it.name.lowercase() },
                        venuesFeaturePending = false,
                    )
                }
                onResult(saved)
            }.onFailure { err ->
                if (err is AppError.ServerFeaturePending) {
                    _dataState.update { it.copy(venuesFeaturePending = true) }
                }
                // Never block activity form: still return the picked coordinates.
                onResult(venue)
            }
        }
    }

    fun captureCurrentLocationAsHome(onResult: (Result<GeoPoint>) -> Unit) {
        viewModelScope.launch {
            val point = mapsAndLocationPort.getLastKnownOrCurrentLocation()
            if (point == null) {
                val err = AppError.Network("No se pudo obtener tu ubicación actual. Verifica el GPS y el permiso.")
                _dataState.update { it.copy(snackbarMessage = err.message) }
                onResult(Result.failure(err))
                return@launch
            }
            val updated = _dataState.value.settings.copy(
                homeLatitude = point.latitude,
                homeLongitude = point.longitude,
            )
            saveSettings(updated) { saveRes ->
                if (saveRes.isSuccess) {
                    onResult(Result.success(point))
                } else {
                    onResult(Result.failure(saveRes.exceptionOrNull() ?: AppError.Network("No se pudo guardar la ubicación.")))
                }
            }
        }
    }

    fun recalculateRoutesNow() {
        viewModelScope.launch {
            _dataState.update { it.copy(isRecalculatingRoutes = true) }
            syncDeparturesInternal(
                activities = _dataState.value.activities,
                settings = _dataState.value.settings,
                forceRecalculateRoutes = true,
            )
            _dataState.update {
                it.copy(
                    isRecalculatingRoutes = false,
                    snackbarMessage = "Distancias y horarios de salida actualizados.",
                )
            }
        }
    }

    fun refreshExactAlarmPermissionStatus() {
        val granted = departureAlarmPort.canScheduleExactAlarms()
        val previous = _dataState.value.canScheduleExactAlarms
        _dataState.update { it.copy(canScheduleExactAlarms = granted) }
        if (granted != previous && _dataState.value.activities.isNotEmpty()) {
            viewModelScope.launch {
                syncDeparturesInternal(
                    activities = _dataState.value.activities,
                    settings = _dataState.value.settings,
                    forceRecalculateRoutes = false,
                )
            }
        }
    }

    private suspend fun syncDeparturesInternal(
        activities: List<Activity>,
        settings: LabSettings,
        forceRecalculateRoutes: Boolean,
    ) = withContext(Dispatchers.IO) {
        val today = _dataState.value.today
        val receivables = ReceivablesCalculator.calculate(
            activities = activities,
            today = today,
            defaultCurrency = settings.defaultCurrency,
            financialRecords = _dataState.value.activityFinancialRecords,
        )
        val overdueSummary = if (receivables.overdueTotal > BigDecimal.ZERO) {
            DominicanFormatter.formatMoney(receivables.overdueTotal, receivables.defaultCurrency)
        } else {
            null
        }
        departureAlarmPort.syncUpcomingActivities(
            activities = activities,
            settings = settings,
            overdueSummaryText = overdueSummary,
            forceRecalculateRoutes = forceRecalculateRoutes,
        )
    }

    fun setScreenPrivacyEnabled(enabled: Boolean) {
        _dataState.update {
            it.copy(privacyPreferences = it.privacyPreferences.copy(screenPrivacyEnabled = enabled))
        }
        viewModelScope.launch {
            privacyPreferencesPort?.setScreenPrivacyEnabled(enabled)
        }
    }

    fun setResolveExternalIpInHistory(enabled: Boolean) {
        _dataState.update {
            it.copy(privacyPreferences = it.privacyPreferences.copy(resolveExternalIpInHistory = enabled))
        }
        viewModelScope.launch {
            privacyPreferencesPort?.setResolveExternalIpInHistory(enabled)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        _dataState.update {
            it.copy(privacyPreferences = it.privacyPreferences.copy(appLockEnabled = enabled))
        }
        viewModelScope.launch {
            privacyPreferencesPort?.setAppLockEnabled(enabled)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _dataState.update {
            it.copy(privacyPreferences = it.privacyPreferences.copy(themeMode = mode))
        }
        viewModelScope.launch {
            privacyPreferencesPort?.setThemeMode(mode)
        }
    }

    fun setDefaultUsdToDopRate(rate: BigDecimal) {
        if (rate <= BigDecimal.ZERO) return
        _dataState.update {
            it.copy(privacyPreferences = it.privacyPreferences.copy(defaultUsdToDopRate = rate))
        }
        viewModelScope.launch {
            privacyPreferencesPort?.setDefaultUsdToDopRate(rate)
        }
    }

    fun setConvertMixedCurrencies(enabled: Boolean) {
        _dataState.update {
            it.copy(privacyPreferences = it.privacyPreferences.copy(convertMixedCurrencies = enabled))
        }
        viewModelScope.launch {
            privacyPreferencesPort?.setConvertMixedCurrencies(enabled)
        }
    }

    // Groq AI Configuration & Analytics
    fun saveGroqApiKey(rawKey: String, onResult: (Result<Unit>) -> Unit) {
        val port = groqAiPort ?: return
        viewModelScope.launch {
            val result = port.saveApiKey(rawKey)
            result.onSuccess {
                _dataState.update { it.copy(snackbarMessage = "API Key de Groq guardada con cifrado local.") }
            }
            onResult(result)
        }
    }

    fun clearGroqApiKey() {
        val port = groqAiPort ?: return
        viewModelScope.launch {
            port.clearApiKey()
            _dataState.update {
                it.copy(
                    aiErrorMessage = null,
                    snackbarMessage = "API Key de Groq eliminada.",
                )
            }
        }
    }

    fun setGroqModel(model: GroqModelOption) {
        viewModelScope.launch {
            groqAiPort?.setSelectedModel(model)
        }
    }

    fun setGroqAnonymizeClients(enabled: Boolean) {
        viewModelScope.launch {
            groqAiPort?.setAnonymizeClientNames(enabled)
        }
    }

    fun testGroqConnection(overrideKey: String?, onResult: (Result<String>) -> Unit) {
        val port = groqAiPort ?: return
        viewModelScope.launch {
            val result = port.testConnection(overrideKey)
            onResult(result)
        }
    }

    fun clearAiConversation() {
        _dataState.update { it.copy(aiMessages = emptyList(), aiErrorMessage = null) }
    }

    fun runGroqPresetAnalysis(preset: AiAnalysisPreset, displayLabel: String) {
        sendGroqQuestion(promptToSend = preset.promptText, userBubbleLabel = displayLabel)
    }

    fun sendGroqQuestion(promptToSend: String, userBubbleLabel: String = promptToSend) {
        val port = groqAiPort ?: return
        val cleanPrompt = promptToSend.trim()
        if (cleanPrompt.isEmpty() || _dataState.value.isAiAnalyzing) return

        val stateSnapshot = _dataState.value
        val previousConversation = stateSnapshot.aiMessages
        val userMsg = AiChatMessage(
            id = "u_${System.nanoTime()}",
            isUser = true,
            content = userBubbleLabel.trim(),
        )
        _dataState.update {
            it.copy(
                aiMessages = it.aiMessages + userMsg,
                isAiAnalyzing = true,
                aiErrorMessage = null,
            )
        }

        viewModelScope.launch {
            val request = com.example.domain.model.GroqAgendaFinanceAnalysisRequest(
                activities = stateSnapshot.activities,
                routes = stateSnapshot.activityRoutes,
                scheduledDepartures = stateSnapshot.scheduledDepartures,
                financialRecords = stateSnapshot.activityFinancialRecords,
                settings = stateSnapshot.settings,
                privacyPreferences = stateSnapshot.privacyPreferences,
                today = stateSnapshot.today,
                anonymizeClientNames = stateSnapshot.aiSettings.anonymizeClientNames,
            )
            val result = port.analyzeAgendaAndFinancialHistory(
                request = request,
                conversation = previousConversation,
                userPrompt = cleanPrompt,
            )
            result.onSuccess { completion ->
                val aiMsg = AiChatMessage(
                    id = "a_${System.nanoTime()}",
                    isUser = false,
                    content = completion.content,
                    modelUsed = completion.model,
                    latencyMs = completion.latencyMs,
                    totalTokens = completion.totalTokens,
                )
                _dataState.update {
                    it.copy(
                        aiMessages = it.aiMessages + aiMsg,
                        isAiAnalyzing = false,
                        aiErrorMessage = null,
                    )
                }
            }.onFailure { err ->
                _dataState.update {
                    it.copy(
                        isAiAnalyzing = false,
                        aiErrorMessage = err.message ?: "No se pudo completar el análisis con Groq.",
                    )
                }
            }
        }
    }

    private fun currentOwnerId(): String? =
        (_authState.value as? AuthUiState.SignedIn)?.session?.userId

    class Factory(
        private val authRepository: AuthRepository,
        private val activityRepository: ActivityRepository,
        private val supplierRepository: SupplierRepository,
        private val settingsRepository: SettingsRepository,
        private val venueRepository: VenueRepository,
        private val mapsAndLocationPort: MapsAndLocationPort,
        private val departureAlarmPort: DepartureAlarmPort,
        private val bankAccountRepository: BankAccountRepository? = null,
        private val loginHistoryRepository: LoginHistoryRepository? = null,
        private val privacyPreferencesPort: PrivacyPreferencesPort? = null,
        private val activityFinancePort: ActivityFinancePort? = null,
        private val offlineCachePort: OfflineCachePort? = null,
        private val connectivityPort: ConnectivityPort? = null,
        private val brandProfilePort: BrandProfilePort? = null,
        private val accountDeletionPort: AccountDeletionPort? = null,
        private val widgetSyncPort: WidgetSyncPort? = null,
        private val groqAiPort: GroqAiPort? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                authRepository = authRepository,
                activityRepository = activityRepository,
                supplierRepository = supplierRepository,
                settingsRepository = settingsRepository,
                venueRepository = venueRepository,
                mapsAndLocationPort = mapsAndLocationPort,
                departureAlarmPort = departureAlarmPort,
                bankAccountRepository = bankAccountRepository,
                loginHistoryRepository = loginHistoryRepository,
                privacyPreferencesPort = privacyPreferencesPort,
                activityFinancePort = activityFinancePort,
                offlineCachePort = offlineCachePort,
                connectivityPort = connectivityPort,
                brandProfilePort = brandProfilePort,
                accountDeletionPort = accountDeletionPort,
                widgetSyncPort = widgetSyncPort,
                groqAiPort = groqAiPort,
            ) as T
        }
    }
}
