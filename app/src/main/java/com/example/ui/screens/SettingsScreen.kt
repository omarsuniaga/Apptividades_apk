package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.port.AppThemeMode
import com.example.domain.model.Currency
import com.example.domain.model.GroqAiSettings
import com.example.domain.model.GroqModelOption
import com.example.domain.model.LabSettings
import com.example.domain.rules.AuthInputValidator
import com.example.domain.rules.CurrencyConverter
import com.example.ui.components.GroqSettingsCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import java.math.BigDecimal
import java.util.Locale

private enum class PermissionRationaleType {
    NOTIFICATIONS,
    LOCATION,
    LOCATION_FOR_HOME,
    EXACT_ALARM,
}

private enum class SettingsCategory(val labelRes: Int, val testTag: String) {
    GENERAL(R.string.settings_tab_general, "settings_tab_general"),
    TIMINGS(R.string.settings_tab_timings, "settings_tab_timings"),
    ALERTS(R.string.settings_tab_alerts, "settings_tab_alerts"),
    SECURITY(R.string.settings_tab_security, "settings_tab_security"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: LabSettings,
    userEmail: String,
    isMutating: Boolean,
    onBack: () -> Unit,
    onSaveSettings: (LabSettings) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    bankAccountsCount: Int = 0,
    loginHistoryCount: Int = 0,
    hasNotificationPermission: Boolean = true,
    hasLocationPermission: Boolean = false,
    canScheduleExactAlarms: Boolean = true,
    isIgnoringBatteryOptimizations: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onRequestLocationPermission: (captureHomeAfterGrant: Boolean) -> Unit = {},
    onRequestExactAlarmPermission: () -> Unit = {},
    onOpenBatteryOptimizationSettings: () -> Unit = {},
    onUseCurrentLocationAsHome: () -> Unit = {},
    onPickHomeOnMap: () -> Unit = {},
    onOpenBankAccounts: () -> Unit = {},
    onOpenLoginHistory: () -> Unit = {},
    onChangePassword: (newPassword: String, confirmPassword: String, onResult: (Result<Unit>) -> Unit) -> Unit = { _, _, _ -> },
    screenPrivacyEnabled: Boolean = false,
    resolveExternalIpInHistory: Boolean = false,
    appLockEnabled: Boolean = false,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onToggleScreenPrivacy: (Boolean) -> Unit = {},
    onToggleResolveExternalIp: (Boolean) -> Unit = {},
    onToggleAppLock: (Boolean) -> Unit = {},
    onSelectThemeMode: (AppThemeMode) -> Unit = {},
    defaultUsdToDopRate: BigDecimal = CurrencyConverter.DEFAULT_USD_TO_DOP_RATE,
    convertMixedCurrencies: Boolean = true,
    onSaveDefaultUsdToDopRate: (BigDecimal) -> Unit = {},
    onToggleConvertMixedCurrencies: (Boolean) -> Unit = {},
    brandProfileConfigured: Boolean = false,
    onOpenBrandProfile: () -> Unit = {},
    onPinWidget: () -> Unit = {},
    onDeleteAccount: (onResult: (Result<Unit>) -> Unit) -> Unit = {},
    groqAiSettings: GroqAiSettings = GroqAiSettings(),
    onSaveGroqApiKey: (rawKey: String, onResult: (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    onClearGroqApiKey: () -> Unit = {},
    onSelectGroqModel: (GroqModelOption) -> Unit = {},
    onToggleGroqAnonymize: (Boolean) -> Unit = {},
    onTestGroqConnection: (overrideKey: String?, onResult: (Result<String>) -> Unit) -> Unit = { _, _ -> },
    onOpenAiAnalyst: () -> Unit = {},
) {
    val colors = ApptividadesThemeDefaults.colors
    val haptic = LocalHapticFeedback.current

    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.GENERAL) }
    var currency by remember(settings) { mutableStateOf(settings.defaultCurrency) }
    var monthlyGoalText by remember(settings) {
        mutableStateOf(settings.monthlyGoal.stripTrailingZeros().toPlainString())
    }
    var exchangeRateText by remember(defaultUsdToDopRate) {
        mutableStateOf(defaultUsdToDopRate.stripTrailingZeros().toPlainString())
    }
    var setupText by remember(settings) { mutableStateOf(settings.setupMinutes.toString()) }
    var internalInText by remember(settings) { mutableStateOf(settings.internalTransferInMinutes.toString()) }
    var preMarginText by remember(settings) { mutableStateOf(settings.preStartMarginMinutes.toString()) }
    var eventText by remember(settings) { mutableStateOf(settings.eventMinutes.toString()) }
    var teardownText by remember(settings) { mutableStateOf(settings.teardownMinutes.toString()) }
    var internalOutText by remember(settings) { mutableStateOf(settings.internalTransferOutMinutes.toString()) }
    var prepText by remember(settings) { mutableStateOf(settings.prepMinutes.toString()) }
    var travelBufferText by remember(settings) {
        mutableStateOf(settings.travelBufferMinutes.toString())
    }
    var contingencyEnabled by remember(settings) { mutableStateOf(settings.contingencyEnabled) }
    var contingencyLeadMinutes by remember(settings) { mutableStateOf(settings.contingencyLeadMinutes) }
    var scoreReminderDays by remember(settings) { mutableStateOf(settings.scoreReminderDays) }

    var activeRationale by remember { mutableStateOf<PermissionRationaleType?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val saveCurrentSettings = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val goal = monthlyGoalText.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
            ?: settings.monthlyGoal
        val setup = (setupText.toIntOrNull() ?: settings.setupMinutes).coerceIn(0, 240)
        val internalIn = (internalInText.toIntOrNull() ?: settings.internalTransferInMinutes).coerceIn(0, 180)
        val preMargin = (preMarginText.toIntOrNull() ?: settings.preStartMarginMinutes).coerceIn(0, 180)
        val event = (eventText.toIntOrNull() ?: settings.eventMinutes).coerceIn(1, 1440)
        val teardown = (teardownText.toIntOrNull() ?: settings.teardownMinutes).coerceIn(0, 240)
        val internalOut = (internalOutText.toIntOrNull() ?: settings.internalTransferOutMinutes).coerceIn(0, 180)
        val prep = (prepText.toIntOrNull() ?: settings.prepMinutes).coerceIn(0, 240)
        val travelBuffer = (travelBufferText.toIntOrNull() ?: settings.travelBufferMinutes).coerceIn(0, 120)
        val parsedRate = exchangeRateText.replace(",", ".").trim().toBigDecimalOrNull()
            ?.takeIf { it >= BigDecimal.ONE && it <= BigDecimal("500") }
            ?: defaultUsdToDopRate
        onSaveDefaultUsdToDopRate(parsedRate)
        onSaveSettings(
            settings.copy(
                defaultCurrency = currency,
                monthlyGoal = goal,
                setupMinutes = setup,
                internalTransferInMinutes = internalIn,
                preStartMarginMinutes = preMargin,
                eventMinutes = event,
                teardownMinutes = teardown,
                internalTransferOutMinutes = internalOut,
                prepMinutes = prep,
                travelBufferMinutes = travelBuffer,
                scoreReminderDays = scoreReminderDays,
                contingencyEnabled = contingencyEnabled,
                contingencyLeadMinutes = contingencyLeadMinutes,
            )
        )
    }

    val hasAlertPermissionsWarning = !canScheduleExactAlarms || !hasNotificationPermission

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                testTag = "settings_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
            )
        }

        // Editorial 4-category segmented bar: General · Tiempos · Alertas · Seguridad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsCategory.entries.forEach { category ->
                        val active = selectedCategory == category
                        val showWarningDot = category == SettingsCategory.ALERTS && hasAlertPermissionsWarning
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedCategory = category
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) colors.ink else colors.surface,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag(category.testTag),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(category.labelRes),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (active) colors.surface else colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (showWarningDot) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.danger),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Prominent RED warning when Exact Alarm permission is missing (always visible across tabs)
                if (!canScheduleExactAlarms) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.5.dp, colors.dangerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_exact_alarm_red_alert"),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = colors.dangerText,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = stringResource(R.string.settings_exact_alarm_red_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.dangerText,
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_exact_alarm_red_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.dangerText,
                            )
                            Button(
                                onClick = onRequestExactAlarmPermission,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.dangerText,
                                    contentColor = colors.surface,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_exact_alarm_system_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Alarm,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_exact_alarm_open_system),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Prominent RED warning when Android 13+ POST_NOTIFICATIONS is missing (always visible across tabs)
                if (!hasNotificationPermission) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.5.dp, colors.dangerBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_notifications_red_alert"),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    tint = colors.dangerText,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = stringResource(R.string.settings_notif_red_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.dangerText,
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_notif_red_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.dangerText,
                            )
                            Button(
                                onClick = onRequestNotificationPermission,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.dangerText,
                                    contentColor = colors.surface,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_notifications_enable_btn"),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_perm_enable),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
                    },
                    label = "settings_category_content",
                ) { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (category) {
                            SettingsCategory.GENERAL -> {
                                // Appearance (Editorial Light / Backstage Dark / System) card
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.line),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_appearance_card"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = stringResource(R.string.settings_section_appearance),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.ink,
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_appearance_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.muted,
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            listOf(
                                                Triple(AppThemeMode.SYSTEM, R.string.settings_theme_system, Icons.Outlined.BrightnessAuto),
                                                Triple(AppThemeMode.LIGHT, R.string.settings_theme_light, Icons.Outlined.LightMode),
                                                Triple(AppThemeMode.DARK, R.string.settings_theme_dark, Icons.Outlined.DarkMode),
                                            ).forEach { (mode, labelRes, icon) ->
                                                val selected = themeMode == mode
                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSelectThemeMode(mode)
                                                    },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (selected) colors.ink else colors.neutralChipBg,
                                                    border = if (selected) null else BorderStroke(1.dp, colors.line),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(52.dp)
                                                        .testTag("settings_theme_${mode.storageValue}"),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = null,
                                                            tint = if (selected) colors.surface else colors.ink,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = stringResource(labelRes),
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                            ),
                                                            color = if (selected) colors.surface else colors.ink,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                // Phase 3: Bank Accounts entry card
                Surface(
                    onClick = onOpenBankAccounts,
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_bank_accounts_card"),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalance,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_bank_accounts_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                            )
                            Text(
                                text = if (bankAccountsCount > 0) {
                                    "$bankAccountsCount configurada${if (bankAccountsCount == 1) "" else "s"} · ${stringResource(R.string.settings_bank_accounts_subtitle)}"
                                } else {
                                    stringResource(R.string.settings_bank_accounts_subtitle)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.muted,
                        )
                    }
                }

                // Personal Brand (Logo & Signature for PDFs and Quotes)
                Surface(
                    onClick = onOpenBrandProfile,
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_brand_profile_card"),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_brand_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (brandProfileConfigured) {
                                    StatusPill(
                                        label = stringResource(R.string.settings_brand_configured_badge),
                                        backgroundColor = colors.successBg,
                                        textColor = colors.successText,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.settings_brand_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.muted,
                        )
                    }
                }

                // Home Screen Widget pin card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_widget_pin_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_widget_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.settings_widget_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPinWidget()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_widget_pin_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_widget_pin_cta),
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // Groq AI configuration card (API Key encrypted in Keystore + Model + Privacy)
                GroqSettingsCard(
                    groqAiSettings = groqAiSettings,
                    onSaveApiKey = onSaveGroqApiKey,
                    onClearApiKey = onClearGroqApiKey,
                    onSelectModel = onSelectGroqModel,
                    onToggleAnonymize = onToggleGroqAnonymize,
                    onTestConnection = onTestGroqConnection,
                    onOpenAiAnalyst = onOpenAiAnalyst,
                )

                // Work preferences card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_general),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_currency_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.muted,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Currency.entries.forEach { curr ->
                                    val active = currency == curr
                                    Surface(
                                        onClick = { currency = curr },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (active) colors.ink else colors.neutralChipBg,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("settings_currency_${curr.code.lowercase()}"),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${curr.symbol} (${curr.code})",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontFamily = SpaceGroteskFontFamily,
                                                ),
                                                color = if (active) colors.surface else colors.ink,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = monthlyGoalText,
                                onValueChange = { next ->
                                    if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                        monthlyGoalText = next
                                    }
                                },
                                label = {
                                    Text("${stringResource(R.string.settings_monthly_goal_label)} (${currency.symbol})")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.ink,
                                    unfocusedBorderColor = colors.line,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_monthly_goal_input"),
                            )
                            if (!settings.monthlyGoalSyncedOnServer) {
                                Text(
                                    text = stringResource(R.string.settings_monthly_goal_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                        }
                    }
                }

                // Multi-currency & historical exchange rate card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_exchange_rate_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_exchange_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )

                        OutlinedTextField(
                            value = exchangeRateText,
                            onValueChange = { next ->
                                if (next.isEmpty() || next.matches(Regex("^\\d{0,3}\\.?\\d{0,2}$"))) {
                                    exchangeRateText = next
                                }
                            },
                            label = { Text(stringResource(R.string.settings_exchange_rate_label)) },
                            prefix = {
                                Text(
                                    text = "RD$ ",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = colors.muted,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_exchange_rate_input"),
                        )

                        HorizontalDivider(thickness = 1.dp, color = colors.line)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_exchange_unify_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.settings_exchange_unify_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                            Switch(
                                checked = convertMixedCurrencies,
                                onCheckedChange = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleConvertMixedCurrencies(enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surface,
                                    checkedTrackColor = colors.accent,
                                ),
                                modifier = Modifier.testTag("settings_convert_mixed_currencies_switch"),
                            )
                        }
                    }
                }
                                SettingsSaveButton(isMutating = isMutating, onClick = saveCurrentSettings)
                            }

                            SettingsCategory.TIMINGS -> {
                // Durations card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_durations),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        DurationField(
                            label = stringResource(R.string.settings_internal_transfer_in_minutes),
                            value = internalInText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) internalInText = it },
                            testTag = "settings_internal_in_input",
                        )
                        DurationField(
                            label = stringResource(R.string.settings_setup_minutes),
                            value = setupText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) setupText = it },
                            testTag = "settings_setup_input",
                        )
                        DurationField(
                            label = stringResource(R.string.settings_pre_start_margin_minutes),
                            value = preMarginText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) preMarginText = it },
                            testTag = "settings_pre_margin_input",
                        )
                        DurationField(
                            label = stringResource(R.string.settings_event_minutes),
                            value = eventText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) eventText = it },
                            testTag = "settings_event_input",
                        )
                        DurationField(
                            label = stringResource(R.string.settings_teardown_minutes),
                            value = teardownText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) teardownText = it },
                            testTag = "settings_teardown_input",
                        )
                        DurationField(
                            label = stringResource(R.string.settings_internal_transfer_out_minutes),
                            value = internalOutText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) internalOutText = it },
                            testTag = "settings_internal_out_input",
                        )
                    }
                }

                // Departures & Home Location card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_departures),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        DurationField(
                            label = stringResource(R.string.settings_prep_minutes),
                            value = prepText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) prepText = it },
                            testTag = "settings_prep_input",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DurationField(
                                label = stringResource(R.string.settings_travel_buffer_minutes),
                                value = travelBufferText,
                                onValueChange = { if (it.all { c -> c.isDigit() }) travelBufferText = it },
                                testTag = "settings_travel_buffer_input",
                            )
                            Text(
                                text = stringResource(R.string.settings_travel_buffer_peak_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.line)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.settings_home_location_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.muted,
                            )
                            Text(
                                text = if (settings.homeLatitude != null && settings.homeLongitude != null) {
                                    String.format(
                                        Locale.US,
                                        "%.5f, %.5f",
                                        settings.homeLatitude,
                                        settings.homeLongitude,
                                    )
                                } else {
                                    stringResource(R.string.settings_home_not_set)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (hasLocationPermission) {
                                            onUseCurrentLocationAsHome()
                                        } else {
                                            activeRationale = PermissionRationaleType.LOCATION_FOR_HOME
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_home_current_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MyLocation,
                                        contentDescription = null,
                                        tint = colors.ink,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        text = stringResource(R.string.settings_home_set_current),
                                        color = colors.ink,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }
                                OutlinedButton(
                                    onClick = onPickHomeOnMap,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_home_map_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Map,
                                        contentDescription = null,
                                        tint = colors.ink,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(
                                        text = stringResource(R.string.settings_home_pick_map),
                                        color = colors.ink,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                                SettingsSaveButton(isMutating = isMutating, onClick = saveCurrentSettings)
                            }

                            SettingsCategory.ALERTS -> {
                // Reminders & Modo Contingencia card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reminders_contingency_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_reminders_contingency),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )

                        // Modo Contingencia toggle + lead minutes
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_contingency_toggle),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = contingencyEnabled,
                                    onCheckedChange = { contingencyEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surface,
                                        checkedTrackColor = colors.danger,
                                    ),
                                    modifier = Modifier.testTag("settings_contingency_switch"),
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_contingency_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                            if (contingencyEnabled) {
                                Text(
                                    text = stringResource(R.string.settings_contingency_lead_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.muted,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(15, 20, 30, 45, 60).forEach { lead ->
                                        val selected = contingencyLeadMinutes == lead
                                        Surface(
                                            onClick = { contingencyLeadMinutes = lead },
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (selected) colors.ink else colors.neutralChipBg,
                                            modifier = Modifier
                                                .height(40.dp)
                                                .testTag("settings_contingency_lead_$lead"),
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.form_duration_chip_min, lead),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    ),
                                                    color = if (selected) colors.surface else colors.ink,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.line)

                        // Score / repertoire reminder days (independent CHANNEL_SCORES)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_score_reminder_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.muted,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(0, 1, 2, 3, 5, 7).forEach { days ->
                                    val selected = scoreReminderDays == days
                                    Surface(
                                        onClick = { scoreReminderDays = days },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (selected) colors.ink else colors.neutralChipBg,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .testTag("settings_score_reminder_$days"),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                        ) {
                                            Text(
                                                text = if (days == 0) {
                                                    stringResource(R.string.settings_score_reminder_off)
                                                } else {
                                                    stringResource(R.string.settings_score_reminder_days_chip, days)
                                                },
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                ),
                                                color = if (selected) colors.surface else colors.ink,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = colors.line)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_receivables_monday_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    }
                }

                // Permissions & Battery Optimization card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_permissions_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        PermissionStatusRow(
                            icon = Icons.Outlined.Notifications,
                            label = stringResource(R.string.settings_perm_notifications),
                            granted = hasNotificationPermission,
                            deniedSubtitle = stringResource(R.string.settings_notif_red_body),
                            onEnable = { activeRationale = PermissionRationaleType.NOTIFICATIONS },
                            testTag = "settings_perm_notifications_btn",
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                        PermissionStatusRow(
                            icon = Icons.Outlined.MyLocation,
                            label = stringResource(R.string.settings_perm_location),
                            granted = hasLocationPermission,
                            deniedSubtitle = null,
                            onEnable = { activeRationale = PermissionRationaleType.LOCATION },
                            testTag = "settings_perm_location_btn",
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                        PermissionStatusRow(
                            icon = Icons.Outlined.Alarm,
                            label = stringResource(R.string.settings_perm_exact_alarm),
                            granted = canScheduleExactAlarms,
                            deniedSubtitle = stringResource(R.string.settings_perm_inexact_fallback),
                            onEnable = { activeRationale = PermissionRationaleType.EXACT_ALARM },
                            testTag = "settings_perm_exact_alarm_btn",
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                        // Battery optimization recommendation (Xiaomi, Samsung, Huawei)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (isIgnoringBatteryOptimizations) colors.success else colors.accentHover,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.settings_battery_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusPill(
                                    label = stringResource(
                                        if (isIgnoringBatteryOptimizations) R.string.settings_battery_ok
                                        else R.string.settings_battery_restricted
                                    ),
                                    backgroundColor = if (isIgnoringBatteryOptimizations) colors.successBg else colors.accentSoft,
                                    textColor = if (isIgnoringBatteryOptimizations) colors.successText else colors.accentHover,
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_battery_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                            OutlinedButton(
                                onClick = onOpenBatteryOptimizationSettings,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_battery_open_btn"),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_battery_open),
                                    color = colors.ink,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                                SettingsSaveButton(isMutating = isMutating, onClick = saveCurrentSettings)
                            }

                            SettingsCategory.SECURITY -> {
                // Privacy & Screen Protection card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_privacy_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_privacy),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_app_lock_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.settings_app_lock_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                            Switch(
                                checked = appLockEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleAppLock(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surface,
                                    checkedTrackColor = colors.accent,
                                ),
                                modifier = Modifier.testTag("settings_app_lock_switch"),
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_screen_privacy_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.settings_screen_privacy_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                            Switch(
                                checked = screenPrivacyEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleScreenPrivacy(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surface,
                                    checkedTrackColor = colors.accent,
                                ),
                                modifier = Modifier.testTag("settings_screen_privacy_switch"),
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_external_ip_title),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.ink,
                                )
                                Text(
                                    text = stringResource(R.string.settings_external_ip_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted,
                                )
                            }
                            Switch(
                                checked = resolveExternalIpInHistory,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleResolveExternalIp(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surface,
                                    checkedTrackColor = colors.accent,
                                ),
                                modifier = Modifier.testTag("settings_external_ip_switch"),
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.settings_keystore_active_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    }
                }

                // Account & Login Session History card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_section_account),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        if (userEmail.isNotBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        }
                        OutlinedButton(
                            onClick = { showChangePasswordDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_change_password_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LockReset,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_change_password_button),
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenLoginHistory,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_login_history_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = colors.ink,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (loginHistoryCount > 0) {
                                    "${stringResource(R.string.settings_login_history_title)} ($loginHistoryCount)"
                                } else {
                                    stringResource(R.string.settings_login_history_title)
                                },
                                color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.dangerBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.dangerBg,
                                contentColor = colors.dangerText,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_sign_out_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.settings_sign_out),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(
                            onClick = { showDeleteAccountDialog = true },
                            enabled = !isMutating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_delete_account_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                tint = colors.dangerText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_delete_account_button),
                                color = colors.dangerText,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Contextual permission rationale dialog
    activeRationale?.let { type ->
        val bodyRes = when (type) {
            PermissionRationaleType.NOTIFICATIONS -> R.string.perm_rationale_notifications
            PermissionRationaleType.LOCATION,
            PermissionRationaleType.LOCATION_FOR_HOME -> R.string.perm_rationale_location
            PermissionRationaleType.EXACT_ALARM -> R.string.perm_rationale_exact_alarm
        }
        AlertDialog(
            onDismissRequest = { activeRationale = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.perm_rationale_title),
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(bodyRes),
                    color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeRationale = null
                        when (type) {
                            PermissionRationaleType.NOTIFICATIONS -> onRequestNotificationPermission()
                            PermissionRationaleType.LOCATION -> onRequestLocationPermission(false)
                            PermissionRationaleType.LOCATION_FOR_HOME -> onRequestLocationPermission(true)
                            PermissionRationaleType.EXACT_ALARM -> onRequestExactAlarmPermission()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_perm_enable),
                        color = colors.accentHover,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { activeRationale = null }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            isMutating = isMutating,
            onDismiss = { showChangePasswordDialog = false },
            onSubmit = { newPwd, confirmPwd, onError ->
                onChangePassword(newPwd, confirmPwd) { result ->
                    result.onSuccess {
                        showChangePasswordDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "No se pudo actualizar la contraseña.")
                    }
                }
            },
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            isMutating = isMutating,
            onDismiss = { showDeleteAccountDialog = false },
            onConfirmDelete = { onError ->
                onDeleteAccount { result ->
                    result.onSuccess {
                        showDeleteAccountDialog = false
                    }.onFailure { err ->
                        onError(err.message ?: "No se pudieron eliminar tus datos.")
                    }
                }
            },
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: (onError: (String) -> Unit) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val confirmWord = stringResource(R.string.delete_account_confirm_word)
    var typedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canDelete = typedText.trim().equals(confirmWord, ignoreCase = true) && !isMutating

    AlertDialog(
        onDismissRequest = { if (!isMutating) onDismiss() },
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.delete_account_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.dangerText,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.delete_account_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                    )
                }
                OutlinedTextField(
                    value = typedText,
                    onValueChange = {
                        typedText = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.delete_account_confirm_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.dangerText,
                        unfocusedBorderColor = colors.dangerBorder,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_account_confirm_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete { err -> errorMessage = err }
                },
                enabled = canDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.dangerText,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("delete_account_confirm_button"),
            ) {
                if (isMutating) {
                    CircularProgressIndicator(
                        color = colors.surface,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.delete_account_confirm_cta),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMutating) {
                Text(stringResource(R.string.form_cancel), color = colors.muted)
            }
        },
    )
}

@Composable
private fun ChangePasswordDialog(
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (newPassword: String, confirmPassword: String, onError: (String) -> Unit) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    // Ephemeral UI-only state; never saved or persisted anywhere.
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val strengthCheck = remember(newPassword) { AuthInputValidator.checkPasswordStrength(newPassword) }

    AlertDialog(
        onDismissRequest = {
            if (!isMutating) {
                newPassword = ""
                confirmPassword = ""
                onDismiss()
            }
        },
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.settings_change_password_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                    )
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.settings_change_password_new_label)) },
                    singleLine = true,
                    visualTransformation = if (showPasswords) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPasswords = !showPasswords }) {
                            Icon(
                                imageVector = if (showPasswords) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(
                                    if (showPasswords) R.string.login_hide_password
                                    else R.string.login_show_password
                                ),
                                tint = colors.muted,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_new_password_input"),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.settings_change_password_confirm_label)) },
                    singleLine = true,
                    visualTransformation = if (showPasswords) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_confirm_password_input"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsPasswordRule(stringResource(R.string.signup_rule_length), strengthCheck.hasMinLength)
                    SettingsPasswordRule(stringResource(R.string.signup_rule_letter), strengthCheck.hasLetter)
                    SettingsPasswordRule(stringResource(R.string.signup_rule_digit), strengthCheck.hasDigit)
                    SettingsPasswordRule(
                        stringResource(R.string.signup_rule_match),
                        confirmPassword.isNotEmpty() && newPassword == confirmPassword,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val localErr = AuthInputValidator.validatePasswordChange(newPassword, confirmPassword)
                    if (localErr != null) {
                        errorMessage = localErr
                    } else {
                        onSubmit(newPassword, confirmPassword) { err ->
                            errorMessage = err
                        }
                    }
                },
                enabled = !isMutating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("settings_change_password_submit"),
            ) {
                if (isMutating) {
                    CircularProgressIndicator(
                        color = colors.surface,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(stringResource(R.string.settings_change_password_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    newPassword = ""
                    confirmPassword = ""
                    onDismiss()
                },
                enabled = !isMutating,
            ) {
                Text(stringResource(R.string.form_cancel), color = colors.muted)
            }
        },
    )
}

@Composable
private fun SettingsPasswordRule(label: String, satisfied: Boolean) {
    val colors = ApptividadesThemeDefaults.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (satisfied) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (satisfied) colors.success else colors.muted,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (satisfied) colors.successText else colors.muted,
        )
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    deniedSubtitle: String?,
    onEnable: () -> Unit,
    testTag: String,
) {
    val colors = ApptividadesThemeDefaults.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) colors.success else colors.dangerText,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            if (!granted && !deniedSubtitle.isNullOrBlank()) {
                Text(
                    text = deniedSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerText,
                )
            }
        }
        if (granted) {
            StatusPill(
                label = stringResource(R.string.settings_perm_granted),
                backgroundColor = colors.successBg,
                textColor = colors.successText,
            )
        } else {
            TextButton(
                onClick = onEnable,
                modifier = Modifier.testTag(testTag),
            ) {
                Text(
                    text = stringResource(R.string.settings_perm_enable),
                    color = colors.accentHover,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
) {
    val colors = ApptividadesThemeDefaults.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.ink,
            unfocusedBorderColor = colors.line,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
private fun SettingsSaveButton(
    isMutating: Boolean,
    onClick: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    Button(
        onClick = onClick,
        enabled = !isMutating,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.ink,
            contentColor = colors.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("settings_save_button"),
    ) {
        if (isMutating) {
            CircularProgressIndicator(
                color = colors.surface,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.settings_save),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
