package com.example

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.alarms.NotificationHelper
import com.example.data.AppContainer
import com.example.data.CalendarEventExporter
import com.example.data.PdfStatementSharer
import com.example.data.local.BrandAssetStorage
import com.example.data.port.AppThemeMode
import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.ArtistBrandProfile
import com.example.domain.model.Currency
import com.example.domain.rules.AccountStatement
import com.example.domain.rules.AccountStatementPdfGenerator
import com.example.domain.rules.CsvExporter
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.GeneratedStatementPdf
import com.example.domain.rules.PdfBrandAssets
import com.example.domain.rules.QuoteCalculator
import com.example.domain.rules.QuotePdfGenerator
import com.example.domain.rules.SupplierMessagePreset
import com.example.ui.components.ApptividadesBottomBar
import com.example.ui.components.MainTab
import com.example.ui.components.PdfPreviewDialog
import com.example.ui.components.SupplierQuickMessageSheet
import com.example.ui.screens.ActivityFormScreen
import com.example.ui.screens.AgendaScreen
import com.example.ui.screens.AlertsAndDeparturesSheet
import com.example.ui.screens.AppLockOverlay
import com.example.ui.screens.BalanceScreen
import com.example.ui.screens.BankAccountsScreen
import com.example.ui.screens.BrandProfileScreen
import com.example.ui.screens.CobrosScreen
import com.example.ui.screens.GroqAnalyticsScreen
import com.example.ui.screens.InicioScreen
import com.example.ui.screens.LoginHistoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.QuoteCreatorScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SuppliersScreen
import com.example.ui.screens.VenueMapPickerScreen
import com.example.ui.security.AppBiometricAuthenticator
import com.example.widget.NextGigWidgetProvider
import com.example.ui.theme.ApptividadesTheme
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.DarkGround
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.Ground
import com.example.ui.theme.SurfaceWhite
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.QuoteFunnelViewModel
import com.example.ui.viewmodel.CrmViewModel
import com.example.ui.screens.CrmScreen
import com.example.domain.rules.HotelCrmCalculator
import com.example.domain.rules.WhatsAppReminderBuilder
import com.example.ui.screens.QuoteFunnelScreen
import com.example.domain.rules.QuoteFunnelCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate
import java.time.YearMonth

const val ROUTE_INICIO = "inicio"
const val ROUTE_AGENDA = "agenda"
const val ROUTE_COBROS = "cobros"
const val ROUTE_BALANCE = "balance"
const val ROUTE_SUPPLIERS = "suppliers"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_REPORTS = "reports"
const val ROUTE_BANK_ACCOUNTS = "bank_accounts"
const val ROUTE_LOGIN_HISTORY = "login_history"
const val ROUTE_BRAND_PROFILE = "brand_profile"
const val ROUTE_QUOTE_CREATOR = "quote_creator"
const val ROUTE_QUOTE_FUNNEL = "quote_funnel"
const val ROUTE_CRM = "crm"
const val ROUTE_AI_ANALYTICS = "ai_analytics"

