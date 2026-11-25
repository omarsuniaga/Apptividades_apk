package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Activity
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.ContingencyAssessment
import com.example.domain.rules.ContingencyEvaluator
import com.example.domain.rules.ContingencyState
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.LiveStageCalculator
import com.example.domain.rules.MonthOverview
import com.example.domain.rules.ReceivableGroup
import com.example.domain.rules.SupplierMessagePreset
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LiveStageCard
import com.example.ui.components.LoadingStateBlock
import com.example.ui.components.NavigationActionButtons
import com.example.ui.components.OfflineSyncBanner
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AppDataState
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.Instant
import java.time.ZonedDateTime

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InicioScreen(
    state: AppDataState,
    userDisplayName: String?,
    onOpenCobros: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenActivity: (Activity) -> Unit,
    onNewActivity: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAlerts: () -> Unit = {},
    onQuickMessageSupplier: (Activity) -> Unit = {},
    onQuickMessageSupplierWithPreset: (Activity, SupplierMessagePreset) -> Unit = { a, _ -> onQuickMessageSupplier(a) },
    onSetContingencyUnderControl: (String, Boolean) -> Unit = { _, _ -> },
    onOpenQuoteCreator: () -> Unit = {},
    onOpenAiAnalyst: () -> Unit = {},
) {
    val colors = ApptividadesThemeDefaults.colors
    val haptic = LocalHapticFeedback.current
    val overview = state.monthOverview
    val receivables = state.receivablesSummary
    val upcoming = state.upcomingActivities
    var showQuickMenu by remember { mutableStateOf(false) }

    var nowLocal by remember { mutableStateOf(ZonedDateTime.now(SANTO_DOMINGO_ZONE).toLocalDateTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowLocal = ZonedDateTime.now(SANTO_DOMINGO_ZONE).toLocalDateTime()
        }
    }

    val activeContingencyAlerts = remember(
        upcoming,
        nowLocal,
        state.settings.contingencyEnabled,
        state.settings.contingencyLeadMinutes,
        state.contingencyUnderControlIds,
    ) {
        if (!state.settings.contingencyEnabled) emptyList()
        else upcoming.mapNotNull { act ->
            val time = act.eventTime ?: return@mapNotNull null
            if (act.eventDate != state.today) return@mapNotNull null
            val assessment = ContingencyEvaluator.evaluate(
                now = nowLocal,
                eventStart = act.eventDate.atTime(time),
                contingencyEnabled = true,
                leadMinutes = state.settings.contingencyLeadMinutes,
                isMarkedUnderControl = act.id in state.contingencyUnderControlIds,
                venueLat = act.latitude,
                venueLng = act.longitude,
                currentLat = null,
                currentLng = null,
            )
            if (assessment.state == ContingencyState.ACTIVE_ALERT) act to assessment else null
        }
    }

    val featuredToday = remember(
        state.activities,
        state.today,
        nowLocal,
        state.settings,
        state.activityRoutes,
        state.activityTimings,
    ) {
        LiveStageCalculator.selectFeaturedTodayActivity(
            now = nowLocal,
            today = state.today,
            activities = state.activities,
            settings = state.settings,
            routes = state.activityRoutes,
            timings = state.activityTimings,
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading && state.activities.isNotEmpty(),
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRefresh()
        },
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Editorial header: generous greeting + Alerts bell (with dot) + Quick Menu
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DominicanFormatter.formatFullDateHeader(state.today),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!userDisplayName.isNullOrBlank()) {
                                stringResource(R.string.inicio_greeting_named, userDisplayName)
                            } else {
                                stringResource(R.string.inicio_greeting_default)
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val hasAlertIndicator = state.scheduledDepartures.isNotEmpty() || activeContingencyAlerts.isNotEmpty()
                        Box {
                            UtilityIconTile(
                                icon = Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.inicio_notifications_cd),
                                onClick = onOpenAlerts,
                                testTag = "inicio_alerts_button",
                                borderColor = if (hasAlertIndicator) colors.accent.copy(alpha = 0.45f) else colors.line,
                                backgroundColor = if (hasAlertIndicator) colors.accentSoft else colors.surface,
                                tint = if (hasAlertIndicator) colors.accentHover else colors.ink,
                                modifier = Modifier.size(48.dp),
                            )
                            if (hasAlertIndicator) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (activeContingencyAlerts.isNotEmpty()) colors.danger else colors.accent),
                                )
                            }
                        }

                        Box {
                            UtilityIconTile(
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.inicio_quick_menu_cd),
                                onClick = { showQuickMenu = true },
                                testTag = "inicio_quick_menu_button",
                                borderColor = colors.line,
                                backgroundColor = colors.surface,
                                tint = colors.ink,
                                modifier = Modifier.size(48.dp),
                            )
                            DropdownMenu(
                                expanded = showQuickMenu,
                                onDismissRequest = { showQuickMenu = false },
                                containerColor = colors.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, colors.line),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.nav_suppliers),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = colors.ink,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Groups,
                                            contentDescription = null,
                                            tint = colors.ink,
                                        )
                                    },
                                    onClick = {
                                        showQuickMenu = false
                                        onOpenSuppliers()
                                    },
                                    modifier = Modifier.testTag("inicio_menu_suppliers"),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.nav_quotes),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = colors.ink,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.RequestQuote,
                                            contentDescription = null,
                                            tint = colors.accentHover,
                                        )
                                    },
                                    onClick = {
                                        showQuickMenu = false
                                        onOpenQuoteCreator()
                                    },
                                    modifier = Modifier.testTag("inicio_menu_quotes"),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.nav_reports),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = colors.ink,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = colors.ink,
                                        )
                                    },
                                    onClick = {
                                        showQuickMenu = false
                                        onOpenReports()
                                    },
                                    modifier = Modifier.testTag("inicio_menu_reports"),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.nav_ai_analyst),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = colors.ink,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = colors.accentHover,
                                        )
                                    },
                                    onClick = {
                                        showQuickMenu = false
                                        onOpenAiAnalyst()
                                    },
                                    modifier = Modifier.testTag("inicio_menu_ai"),
                                )
                                HorizontalDivider(thickness = 1.dp, color = colors.line)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.nav_settings),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = colors.ink,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = null,
                                            tint = colors.ink,
                                        )
                                    },
                                    onClick = {
                                        showQuickMenu = false
                                        onOpenSettings()
                                    },
                                    modifier = Modifier.testTag("inicio_menu_settings"),
                                )
                            }
                        }
                    }
                }
            }

            if (!state.isOnline || state.isOfflineCacheActive || state.pendingSyncCount > 0) {
                item(key = "inicio_offline_sync_banner") {
                    OfflineSyncBanner(
                        isOnline = state.isOnline,
                        isOfflineCacheActive = state.isOfflineCacheActive,
                        lastSyncedAtMillis = state.lastSyncedAtMillis,
                        pendingSyncCount = state.pendingSyncCount,
                        isSyncing = state.isSyncingPending || state.isLoading,
                        onSyncNow = onRefresh,
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    ErrorBanner(message = state.errorMessage, onRetry = onRefresh)
                }
            }

            if (state.isLoading && state.activities.isEmpty()) {
                item { LoadingStateBlock() }
            } else {
                // Live Modo Contingencia urgent banners when a gig is about to start and unconfirmed
                if (activeContingencyAlerts.isNotEmpty()) {
                    items(activeContingencyAlerts, key = { "contingency_${it.first.id}" }) { (act, assessment) ->
                        ContingencyAlertCard(
                            activity = act,
                            venueDisplay = state.venueResolution.displayName(act.venueName),
                            assessment = assessment,
                            onNotifyDelay = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQuickMessageSupplierWithPreset(act, SupplierMessagePreset.DELAY)
                            },
                            onConfirmUnderControl = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetContingencyUnderControl(act.id, true)
                            },
                        )
                    }
                }

                // Editorial Studio "En vivo hoy" card when the artist has a timed gig today
                if (featuredToday != null) {
                    item(key = "live_stage_${featuredToday.activity.id}") {
                        val act = featuredToday.activity
                        val showControl = state.settings.contingencyEnabled &&
                            act.eventTime != null &&
                            act.eventDate.atTime(act.eventTime).isAfter(nowLocal)
                        LiveStageCard(
                            featured = featuredToday,
                            venueDisplay = state.venueResolution.displayName(act.venueName),
                            supplierDisplay = state.supplierResolution.displayName(act.supplierName),
                            showContingencyControl = showControl,
                            isUnderControl = act.id in state.contingencyUnderControlIds,
                            onOpenActivity = { onOpenActivity(act) },
                            onQuickMessage = { preset ->
                                if (preset != null) {
                                    onQuickMessageSupplierWithPreset(act, preset)
                                } else {
                                    onQuickMessageSupplier(act)
                                }
                            },
                            onToggleUnderControl = { underControl ->
                                onSetContingencyUnderControl(act.id, underControl)
                            },
                        )
                    }
                }

                // Dark hero summary card
                item {
                    DarkMonthSummaryCard(overview = overview)
                }

                // Overdue alert card (only when overdue > 0)
                if (receivables.overdueTotal > BigDecimal.ZERO) {
                    item {
                        OverdueAlertCard(
                            overdueTotal = DominicanFormatter.formatMoney(
                                receivables.overdueTotal,
                                receivables.defaultCurrency,
                            ),
                            overdueGroups = receivables.overdueGroups,
                            onOpenCobros = onOpenCobros,
                        )
                    }
                }

                // Upcoming gigs section header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.inicio_upcoming_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.inicio_see_agenda),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentHover,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenAgenda)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("inicio_see_agenda_button"),
                        )
                    }
                }

                if (upcoming.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.CalendarMonth,
                            title = stringResource(R.string.inicio_empty_upcoming_title),
                            body = stringResource(R.string.inicio_empty_upcoming_body),
                            actionLabel = stringResource(R.string.nav_new_activity),
                            onAction = onNewActivity,
                        )
                    }
                } else {
                    items(upcoming, key = { it.id }) { activity ->
                        val isFutureToday = activity.eventDate == state.today &&
                            activity.eventTime != null &&
                            activity.eventDate.atTime(activity.eventTime).isAfter(nowLocal)
                        UpcomingActivityCard(
                            activity = activity,
                            supplierDisplay = state.supplierResolution.displayName(activity.supplierName),
                            venueDisplay = state.venueResolution.displayName(activity.venueName),
                            scheduledDeparture = state.scheduledDepartures[activity.id],
                            cachedRoute = state.activityRoutes[activity.id],
                            customEventMinutes = state.activityTimings[activity.id]?.eventMinutes,
                            showContingencyControl = isFutureToday && state.settings.contingencyEnabled,
                            isUnderControl = activity.id in state.contingencyUnderControlIds,
                            onToggleUnderControl = { underControl ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetContingencyUnderControl(activity.id, underControl)
                            },
                            onClick = { onOpenActivity(activity) },
                            onQuickMessageSupplier = { onQuickMessageSupplier(activity) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DarkMonthSummaryCard(
    overview: MonthOverview,
) {
    val colors = ApptividadesThemeDefaults.colors
    val monthLabel = DominicanFormatter.formatMonthYear(overview.month)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.heroCardBg,
        border = if (colors.isDark) BorderStroke(1.dp, colors.heroCardBorder) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inicio_summary_card"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$monthLabel ${stringResource(R.string.inicio_billed_suffix)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDarkMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.activity_count,
                        overview.totalActivities,
                        overview.totalActivities,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDarkMuted,
                )
            }

            Text(
                text = DominicanFormatter.formatMoney(overview.billed, overview.currency),
                style = MaterialTheme.typography.displayLarge,
                color = colors.heroCardText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Progress bar on hero track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.heroCardSurface),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(overview.progressFraction.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.accentBright),
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.inicio_goal_progress,
                        overview.progressPercent,
                        DominicanFormatter.formatMoney(overview.monthlyGoal, overview.currency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDarkMuted,
                )
                Text(
                    text = if (overview.missingGigsEstimate <= 0 && overview.billed >= overview.monthlyGoal) {
                        stringResource(R.string.inicio_goal_reached)
                    } else if (overview.missingGigsEstimate > 0) {
                        pluralStringResource(
                            R.plurals.missing_gigs_estimate,
                            overview.missingGigsEstimate,
                            overview.missingGigsEstimate,
                        )
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onDarkMuted,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = colors.heroCardSurface)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DarkMetricColumn(
                    label = stringResource(R.string.inicio_metric_played),
                    value = overview.playedCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                DarkMetricColumn(
                    label = stringResource(R.string.inicio_metric_upcoming),
                    value = overview.upcomingCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                DarkMetricColumn(
                    label = stringResource(R.string.inicio_metric_collected),
                    value = DominicanFormatter.formatCompactMoney(overview.collectedAmount, overview.currency),
                    modifier = Modifier.weight(1.2f),
                )
            }
        }
    }
}

@Composable
private fun DarkMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = colors.heroCardText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverdueAlertCard(
    overdueTotal: String,
    overdueGroups: List<ReceivableGroup>,
    onOpenCobros: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current

    val breakdownText = overdueGroups.joinToString(" · ") { group ->
        val gigsLabel = context.resources.getQuantityString(R.plurals.gig_count, group.count, group.count)
        val months = group.sortedDates.map { DominicanFormatter.formatShortMonth(it.month) }.distinct()
        val monthsRange = when {
            months.isEmpty() -> ""
            months.size == 1 -> " (${months.first()})"
            else -> " (${months.first()}–${months.last()})"
        }
        "${group.supplierDisplayName} · $gigsLabel$monthsRange"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.dangerBg,
        border = BorderStroke(1.dp, colors.dangerBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inicio_overdue_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.danger,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = colors.onDanger,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.inicio_overdue_title, overdueTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.dangerText,
                    )
                    if (breakdownText.isNotEmpty()) {
                        Text(
                            text = breakdownText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenCobros,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.dangerBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surface,
                    contentColor = colors.dangerText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inicio_overdue_cta_button"),
            ) {
                Text(
                    text = stringResource(R.string.inicio_overdue_cta),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContingencyAlertCard(
    activity: Activity,
    venueDisplay: String,
    assessment: ContingencyAssessment,
    onNotifyDelay: () -> Unit,
    onConfirmUnderControl: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val venueLabel = venueDisplay.ifBlank { activity.venueName }
    val timeLabel = activity.eventTime?.let { DominicanFormatter.formatTime12h(it) }.orEmpty()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.dangerBg,
        border = BorderStroke(1.5.dp, colors.danger),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inicio_contingency_banner_${activity.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.danger,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = colors.onDanger,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.contingency_banner_badge),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.danger,
                    )
                    Text(
                        text = stringResource(
                            R.string.contingency_banner_title,
                            assessment.minutesUntilStart.toInt().coerceAtLeast(1),
                            venueLabel,
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.dangerText,
                    )
                }
            }

            Text(
                text = stringResource(R.string.contingency_banner_body, timeLabel),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onNotifyDelay,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.danger,
                        contentColor = colors.onDanger,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("contingency_notify_delay_${activity.id}"),
                ) {
                    Text(
                        text = stringResource(R.string.contingency_cta_notify_supplier),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onConfirmUnderControl,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.dangerBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surface,
                        contentColor = colors.ink,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("contingency_under_control_${activity.id}"),
                ) {
                    Text(
                        text = stringResource(R.string.contingency_cta_under_control),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingActivityCard(
    activity: Activity,
    supplierDisplay: String,
    venueDisplay: String,
    scheduledDeparture: ScheduledDeparture?,
    cachedRoute: CachedActivityRoute?,
    customEventMinutes: Int?,
    showContingencyControl: Boolean,
    isUnderControl: Boolean,
    onToggleUnderControl: (Boolean) -> Unit,
    onClick: () -> Unit,
    onQuickMessageSupplier: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors

    // Avoid repeating the supplier name twice: prefer description first line when available
    val firstDescLine = activity.description
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() }
        .orEmpty()
    val headline = when {
        firstDescLine.isNotEmpty() -> firstDescLine
        activity.title.isNotBlank() -> activity.title
        venueDisplay.isNotBlank() -> venueDisplay
        else -> stringResource(R.string.app_name)
    }

    val subtitleParts = buildList {
        activity.eventTime?.let { add(DominicanFormatter.formatTime24(it)) }
        customEventMinutes?.let { add("$it min") }
        if (venueDisplay.isNotBlank() && !venueDisplay.equals(headline, ignoreCase = true)) {
            add(venueDisplay)
        }
        if (supplierDisplay.isNotBlank() && !supplierDisplay.equals(headline, ignoreCase = true)) {
            add(supplierDisplay)
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("upcoming_activity_${activity.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 52dp date tile
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.neutralChipBg,
                    modifier = Modifier.size(52.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = DominicanFormatter.formatWeekdayUpper(activity.eventDate.dayOfWeek),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.muted,
                        )
                        Text(
                            text = activity.eventDate.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 18.sp,
                            ),
                            color = colors.ink,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Text(
                    text = DominicanFormatter.formatMoney(activity.amount, activity.currency),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.ink,
                    maxLines = 1,
                )
            }

            if (scheduledDeparture != null) {
                val prepareTime = Instant.ofEpochMilli(scheduledDeparture.prepareAtEpochMillis)
                    .atZone(SANTO_DOMINGO_ZONE)
                    .toLocalTime()
                val leaveTime = Instant.ofEpochMilli(scheduledDeparture.leaveAtEpochMillis)
                    .atZone(SANTO_DOMINGO_ZONE)
                    .toLocalTime()

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.accentSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upcoming_departure_${activity.id}"),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = stringResource(
                                    R.string.departure_leave_and_prepare,
                                    DominicanFormatter.formatTime12h(prepareTime),
                                    DominicanFormatter.formatTime12h(leaveTime),
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentHover,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val routeEstLabel = when {
                            scheduledDeparture.distanceKm != null -> stringResource(
                                R.string.departure_osrm_distance_time,
                                DominicanFormatter.formatOneDecimal(scheduledDeparture.distanceKm),
                                scheduledDeparture.travelMinutes,
                            )
                            scheduledDeparture.travelMinutes > 0 -> stringResource(
                                R.string.departure_osrm_time_only,
                                scheduledDeparture.travelMinutes,
                            )
                            else -> stringResource(R.string.departure_no_coords_note)
                        }
                        Text(
                            text = routeEstLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else if (cachedRoute != null) {
                val routeEstLabel = if (cachedRoute.distanceKm != null) {
                    stringResource(
                        R.string.departure_osrm_distance_time,
                        DominicanFormatter.formatOneDecimal(cachedRoute.distanceKm),
                        cachedRoute.travelMinutes,
                    )
                } else {
                    stringResource(
                        R.string.departure_osrm_time_only,
                        cachedRoute.travelMinutes,
                    )
                }
                Text(
                    text = routeEstLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            NavigationActionButtons(
                venueName = listOfNotNull(
                    venueDisplay.ifBlank { activity.venueName }.takeIf { it.isNotBlank() },
                    activity.venueAddress?.takeIf { it.isNotBlank() },
                ).joinToString(", ").ifBlank { null },
                latitude = activity.latitude,
                longitude = activity.longitude,
                idTag = "inicio_${activity.id}",
            )

            if (showContingencyControl) {
                if (isUnderControl) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.successBg,
                        border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inicio_under_control_badge_${activity.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.contingency_status_under_control),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.successText,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(
                                onClick = { onToggleUnderControl(false) },
                                modifier = Modifier.testTag("inicio_reactivate_contingency_${activity.id}"),
                            ) {
                                Text(
                                    text = stringResource(R.string.contingency_cta_reactivate),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.muted,
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { onToggleUnderControl(true) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.line),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.ground,
                            contentColor = colors.ink,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("inicio_mark_under_control_${activity.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.contingency_cta_mark_under_control),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onQuickMessageSupplier,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.successBg,
                    contentColor = colors.successText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inicio_quick_message_${activity.id}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = colors.successText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.supplier_msg_action_cta),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