private data class NotificationDeepLink(
    val openTab: String? = null,
    val quickMessageActivityId: String? = null,
    val quickMessagePreset: SupplierMessagePreset = SupplierMessagePreset.DELAY,
    val seq: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private val _pendingDeepLink = MutableStateFlow<NotificationDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer.getInstance(applicationContext)
        handleNotificationIntent(intent)
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationHelper.ensureChannels(applicationContext)
            appContainer.departureAlarmScheduler.ensurePeriodicSyncScheduled()
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Ground.toArgb(),
                darkScrim = Ground.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.WHITE,
                darkScrim = android.graphics.Color.WHITE,
            ),
        )

        setContent {
            val vm: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    authRepository = appContainer.authRepository,
                    activityRepository = appContainer.activityRepository,
                    supplierRepository = appContainer.supplierRepository,
                    settingsRepository = appContainer.settingsRepository,
                    venueRepository = appContainer.venueRepository,
                    mapsAndLocationPort = appContainer.mapsAndLocationPort,
                    departureAlarmPort = appContainer.departureAlarmPort,
                    bankAccountRepository = appContainer.bankAccountRepository,
                    loginHistoryRepository = appContainer.loginHistoryRepository,
                    privacyPreferencesPort = appContainer.privacyPreferencesPort,
                    activityFinancePort = appContainer.activityFinancePort,
                    offlineCachePort = appContainer.offlineCachePort,
                    connectivityPort = appContainer.connectivityPort,
                    brandProfilePort = appContainer.brandProfilePort,
                    accountDeletionPort = appContainer.accountDeletionPort,
                    widgetSyncPort = appContainer.widgetSyncPort,
                    groqAiPort = appContainer.groqAiPort,
                )
            )
            val funnelVm: QuoteFunnelViewModel = viewModel(
                factory = QuoteFunnelViewModel.Factory(
                    port = appContainer.quoteFunnelPort,
                    manager = appContainer.quoteFunnelManager,
                )
            )
            val crmVm: CrmViewModel = viewModel(
                factory = CrmViewModel.Factory(
                    port = appContainer.crmPort,
                    manager = appContainer.crmManager,
                )
            )
            val dataStateForWindow by vm.dataState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (dataStateForWindow.privacyPreferences.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            DisposableEffect(isDarkTheme) {
                if (isDarkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(DarkGround.toArgb()),
                        navigationBarStyle = SystemBarStyle.dark(DarkSurface.toArgb()),
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            scrim = Ground.toArgb(),
                            darkScrim = Ground.toArgb(),
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            scrim = SurfaceWhite.toArgb(),
                            darkScrim = SurfaceWhite.toArgb(),
                        ),
                    )
                }
                onDispose {}
            }

            LaunchedEffect(dataStateForWindow.privacyPreferences.screenPrivacyEnabled) {
                if (dataStateForWindow.privacyPreferences.screenPrivacyEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            ApptividadesTheme(darkTheme = isDarkTheme) {
                val deepLink by _pendingDeepLink.asStateFlow().collectAsStateWithLifecycle()
                ApptividadesRoot(
                    viewModel = vm,
                    funnelViewModel = funnelVm,
                    crmViewModel = crmVm,
                    pendingDeepLink = deepLink,
                    onConsumeDeepLink = { _pendingDeepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent ?: return
        val tab = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB)?.takeIf { it.isNotBlank() }
        val msgActivityId = intent.getStringExtra(NotificationHelper.EXTRA_QUICK_MESSAGE_ACTIVITY_ID)
            ?.takeIf { it.isNotBlank() }
        val presetName = intent.getStringExtra(NotificationHelper.EXTRA_QUICK_MESSAGE_PRESET)
        val preset = SupplierMessagePreset.entries.firstOrNull { it.name == presetName }
            ?: SupplierMessagePreset.DELAY
        if (tab != null || msgActivityId != null) {
            _pendingDeepLink.value = NotificationDeepLink(
                openTab = tab,
                quickMessageActivityId = msgActivityId,
                quickMessagePreset = preset,
            )
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_TAB)
            intent.removeExtra(NotificationHelper.EXTRA_QUICK_MESSAGE_ACTIVITY_ID)
            intent.removeExtra(NotificationHelper.EXTRA_QUICK_MESSAGE_PRESET)
        }
    }
}

private sealed interface FormOverlayState {
    data object Hidden : FormOverlayState
    data class Create(val date: LocalDate) : FormOverlayState
    data class Edit(val activity: Activity) : FormOverlayState
}

@Composable
private fun ApptividadesRoot(
    viewModel: MainViewModel,
    funnelViewModel: QuoteFunnelViewModel,
    crmViewModel: CrmViewModel,
    pendingDeepLink: NotificationDeepLink?,
    onConsumeDeepLink: () -> Unit,
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val hostActivity = context as? android.app.Activity

    var signedInFromLoginScreen by remember { mutableStateOf(false) }
    var isAppUnlocked by remember { mutableStateOf(false) }
    var lockErrorMessage by remember { mutableStateOf<String?>(null) }
    var backgroundedAtMillis by remember { mutableStateOf<Long?>(null) }
    var pendingEnableLockAfterFallback by remember { mutableStateOf(false) }

    val fallbackCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val enabling = pendingEnableLockAfterFallback
        pendingEnableLockAfterFallback = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isAppUnlocked = true
            lockErrorMessage = null
            if (enabling) {
                viewModel.setAppLockEnabled(true)
            }
        } else {
            if (!enabling) {
                lockErrorMessage = null
            }
        }
    }

    fun launchUnlockPrompt() {
        val act = hostActivity ?: return
        lockErrorMessage = null
        AppBiometricAuthenticator.authenticate(
            activity = act,
            title = act.getString(R.string.app_lock_prompt_title),
            subtitle = act.getString(R.string.app_lock_prompt_subtitle),
            fallbackCredentialLauncher = { intent ->
                pendingEnableLockAfterFallback = false
                fallbackCredentialLauncher.launch(intent)
            },
            onSuccess = {
                isAppUnlocked = true
                lockErrorMessage = null
            },
            onFailure = { msg ->
                lockErrorMessage = msg
            },
        )
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.SignedOut -> {
                signedInFromLoginScreen = true
                isAppUnlocked = false
                lockErrorMessage = null
                backgroundedAtMillis = null
            }
            is AuthUiState.SignedIn -> {
                if (signedInFromLoginScreen) {
                    isAppUnlocked = true
                    signedInFromLoginScreen = false
                }
            }
            AuthUiState.Checking -> Unit
        }
    }

    val appLockEnabled = dataState.privacyPreferences.appLockEnabled
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (authState is AuthUiState.SignedIn && appLockEnabled) {
            backgroundedAtMillis = System.currentTimeMillis()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (authState is AuthUiState.SignedIn &&
            AppBiometricAuthenticator.shouldRelockAfterBackground(
                appLockEnabled = appLockEnabled,
                backgroundedAtMillis = backgroundedAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            isAppUnlocked = false
            lockErrorMessage = null
        }
        backgroundedAtMillis = null
    }

    when (val auth = authState) {
        AuthUiState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.ground),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        }

        is AuthUiState.SignedOut -> {
            LoginScreen(
                state = auth,
                onSignIn = viewModel::signIn,
                onResetPassword = viewModel::resetPassword,
                onSignUp = viewModel::signUp,
                onResendConfirmation = viewModel::resendConfirmation,
            )
        }

        is AuthUiState.SignedIn -> {
            Box(modifier = Modifier.fillMaxSize()) {
                ApptividadesSignedInShell(
                    userEmail = auth.session.email,
                    viewModel = viewModel,
                    funnelViewModel = funnelViewModel,
                    crmViewModel = crmViewModel,
                    pendingDeepLink = pendingDeepLink,
                    onConsumeDeepLink = onConsumeDeepLink,
                    onToggleAppLock = { enable, onUnavailable ->
                        if (!enable) {
                            viewModel.setAppLockEnabled(false)
                        } else {
                            val act = hostActivity
                            if (act == null || !AppBiometricAuthenticator.canAuthenticate(context)) {
                                onUnavailable(context.getString(R.string.settings_app_lock_unavailable))
                            } else {
                                AppBiometricAuthenticator.authenticate(
                                    activity = act,
                                    title = act.getString(R.string.app_lock_enable_prompt_title),
                                    subtitle = act.getString(R.string.app_lock_enable_prompt_subtitle),
                                    fallbackCredentialLauncher = { intent ->
                                        pendingEnableLockAfterFallback = true
                                        fallbackCredentialLauncher.launch(intent)
                                    },
                                    onSuccess = {
                                        isAppUnlocked = true
                                        viewModel.setAppLockEnabled(true)
                                    },
                                    onFailure = { msg ->
                                        if (!msg.isNullOrBlank()) onUnavailable(msg)
                                    },
                                )
                            }
                        }
                    },
                )

                if (appLockEnabled && !isAppUnlocked) {
                    AppLockOverlay(
                        userEmail = auth.session.email,
                        errorMessage = lockErrorMessage,
                        onRequestUnlock = ::launchUnlockPrompt,
                        onSignOut = viewModel::signOut,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApptividadesSignedInShell(
    userEmail: String,
    viewModel: MainViewModel,
    funnelViewModel: QuoteFunnelViewModel,
    crmViewModel: CrmViewModel,
    pendingDeepLink: NotificationDeepLink?,
    onConsumeDeepLink: () -> Unit,
    onToggleAppLock: (enable: Boolean, onUnavailable: (String) -> Unit) -> Unit,
) {
    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val savedQuotes by funnelViewModel.quotes.collectAsStateWithLifecycle()
    val crmProfiles by crmViewModel.profiles.collectAsStateWithLifecycle()
    val crmContacts by crmViewModel.contacts.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_INICIO
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var formOverlay by remember { mutableStateOf<FormOverlayState>(FormOverlayState.Hidden) }
    var showAlertsSheet by remember { mutableStateOf(false) }
    var showHomeMapPicker by remember { mutableStateOf(false) }
    var captureHomeAfterLocationGrant by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var pendingDownloadPdf by remember { mutableStateOf<GeneratedStatementPdf?>(null) }
    var pendingDownloadSuccessMsg by remember { mutableStateOf<String?>(null) }
    var previewStatementPdf by remember { mutableStateOf<Triple<AccountStatement, GeneratedStatementPdf, PdfBrandAssets>?>(null) }
    var quickMessageRequest by remember { mutableStateOf<Pair<Activity, SupplierMessagePreset>?>(null) }
    var pendingNewTimingOverride by remember { mutableStateOf<ActivityTimelineOverride?>(null) }
    var pendingNewFinancialRecord by remember { mutableStateOf(ActivityFinancialRecord()) }

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var hasLocationPermission by remember {
        mutableStateOf(checkLocationPermission(context))
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(checkIgnoringBatteryOptimizations(context))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
        hasLocationPermission = checkLocationPermission(context)
        isIgnoringBatteryOptimizations = checkIgnoringBatteryOptimizations(context)
        viewModel.refreshExactAlarmPermissionStatus()
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val pdf = pendingDownloadPdf
        val successMsg = pendingDownloadSuccessMsg ?: context.getString(R.string.cobros_pdf_saved_ok)
        pendingDownloadPdf = null
        pendingDownloadSuccessMsg = null
        if (uri != null && pdf != null) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    PdfStatementSharer.saveToDocumentUri(context, uri, pdf.bytes)
                }
                snackbarHostState.showSnackbar(
                    if (saved.isSuccess) successMsg
                    else context.getString(R.string.cobros_pdf_error)
                )
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) ||
            checkLocationPermission(context)
        hasLocationPermission = granted
        if (granted && captureHomeAfterLocationGrant) {
            captureHomeAfterLocationGrant = false
            viewModel.captureCurrentLocationAsHome {}
        } else {
            captureHomeAfterLocationGrant = false
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestLocationPermission(captureHomeAfterGrant: Boolean) {
        captureHomeAfterLocationGrant = captureHomeAfterGrant
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun openBatteryOptimizationSettings() {
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { context.startActivity(batteryIntent) }.isSuccess
        if (!launched) {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(appDetails) }
        }
    }

    LaunchedEffect(dataState.snackbarMessage) {
        val msg = dataState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val currentTab = MainTab.entries.firstOrNull { it.route == currentRoute }
    val showBottomBar = currentTab != null

    val userDisplayName = remember(userEmail) {
        val localPart = userEmail.substringBefore('@').trim()
        when {
            localPart.contains("omar", ignoreCase = true) -> "Omar"
            localPart.isNotEmpty() -> localPart
                .split('.', '_', '-')
                .firstOrNull()
                ?.replaceFirstChar { it.uppercase() }
            else -> null
        }
    }

    fun navigateToTab(tab: MainTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun exportActivityToCalendar(activity: Activity) {
        val supplierDisplay = dataState.supplierResolution.displayName(activity.supplierName)
        val venueDisplay = dataState.venueResolution.displayName(activity.venueName)
        val durationMin = dataState.activityTimings[activity.id]?.eventMinutes
            ?: dataState.settings.eventMinutes
        val exported = CalendarEventExporter.exportActivityToCalendar(
            context = context,
            activity = activity,
            supplierDisplayName = supplierDisplay,
            venueDisplayName = venueDisplay,
            durationMinutes = durationMin,
        )
        if (exported.isFailure) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.calendar_export_error))
            }
        }
    }

    LaunchedEffect(pendingDeepLink, dataState.activities, dataState.isLoading) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        if (link.openTab != null) {
            val targetTab = MainTab.entries.firstOrNull { it.route == link.openTab }
            if (targetTab != null) {
                navigateToTab(targetTab)
            } else if (link.openTab == ROUTE_QUOTE_FUNNEL) {
                navController.navigate(ROUTE_QUOTE_FUNNEL)
            } else if (link.openTab == ROUTE_CRM) {
                navController.navigate(ROUTE_CRM)
            }
            onConsumeDeepLink()
            return@LaunchedEffect
        }
        val actId = link.quickMessageActivityId
        if (actId != null) {
            val found = dataState.activities.firstOrNull { it.id == actId }
            if (found != null) {
                quickMessageRequest = found to link.quickMessagePreset
                onConsumeDeepLink()
            } else if (!dataState.isLoading && dataState.activities.isNotEmpty()) {
                onConsumeDeepLink()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = ApptividadesThemeDefaults.colors.ground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = if (showBottomBar) 24.dp else 0.dp),
                )
            },
            bottomBar = {
                if (currentTab != null) {
                    ApptividadesBottomBar(
                        currentTab = currentTab,
                        onSelectTab = ::navigateToTab,
                        onNewActivityClick = {
                            val prefillDate = if (currentTab == MainTab.AGENDA) {
                                dataState.selectedAgendaDate
                            } else {
                                dataState.today
                            }
                            formOverlay = FormOverlayState.Create(prefillDate)
                        },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_INICIO,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(ROUTE_INICIO) {
                    InicioScreen(
                        state = dataState,
                        userDisplayName = userDisplayName,
                        onOpenCobros = { navigateToTab(MainTab.COBROS) },
                        onOpenAgenda = { navigateToTab(MainTab.AGENDA) },
                        onOpenActivity = { formOverlay = FormOverlayState.Edit(it) },
                        onNewActivity = { formOverlay = FormOverlayState.Create(dataState.today) },
                        onOpenReports = { navController.navigate(ROUTE_REPORTS) },
                        onOpenSuppliers = { navController.navigate(ROUTE_SUPPLIERS) },
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onRefresh = { viewModel.syncNow() },
                        onOpenAlerts = { showAlertsSheet = true },
                        onQuickMessageSupplier = { quickMessageRequest = it to SupplierMessagePreset.ARRIVED },
                        onQuickMessageSupplierWithPreset = { act, preset ->
                            quickMessageRequest = act to preset
                        },
                        onSetContingencyUnderControl = viewModel::setContingencyUnderControl,
                        onOpenQuoteCreator = { navController.navigate(ROUTE_QUOTE_CREATOR) },
                        onOpenQuoteFunnel = { navController.navigate(ROUTE_QUOTE_FUNNEL) },
                        onOpenCrm = { navController.navigate(ROUTE_CRM) },
                        onOpenAiAnalyst = { navController.navigate(ROUTE_AI_ANALYTICS) },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                }

                composable(ROUTE_AGENDA) {
                    AgendaScreen(
                        state = dataState,
                        onPrevMonth = { viewModel.navigateAgendaMonth(-1) },
                        onNextMonth = { viewModel.navigateAgendaMonth(1) },
                        onSelectDate = viewModel::selectAgendaDate,
                        onNewActivityOnDate = { formOverlay = FormOverlayState.Create(it) },
                        onOpenActivity = { formOverlay = FormOverlayState.Edit(it) },
                        onTogglePaid = viewModel::togglePaymentStatus,
                        onRefresh = { viewModel.syncNow() },
                        onQuickMessageSupplier = { quickMessageRequest = it to SupplierMessagePreset.ARRIVED },
                        onExportToCalendar = ::exportActivityToCalendar,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                }

                composable(ROUTE_COBROS) {
                    CobrosScreen(
                        state = dataState,
                        onToggleSelectGroup = viewModel::toggleReceivableGroupSelection,
                        onMarkSelectedPaid = viewModel::markSelectedReceivablesPaid,
                        onPreviewStatementPdf = { supplierName, currency ->
                            coroutineScope.launch {
                                val brandAssets = loadPdfBrandAssets(context, dataState.brandProfile)
                                val result = withContext(Dispatchers.Default) {
                                    viewModel.buildAccountStatement(
                                        supplierName = supplierName,
                                        currency = currency,
                                        issuerFallback = userDisplayName ?: "Omar",
                                    ).mapCatching { stmt ->
                                        Triple(
                                            stmt,
                                            AccountStatementPdfGenerator.generate(stmt, brandAssets),
                                            brandAssets,
                                        )
                                    }
                                }
                                result.onSuccess { triple ->
                                    previewStatementPdf?.third?.recycleSafely()
                                    previewStatementPdf = triple
                                }.onFailure { err ->
                                    brandAssets.recycleSafely()
                                    snackbarHostState.showSnackbar(
                                        err.message ?: context.getString(R.string.cobros_pdf_error)
                                    )
                                }
                            }
                        },
                        onSendStatementPdf = { supplierName, currency ->
                            coroutineScope.launch {
                                val brandAssets = loadPdfBrandAssets(context, dataState.brandProfile)
                                val result = withContext(Dispatchers.Default) {
                                    viewModel.buildAccountStatement(
                                        supplierName = supplierName,
                                        currency = currency,
                                        issuerFallback = userDisplayName ?: "Omar",
                                    ).mapCatching { stmt ->
                                        stmt to AccountStatementPdfGenerator.generate(stmt, brandAssets)
                                    }
                                }
                                brandAssets.recycleSafely()
                                result.onSuccess { (stmt, pdf) ->
                                    val phone = viewModel.findSupplierPhone(stmt.supplierDisplayName)
                                    val shared = PdfStatementSharer.shareStatementPdf(
                                        context = context,
                                        pdf = pdf,
                                        caption = stmt.shareCaption,
                                        supplierRawPhone = phone,
                                    )
                                    if (shared.isFailure) {
                                        snackbarHostState.showSnackbar(
                                            shared.exceptionOrNull()?.message
                                                ?: context.getString(R.string.cobros_pdf_error)
                                        )
                                    }
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(
                                        err.message ?: context.getString(R.string.cobros_pdf_error)
                                    )
                                }
                            }
                        },
                        onDownloadStatementPdf = { supplierName, currency ->
                            coroutineScope.launch {
                                val brandAssets = loadPdfBrandAssets(context, dataState.brandProfile)
                                val result = withContext(Dispatchers.Default) {
                                    viewModel.buildAccountStatement(
                                        supplierName = supplierName,
                                        currency = currency,
                                        issuerFallback = userDisplayName ?: "Omar",
                                    ).mapCatching { stmt ->
                                        AccountStatementPdfGenerator.generate(stmt, brandAssets)
                                    }
                                }
                                brandAssets.recycleSafely()
                                result.onSuccess { pdf ->
                                    pendingDownloadPdf = pdf
                                    pendingDownloadSuccessMsg = context.getString(R.string.cobros_pdf_saved_ok)
                                    savePdfLauncher.launch(pdf.fileName)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(
                                        err.message ?: context.getString(R.string.cobros_pdf_error)
                                    )
                                }
                            }
                        },
                        onOpenBankAccountsSettings = { navController.navigate(ROUTE_BANK_ACCOUNTS) },
                        onRefresh = { viewModel.syncNow() },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                }

                composable(ROUTE_BALANCE) {
                    BalanceScreen(
                        state = dataState,
                        onSelectMonth = viewModel::selectBalanceMonth,
                        onUpdateMonthlyGoal = viewModel::updateMonthlyGoal,
                        onExportCsv = {
                            val balance = dataState.balance12Months
                            val windowMonths = balance.months.map { it.month }.toSet()
                            val periodActivities = dataState.activities.filter {
                                YearMonth.from(it.eventDate) in windowMonths
                            }
                            shareCsv(
                                context = context,
                                title = "Balance ${DominicanFormatter.formatMonthRange(balance.startMonth, balance.endMonth)}",
                                csvContent = CsvExporter.buildActivitiesCsv(periodActivities),
                            )
                        },
                        onOpenReports = { navController.navigate(ROUTE_REPORTS) },
                        onRefresh = { viewModel.syncNow() },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                }

                composable(ROUTE_SUPPLIERS) {
                    SuppliersScreen(
                        state = dataState,
                        onBack = { navController.popBackStack() },
                        onSaveSupplier = viewModel::saveSupplier,
                        onDeleteSupplier = viewModel::deleteSupplier,
                    )
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        settings = dataState.settings,
                        userEmail = userEmail,
                        isMutating = dataState.isMutating,
                        onBack = { navController.popBackStack() },
                        onSaveSettings = { updated ->
                            viewModel.saveSettings(updated) {}
                        },
                        onSignOut = viewModel::signOut,
                        bankAccountsCount = dataState.bankAccounts.size,
                        loginHistoryCount = dataState.loginHistory.size,
                        hasNotificationPermission = hasNotificationPermission,
                        hasLocationPermission = hasLocationPermission,
                        canScheduleExactAlarms = dataState.canScheduleExactAlarms,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onRequestLocationPermission = ::requestLocationPermission,
                        onRequestExactAlarmPermission = ::requestExactAlarmPermission,
                        onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
                        onUseCurrentLocationAsHome = { viewModel.captureCurrentLocationAsHome {} },
                        onPickHomeOnMap = { showHomeMapPicker = true },
                        onOpenBankAccounts = { navController.navigate(ROUTE_BANK_ACCOUNTS) },
                        onOpenLoginHistory = { navController.navigate(ROUTE_LOGIN_HISTORY) },
                        onChangePassword = viewModel::changePassword,
                        screenPrivacyEnabled = dataState.privacyPreferences.screenPrivacyEnabled,
                        resolveExternalIpInHistory = dataState.privacyPreferences.resolveExternalIpInHistory,
                        appLockEnabled = dataState.privacyPreferences.appLockEnabled,
                        themeMode = dataState.privacyPreferences.themeMode,
                        onToggleScreenPrivacy = viewModel::setScreenPrivacyEnabled,
                        onToggleResolveExternalIp = viewModel::setResolveExternalIpInHistory,
                        onToggleAppLock = { enable ->
                            onToggleAppLock(enable) { msg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        },
                        onSelectThemeMode = viewModel::setThemeMode,
                        defaultUsdToDopRate = dataState.privacyPreferences.defaultUsdToDopRate,
                        convertMixedCurrencies = dataState.privacyPreferences.convertMixedCurrencies,
                        onSaveDefaultUsdToDopRate = viewModel::setDefaultUsdToDopRate,
                        onToggleConvertMixedCurrencies = viewModel::setConvertMixedCurrencies,
                        brandProfileConfigured = dataState.brandProfile.hasBranding,
                        onOpenBrandProfile = { navController.navigate(ROUTE_BRAND_PROFILE) },
                        onPinWidget = {
                            val pinned = requestPinNextGigWidget(context)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        if (pinned) R.string.settings_widget_pinned_ok
                                        else R.string.settings_widget_pin_unsupported
                                    )
                                )
                            }
                        },
                        onDeleteAccount = { onResult ->
                            viewModel.deleteAccount { res ->
                                if (res.isSuccess) {
                                    BrandAssetStorage.clearAllBrandAssets(context)
                                    PdfStatementSharer.cleanupAllCachedStatements(context)
                                    funnelViewModel.deleteAll()
                                    crmViewModel.deleteAll()
                                }
                                onResult(res)
                            }
                        },
                        groqAiSettings = dataState.aiSettings,
                        onSaveGroqApiKey = viewModel::saveGroqApiKey,
                        onClearGroqApiKey = viewModel::clearGroqApiKey,
                        onSelectGroqModel = viewModel::setGroqModel,
                        onToggleGroqAnonymize = viewModel::setGroqAnonymizeClients,
                        onTestGroqConnection = viewModel::testGroqConnection,
                        onOpenAiAnalyst = { navController.navigate(ROUTE_AI_ANALYTICS) },
                    )
                }

                composable(ROUTE_BRAND_PROFILE) {
                    BrandProfileScreen(
                        brandProfile = dataState.brandProfile,
                        onBack = { navController.popBackStack() },
                        onSaveProfile = { updated ->
                            viewModel.saveBrandProfile(updated)
                        },
                    )
                }

                composable(ROUTE_QUOTE_CREATOR) {
                    // Keeps the same funnel record (and quote number) while this creator session regenerates the PDF.
                    var sessionQuoteId by remember { mutableStateOf<String?>(null) }
                    val frequentNames = remember(dataState.frequentSuppliers, dataState.suppliers) {
                        (dataState.frequentSuppliers.map { it.displayName } + dataState.suppliers.map { it.name })
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinctBy { it.lowercase() }
                    }
                    val issuerName = dataState.brandProfile.artistName.trim()
                        .ifBlank { userDisplayName ?: "Apptividades" }
                    QuoteCreatorScreen(
                        today = dataState.today,
                        defaultCurrency = dataState.settings.defaultCurrency,
                        issuerDefaultName = issuerName,
                        frequentClientNames = frequentNames,
                        bankAccounts = dataState.bankAccounts,
                        onBack = { navController.popBackStack() },
                        onDownloadQuote = { quote ->
                            coroutineScope.launch {
                                val prepared = funnelViewModel.prepare(sessionQuoteId, quote, dataState.today)
                                sessionQuoteId = prepared.id
                                val brandAssets = loadPdfBrandAssets(context, dataState.brandProfile)
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { QuotePdfGenerator.generate(prepared.quote, brandAssets) }
                                }
                                brandAssets.recycleSafely()
                                result.onSuccess { pdf ->
                                    funnelViewModel.save(prepared, dataState.today, markSent = false)
                                    pendingDownloadPdf = pdf
                                    pendingDownloadSuccessMsg = context.getString(R.string.quote_saved_ok)
                                    savePdfLauncher.launch(pdf.fileName)
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(
                                        err.message ?: context.getString(R.string.quote_generate_error)
                                    )
                                }
                            }
                        },
                        onShareQuote = { quote ->
                            coroutineScope.launch {
                                val prepared = funnelViewModel.prepare(sessionQuoteId, quote, dataState.today)
                                sessionQuoteId = prepared.id
                                val brandAssets = loadPdfBrandAssets(context, dataState.brandProfile)
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { QuotePdfGenerator.generate(prepared.quote, brandAssets) }
                                }
                                brandAssets.recycleSafely()
                                result.onSuccess { pdf ->
                                    val phone = viewModel.findSupplierPhone(prepared.quote.clientName)
                                    val caption = QuoteCalculator.buildShareCaption(prepared.quote)
                                    val shared = PdfStatementSharer.shareStatementPdf(
                                        context = context,
                                        pdf = pdf,
                                        caption = caption,
                                        supplierRawPhone = phone,
                                    )
                                    if (shared.isFailure) {
                                        snackbarHostState.showSnackbar(
                                            shared.exceptionOrNull()?.message
                                                ?: context.getString(R.string.quote_generate_error)
                                        )
                                    } else {
                                        funnelViewModel.save(prepared, dataState.today, markSent = true)
                                        snackbarHostState.showSnackbar(context.getString(R.string.funnel_saved_sent))
                                    }
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(
                                        err.message ?: context.getString(R.string.quote_generate_error)
                                    )
                                }
                            }
                        },
                    )
                }

                composable(ROUTE_CRM) {
                    val crmAccounts = remember(
                        dataState.activities,
                        dataState.suppliers,
                        savedQuotes,
                        crmProfiles,
                        crmContacts,
                        dataState.today,
                    ) {
                        HotelCrmCalculator.buildAccounts(
                            activities = dataState.activities,
                            suppliers = dataState.suppliers,
                            quotes = savedQuotes,
                            profiles = crmProfiles,
                            contacts = crmContacts,
                            today = dataState.today,
                        )
                    }
                    CrmScreen(
                        accounts = crmAccounts,
                        today = dataState.today,
                        issuerName = dataState.brandProfile.artistName.trim().ifBlank { userDisplayName ?: "Omar" },
                        onBack = { navController.popBackStack() },
                        onSendWhatsApp = { account, message ->
                            val opened = openWhatsApp(context, account.phone, message)
                            if (!opened) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.crm_whatsapp_no_phone))
                                }
                            }
                        },
                        onLogContact = { account, channel, note, date, next ->
                            crmViewModel.logContact(account, channel, note, date, next)
                        },
                        onSaveDetails = { account, person, role, phone, email, notes ->
                            crmViewModel.updateDetails(account, person, role, phone, email, notes)
                        },
                        onSetNextContact = { account, date -> crmViewModel.setNextContact(account, date) },
                        onAddProspect = { type, name -> crmViewModel.addProspect(type, name) },
                        onDeleteContact = { contact -> crmViewModel.deleteContact(contact.id) },
                        onDeleteAccountData = { account -> crmViewModel.deleteAccountData(account.accountKey) },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                }

                composable(ROUTE_QUOTE_FUNNEL) {
                    QuoteFunnelScreen(
                        quotes = savedQuotes,
                        today = dataState.today,
                        onBack = { navController.popBackStack() },
                        onNewQuote = { navController.navigate(ROUTE_QUOTE_CREATOR) },
                        onSendQuote = { saved ->
                            coroutineScope.launch {
                                val brandAssets = loadPdfBrandAssets(context, dataState.brandProfile)
                                val quote = QuoteFunnelCalculator.quoteForPdf(saved, dataState.bankAccounts)
                                val result = withContext(Dispatchers.Default) {
                                    runCatching { QuotePdfGenerator.generate(quote, brandAssets) }
                                }
                                brandAssets.recycleSafely()
                                result.onSuccess { pdf ->
                                    val shared = PdfStatementSharer.shareStatementPdf(
                                        context = context,
                                        pdf = pdf,
                                        caption = QuoteCalculator.buildShareCaption(quote),
                                        supplierRawPhone = viewModel.findSupplierPhone(quote.clientName),
                                    )
                                    if (shared.isSuccess) {
                                        funnelViewModel.markSent(saved.id, dataState.today)
                                        snackbarHostState.showSnackbar(context.getString(R.string.funnel_saved_sent))
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            shared.exceptionOrNull()?.message
                                                ?: context.getString(R.string.quote_generate_error)
                                        )
                                    }
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(
                                        err.message ?: context.getString(R.string.quote_generate_error)
                                    )
                                }
                            }
                        },
                        onSetFollowUp = { saved, date -> funnelViewModel.scheduleFollowUp(saved.id, date) },
                        onMarkWon = { saved ->
                            funnelViewModel.markWon(saved.id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.funnel_marked_won))
                            }
                        },
                        onMarkLost = { saved, reason -> funnelViewModel.markLost(saved.id, reason) },
                        onReopen = { saved -> funnelViewModel.reopen(saved.id, dataState.today) },
                        onCreateActivity = { saved ->
                            QuoteFunnelCalculator.toActivityDraft(saved)?.let { draft ->
                                viewModel.saveNewActivity(draft, 1, null, null, null) { res ->
                                    res.onSuccess { funnelViewModel.markConverted(saved.id) }
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            res.exceptionOrNull()?.message
                                                ?: context.getString(R.string.funnel_activity_ok)
                                        )
                                    }
                                }
                            }
                        },
                        onDelete = { saved -> funnelViewModel.delete(saved.id) },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    )
                }

                composable(ROUTE_BANK_ACCOUNTS) {
                    BankAccountsScreen(
                        accounts = dataState.bankAccounts,
                        isMutating = dataState.isMutating,
                        defaultCurrency = dataState.settings.defaultCurrency,
                        onBack = { navController.popBackStack() },
                        onSaveAccount = viewModel::saveBankAccount,
                        onSetDefault = viewModel::setDefaultBankAccount,
                        onDeleteAccount = viewModel::deleteBankAccount,
                    )
                }

                composable(ROUTE_LOGIN_HISTORY) {
                    LoginHistoryScreen(
                        events = dataState.loginHistory,
                        onBack = { navController.popBackStack() },
                        onClearHistory = viewModel::clearLoginHistory,
                    )
                }

                composable(ROUTE_REPORTS) {
                    ReportsScreen(
                        state = dataState,
                        onBack = { navController.popBackStack() },
                        onPrevMonth = { viewModel.navigateReportsMonth(-1) },
                        onNextMonth = { viewModel.navigateReportsMonth(1) },
                        onExportMonthCsv = { monthActivities, ym ->
                            shareCsv(
                                context = context,
                                title = "Apptividades ${DominicanFormatter.formatMonthYear(ym)}",
                                csvContent = CsvExporter.buildActivitiesCsv(monthActivities),
                            )
                        },
                        onExport12MonthsCsv = {
                            val balance = dataState.balance12Months
                            val windowMonths = balance.months.map { it.month }.toSet()
                            val periodActivities = dataState.activities.filter {
                                YearMonth.from(it.eventDate) in windowMonths
                            }
                            shareCsv(
                                context = context,
                                title = "Apptividades 12 meses",
                                csvContent = CsvExporter.buildActivitiesCsv(periodActivities),
                            )
                        },
                        onOpenQuoteCreator = { navController.navigate(ROUTE_QUOTE_CREATOR) },
                        onOpenAiAnalyst = { navController.navigate(ROUTE_AI_ANALYTICS) },
                    )
                }

                composable(ROUTE_AI_ANALYTICS) {
                    GroqAnalyticsScreen(
                        state = dataState,
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onRunPreset = viewModel::runGroqPresetAnalysis,
                        onSendQuestion = { prompt -> viewModel.sendGroqQuestion(prompt) },
                        onClearConversation = viewModel::clearAiConversation,
                        onCopiedFeedback = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.ai_copied_snackbar)
                                )
                            }
                        },
                    )
                }
            }
        }

        // Full-screen New / Edit Activity overlay
        when (val overlay = formOverlay) {
            FormOverlayState.Hidden -> Unit
            is FormOverlayState.Create -> {
                ActivityFormScreen(
                    existingActivity = null,
                    initialDate = overlay.date,
                    defaultCurrency = dataState.settings.defaultCurrency,
                    frequentSuppliers = dataState.frequentSuppliers,
                    venueSuggestions = dataState.venueSuggestions,
                    isMutating = dataState.isMutating,
                    usualRateProvider = viewModel::usualRateFor,
                    conflictChecker = viewModel::checkLocalConflict,
                    onDismiss = {
                        pendingNewTimingOverride = null
                        pendingNewFinancialRecord = ActivityFinancialRecord()
                        formOverlay = FormOverlayState.Hidden
                    },
                    onSaveNew = { draft, repeatWeeks, route, cb ->
                        val override = pendingNewTimingOverride
                        val finance = pendingNewFinancialRecord
                        viewModel.saveNewActivity(draft, repeatWeeks, route, override, finance) { res ->
                            if (res.isSuccess) {
                                pendingNewTimingOverride = null
                                pendingNewFinancialRecord = ActivityFinancialRecord()
                            }
                            cb(res)
                        }
                    },
                    onUpdateExisting = { id, seriesId, whole, draft, route, cb ->
                        viewModel.updateExistingActivity(id, seriesId, whole, draft, route, null, cb)
                    },
                    onDeleteExisting = viewModel::deleteActivity,
                    savedVenues = dataState.allSavedVenues,
                    initialCachedRoute = null,
                    venuesFeaturePending = dataState.venuesFeaturePending,
                    onSearchPlaces = viewModel::searchPlaces,
                    onFetchPlaceDetails = viewModel::fetchPlaceDetails,
                    onEstimateRoute = viewModel::estimateRouteForDestination,
                    onUpsertVenue = viewModel::upsertVenue,
                    settings = dataState.settings,
                    initialTimingOverride = pendingNewTimingOverride,
                    onTimingOverrideChanged = { pendingNewTimingOverride = it },
                    onSaveDefaultSettings = { updated -> viewModel.saveSettings(updated) {} },
                    defaultUsdToDopRate = dataState.privacyPreferences.defaultUsdToDopRate,
                    initialFinancialRecord = pendingNewFinancialRecord,
                    today = dataState.today,
                    onFinancialRecordChanged = { pendingNewFinancialRecord = it },
                )
            }

            is FormOverlayState.Edit -> {
                val existingOverride = dataState.activityTimings[overlay.activity.id]
                val existingFinance = dataState.activityFinancialRecords[overlay.activity.id]
                    ?: ActivityFinancialRecord(activityId = overlay.activity.id)
                ActivityFormScreen(
                    existingActivity = overlay.activity,
                    initialDate = overlay.activity.eventDate,
                    defaultCurrency = dataState.settings.defaultCurrency,
                    frequentSuppliers = dataState.frequentSuppliers,
                    venueSuggestions = dataState.venueSuggestions,
                    isMutating = dataState.isMutating,
                    usualRateProvider = viewModel::usualRateFor,
                    conflictChecker = viewModel::checkLocalConflict,
                    onDismiss = { formOverlay = FormOverlayState.Hidden },
                    onSaveNew = { draft, repeatWeeks, route, cb ->
                        viewModel.saveNewActivity(draft, repeatWeeks, route, null, null, cb)
                    },
                    onUpdateExisting = { id, seriesId, whole, draft, route, cb ->
                        viewModel.updateExistingActivity(
                            id,
                            seriesId,
                            whole,
                            draft,
                            route,
                            dataState.activityTimings[id],
                            cb,
                        )
                    },
                    onDeleteExisting = viewModel::deleteActivity,
                    savedVenues = dataState.allSavedVenues,
                    initialCachedRoute = dataState.activityRoutes[overlay.activity.id],
                    venuesFeaturePending = dataState.venuesFeaturePending,
                    onSearchPlaces = viewModel::searchPlaces,
                    onFetchPlaceDetails = viewModel::fetchPlaceDetails,
                    onEstimateRoute = viewModel::estimateRouteForDestination,
                    onUpsertVenue = viewModel::upsertVenue,
                    settings = dataState.settings,
                    initialTimingOverride = existingOverride,
                    onTimingOverrideChanged = { updated ->
                        viewModel.saveActivityTimingOverride(
                            activityId = overlay.activity.id,
                            override = updated,
                            showSnackbar = false,
                        )
                    },
                    onSaveDefaultSettings = { updated -> viewModel.saveSettings(updated) {} },
                    onQuickMessageSupplier = { quickMessageRequest = it to SupplierMessagePreset.ARRIVED },
                    onExportToCalendar = ::exportActivityToCalendar,
                    defaultUsdToDopRate = dataState.privacyPreferences.defaultUsdToDopRate,
                    initialFinancialRecord = existingFinance,
                    today = dataState.today,
                    onFinancialRecordChanged = { updated ->
                        viewModel.saveActivityFinancialRecord(
                            activityId = overlay.activity.id,
                            record = updated,
                            showSnackbar = false,
                        )
                    },
                )
            }
        }

        // Full-screen PDF Preview modal for Resumen de actividades (with inline content editor)
        previewStatementPdf?.let { (stmt, pdf, brandAssets) ->
            PdfPreviewDialog(
                statement = stmt,
                pdf = pdf,
                availableBankAccounts = dataState.bankAccounts,
                brandAssets = brandAssets,
                onSend = { editedStmt, editedPdf ->
                    val phone = viewModel.findSupplierPhone(editedStmt.supplierDisplayName)
                        ?: viewModel.findSupplierPhone(stmt.supplierDisplayName)
                    val shared = PdfStatementSharer.shareStatementPdf(
                        context = context,
                        pdf = editedPdf,
                        caption = editedStmt.shareCaption,
                        supplierRawPhone = phone,
                    )
                    if (shared.isFailure) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                shared.exceptionOrNull()?.message
                                    ?: context.getString(R.string.cobros_pdf_error)
                            )
                        }
                    }
                },
                onDownload = { editedPdf ->
                    pendingDownloadPdf = editedPdf
                    pendingDownloadSuccessMsg = context.getString(R.string.cobros_pdf_saved_ok)
                    savePdfLauncher.launch(editedPdf.fileName)
                },
                onDismiss = {
                    brandAssets.recycleSafely()
                    previewStatementPdf = null
                },
            )
        }

        // Full-screen Home Location Map Picker (from Settings)
        if (showHomeMapPicker) {
            VenueMapPickerScreen(
                title = stringResource(R.string.settings_home_pick_map),
                initialName = "",
                initialAddress = null,
                initialLatitude = dataState.settings.homeLatitude,
                initialLongitude = dataState.settings.homeLongitude,
                savedVenues = emptyList(),
                venuesFeaturePending = false,
                isHomePicker = true,
                onSearchPlaces = viewModel::searchPlaces,
                onFetchPlaceDetails = viewModel::fetchPlaceDetails,
                onConfirmVenue = { picked ->
                    showHomeMapPicker = false
                    viewModel.saveSettings(
                        dataState.settings.copy(
                            homeLatitude = picked.latitude,
                            homeLongitude = picked.longitude,
                        )
                    ) {}
                },
                onDismiss = { showHomeMapPicker = false },
            )
        }

        // Alerts & Departures modal sheet (from Inicio bell button)
        if (showAlertsSheet) {
            val orderedDepartures = remember(dataState.scheduledDepartures) {
                dataState.scheduledDepartures.values.sortedBy { it.leaveAtEpochMillis }
            }
            AlertsAndDeparturesSheet(
                scheduledDepartures = orderedDepartures,
                isRecalculatingRoutes = dataState.isRecalculatingRoutes,
                hasNotificationPermission = hasNotificationPermission,
                hasLocationPermission = hasLocationPermission,
                canScheduleExactAlarms = dataState.canScheduleExactAlarms,
                onRecalculateRoutes = viewModel::recalculateRoutesNow,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onDismiss = { showAlertsSheet = false },
                onTestNotification = {
                    val sent = NotificationHelper.postTestAlarmNotification(context)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (sent) context.getString(R.string.alerts_test_notification_sent)
                            else context.getString(R.string.alerts_test_notification_denied)
                        )
                    }
                },
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                onOpenBatterySettings = ::openBatteryOptimizationSettings,
                contingencyEnabled = dataState.settings.contingencyEnabled,
                contingencyLeadMinutes = dataState.settings.contingencyLeadMinutes,
                contingencyUnderControlIds = dataState.contingencyUnderControlIds,
                onSetContingencyUnderControl = viewModel::setContingencyUnderControl,
                onOpenContingencyMessage = { activityId ->
                    val target = dataState.activities.firstOrNull { it.id == activityId }
                    if (target != null) {
                        quickMessageRequest = target to SupplierMessagePreset.DELAY
                    }
                },
            )
        }

        // Quick Supplier Communication Sheet (Arrival / Delay / Finished / Open Situation)
        quickMessageRequest?.let { (targetActivity, initialPreset) ->
            val supplierDisplay = dataState.supplierResolution.displayName(targetActivity.supplierName)
            val venueDisplay = dataState.venueResolution.displayName(targetActivity.venueName)
            val supplierPhone = viewModel.findSupplierPhone(supplierDisplay)
                ?: viewModel.findSupplierPhone(targetActivity.supplierName)
            SupplierQuickMessageSheet(
                activity = targetActivity,
                supplierDisplayName = supplierDisplay,
                venueDisplayName = venueDisplay,
                supplierRawPhone = supplierPhone,
                initialPreset = initialPreset,
                onFeedback = { msg ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onDismiss = { quickMessageRequest = null },
            )
        }
    }
}

private fun checkLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun checkIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun shareCsv(context: Context, title: String, csvContent: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, "$title.csv")
        putExtra(Intent.EXTRA_TEXT, csvContent)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(sendIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}

private suspend fun loadPdfBrandAssets(
    context: Context,
    profile: ArtistBrandProfile,
): PdfBrandAssets = withContext(Dispatchers.IO) {
    PdfBrandAssets(
        logoBitmap = BrandAssetStorage.loadBitmap(context, profile.logoFileName),
        signatureBitmap = BrandAssetStorage.loadBitmap(context, profile.signatureFileName),
        artistSubtitle = profile.subtitle.trim().takeIf { it.isNotEmpty() },
        contactLine = profile.contactLine.trim().takeIf { it.isNotEmpty() },
    )
}

private fun PdfBrandAssets.recycleSafely() {
    runCatching {
        if (logoBitmap != null && !logoBitmap.isRecycled) logoBitmap.recycle()
    }
    runCatching {
        if (signatureBitmap != null && !signatureBitmap.isRecycled) signatureBitmap.recycle()
    }
}

/**
 * Opens WhatsApp with [message] pre-filled. With a known phone it opens that chat; without one the
 * message is handed to WhatsApp (or the system share sheet) so the user picks the contact.
 * Returns false when the phone is unknown and WhatsApp had to ask for the contact.
 */
private fun openWhatsApp(context: Context, rawPhone: String?, message: String): Boolean {
    val url = WhatsAppReminderBuilder.buildWaMeUrl(rawPhone, message)
    if (url != null) {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) return true
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    return url != null
}

private fun requestPinNextGigWidget(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, NextGigWidgetProvider::class.java)
        manager.requestPinAppWidget(provider, null, null)
    }.getOrDefault(false)
}
