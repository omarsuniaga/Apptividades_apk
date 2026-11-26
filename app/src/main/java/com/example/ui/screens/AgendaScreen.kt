package com.example.ui.screens

import com.example.ui.components.routeSummaryText
import com.example.ui.components.RouteWarnings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Activity
import com.example.domain.model.ActivityFinancialRecord
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.ActivityFinanceCalculator
import com.example.domain.rules.CalendarLabelFormatter
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.EffectivePaymentState
import com.example.domain.rules.NameResolution
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingStateBlock
import com.example.ui.components.OfflineSyncBanner
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.AppDataState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendaScreen(
    state: AppDataState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNewActivityOnDate: (LocalDate) -> Unit,
    onOpenActivity: (Activity) -> Unit,
    onTogglePaid: (Activity) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickMessageSupplier: (Activity) -> Unit = {},
    onExportToCalendar: (Activity) -> Unit = {},
) {
    val colors = ApptividadesThemeDefaults.colors
    val haptic = LocalHapticFeedback.current
    val selectedDayActivities = state.selectedDayActivities
    val freeWeekends = state.freeWeekendsInDisplayedMonth

    Box(
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
            // Month title + prev/next controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = DominicanFormatter.formatMonthYear(state.displayedMonth),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UtilityIconTile(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.agenda_prev_month),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPrevMonth()
                            },
                            testTag = "agenda_prev_month",
                            borderColor = colors.line,
                            backgroundColor = colors.surface,
                            tint = colors.ink,
                            modifier = Modifier.size(48.dp),
                        )
                        UtilityIconTile(
                            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.agenda_next_month),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNextMonth()
                            },
                            testTag = "agenda_next_month",
                            borderColor = colors.line,
                            backgroundColor = colors.surface,
                            tint = colors.ink,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            if (!state.isOnline || state.isOfflineCacheActive || state.pendingSyncCount > 0) {
                item(key = "agenda_offline_sync_banner") {
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
                item { ErrorBanner(message = state.errorMessage, onRetry = onRefresh) }
            }

            if (state.isLoading && state.activities.isEmpty()) {
                item { LoadingStateBlock() }
            } else {
                // Monday-first month calendar grid card with smooth month transition
                item {
                    AnimatedContent(
                        targetState = state.displayedMonth,
                        transitionSpec = {
                            val forward = targetState.isAfter(initialState)
                            val enterOffset: (Int) -> Int = { width -> if (forward) width / 5 else -width / 5 }
                            val exitOffset: (Int) -> Int = { width -> if (forward) -width / 5 else width / 5 }
                            (slideInHorizontally(animationSpec = tween(220), initialOffsetX = enterOffset) +
                                fadeIn(animationSpec = tween(220))) togetherWith
                                (slideOutHorizontally(animationSpec = tween(180), targetOffsetX = exitOffset) +
                                    fadeOut(animationSpec = tween(180)))
                        },
                        label = "agenda_month_transition",
                    ) { month ->
                        MonthCalendarCard(
                            displayedMonth = month,
                            today = state.today,
                            selectedDate = state.selectedAgendaDate,
                            activitiesByDate = state.activitiesByDate,
                            supplierResolution = state.supplierResolution,
                            onSelectDate = onSelectDate,
                        )
                    }
                }

                // Free weekend chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.agenda_free_weekends),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.muted,
                        )
                        if (freeWeekends.isEmpty()) {
                            Text(
                                text = stringResource(R.string.agenda_no_free_weekends),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                freeWeekends.forEach { weekendDate ->
                                    Surface(
                                        onClick = { onNewActivityOnDate(weekendDate) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = colors.successBg,
                                        modifier = Modifier.testTag("free_weekend_${weekendDate}"),
                                    ) {
                                        Text(
                                            text = DominicanFormatter.formatChipDate(weekendDate),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.successText,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected day heading
                item {
                    val dayHeading = if (state.selectedAgendaDate == state.today) {
                        stringResource(
                            R.string.agenda_today_prefix,
                            DominicanFormatter.formatChipDate(state.selectedAgendaDate),
                        )
                    } else {
                        DominicanFormatter.formatFormDate(state.selectedAgendaDate)
                    }
                    Text(
                        text = dayHeading,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (selectedDayActivities.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Outlined.EventAvailable,
                            title = stringResource(R.string.agenda_empty_day),
                            body = DominicanFormatter.formatFullDateHeader(state.selectedAgendaDate),
                            actionLabel = stringResource(R.string.agenda_add_on_day),
                            onAction = { onNewActivityOnDate(state.selectedAgendaDate) },
                        )
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            selectedDayActivities.forEachIndexed { idx, activity ->
                                AgendaDayActivityCard(
                                    activity = activity,
                                    today = state.today,
                                    supplierDisplay = state.supplierResolution.displayName(activity.supplierName),
                                    venueDisplay = state.venueResolution.displayName(activity.venueName),
                                    scheduledDeparture = state.scheduledDepartures[activity.id],
                                    cachedRoute = state.activityRoutes[activity.id],
                                    financialRecord = state.activityFinancialRecords[activity.id],
                                    onClick = { onOpenActivity(activity) },
                                    onTogglePaid = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTogglePaid(activity)
                                    },
                                    onQuickMessageSupplier = { onQuickMessageSupplier(activity) },
                                    onExportToCalendar = { onExportToCalendar(activity) },
                                )

                                // Show gap between consecutive gigs with times
                                if (idx < selectedDayActivities.lastIndex) {
                                    val firstTime = activity.eventTime
                                    val nextTime = selectedDayActivities[idx + 1].eventTime
                                    if (firstTime != null && nextTime != null) {
                                        val gap = DominicanFormatter.formatGapBetween(firstTime, nextTime)
                                        if (gap != null) {
                                            Text(
                                                text = stringResource(R.string.agenda_gap_between, gap),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.muted,
                                                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CALENDAR_WEEKDAYS = listOf("L", "M", "M", "J", "V", "S", "D")

private val CALENDAR_CELL_HEIGHT = 72.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthCalendarCard(
    displayedMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    activitiesByDate: Map<LocalDate, List<Activity>>,
    supplierResolution: NameResolution,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors

    val firstOfMonth = displayedMonth.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1 // Monday = 1 -> 0 blanks
    val daysInMonth = displayedMonth.lengthOfMonth()
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7
    val weeks = totalCells / 7

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agenda_calendar_card"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CALENDAR_WEEKDAYS.forEach { dayLetter ->
                    Text(
                        text = dayLetter,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            for (week in 0 until weeks) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = week * 7 + col
                        val dayNum = cellIndex - leadingBlanks + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = displayedMonth.atDay(dayNum)
                            val dayActivities = activitiesByDate[date]
                            CalendarDayCell(
                                date = date,
                                dayNum = dayNum,
                                today = today,
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                dayActivities = dayActivities,
                                supplierResolution = supplierResolution,
                                onSelectDate = onSelectDate,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(CALENDAR_CELL_HEIGHT),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line, modifier = Modifier.padding(top = 6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp, start = 6.dp, end = 6.dp),
            ) {
                LegendDotItem(
                    color = colors.accent,
                    label = stringResource(R.string.agenda_legend_played_pending),
                )
                LegendDotItem(
                    color = colors.info,
                    label = stringResource(R.string.agenda_legend_scheduled),
                )
                LegendDotItem(
                    color = colors.success,
                    label = stringResource(R.string.agenda_legend_paid),
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    dayNum: Int,
    today: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    dayActivities: List<Activity>?,
    supplierResolution: NameResolution,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val bgColor = when {
        isSelected -> colors.ink
        isToday -> colors.accentSoft
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> colors.surface
        isToday -> colors.accentHover
        else -> colors.ink
    }
    val supplierLabelColor = when {
        isSelected -> colors.surface.copy(alpha = 0.88f)
        isToday -> colors.accentHover.copy(alpha = 0.9f)
        else -> colors.muted
    }

    Box(
        modifier = modifier
            .height(CALENDAR_CELL_HEIGHT)
            .padding(horizontal = 1.5.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onSelectDate(date) }
            .testTag("calendar_day_${date}"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = dayNum.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                ),
                color = textColor,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                modifier = Modifier.height(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!dayActivities.isNullOrEmpty()) {
                    dayActivities.take(3).forEach { gig ->
                        val dotColor = if (isSelected) {
                            colors.surface
                        } else {
                            when {
                                gig.paymentStatus == PaymentStatus.PAGADO -> colors.success
                                gig.eventDate.isAfter(today) -> colors.info
                                else -> colors.accent
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }
            }

            if (!dayActivities.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                dayActivities.take(2).forEach { gig ->
                    val shortName = CalendarLabelFormatter.shortSupplierLabel(
                        resolvedSupplierDisplay = supplierResolution.displayName(gig.supplierName),
                        rawSupplierName = gig.supplierName,
                        fallbackLabel = gig.venueName.ifBlank { gig.title },
                    )
                    if (shortName.isNotEmpty()) {
                        Text(
                            text = shortName,
                            fontSize = 8.5.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = supplierLabelColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDotItem(color: Color, label: String) {
    val colors = ApptividadesThemeDefaults.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
    }
}

@Composable
private fun AgendaDayActivityCard(
    activity: Activity,
    today: LocalDate,
    supplierDisplay: String,
    venueDisplay: String,
    scheduledDeparture: ScheduledDeparture?,
    cachedRoute: CachedActivityRoute?,
    financialRecord: ActivityFinancialRecord?,
    onClick: () -> Unit,
    onTogglePaid: () -> Unit,
    onQuickMessageSupplier: () -> Unit,
    onExportToCalendar: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val breakdown = ActivityFinanceCalculator.breakdown(activity, financialRecord)
    val isFuture = activity.eventDate.isAfter(today)

    val (pillLabel, pillBg, pillText) = when {
        breakdown.state == EffectivePaymentState.PAID ->
            Triple(stringResource(R.string.status_pagado), colors.successBg, colors.successText)
        breakdown.state == EffectivePaymentState.PARTIAL ->
            Triple(
                stringResource(
                    R.string.agenda_status_partial,
                    DominicanFormatter.formatCompactMoney(breakdown.pendingBalance, activity.currency),
                ),
                colors.accentSoft,
                colors.accentHover,
            )
        isFuture -> Triple(stringResource(R.string.status_programada), colors.infoBg, colors.info)
        else -> Triple(stringResource(R.string.status_por_cobrar), colors.accentSoft, colors.accentHover)
    }

    val subline = buildList {
        if (supplierDisplay.isNotBlank()) add(supplierDisplay)
        add(DominicanFormatter.formatMoney(activity.amount, activity.currency))
    }.joinToString(" · ")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agenda_activity_${activity.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = DominicanFormatter.formatTime24(activity.eventTime),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                    ),
                    color = colors.ink,
                    modifier = Modifier.width(52.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = venueDisplay.ifBlank { activity.title },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(
                    label = pillLabel,
                    backgroundColor = pillBg,
                    textColor = pillText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onTogglePaid)
                        .testTag("toggle_paid_${activity.id}"),
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (scheduledDeparture.isChained) {
                                    stringResource(
                                        R.string.route_leave_from_venue,
                                        scheduledDeparture.originVenueName.orEmpty(),
                                        DominicanFormatter.formatTime12h(leaveTime),
                                    )
                                } else {
                                    stringResource(
                                        R.string.departure_leave_and_prepare,
                                        DominicanFormatter.formatTime12h(prepareTime),
                                        DominicanFormatter.formatTime12h(leaveTime),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentHover,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val routeEstLabel = if (
                            scheduledDeparture.distanceKm != null || scheduledDeparture.travelMinutes > 0
                        ) {
                            routeSummaryText(
                                distanceKm = scheduledDeparture.distanceKm,
                                travelMinutes = scheduledDeparture.travelMinutes,
                                originKind = scheduledDeparture.originKind,
                                originVenueName = scheduledDeparture.originVenueName,
                            )
                        } else {
                            stringResource(R.string.departure_no_coords_note)
                        }
                        Text(
                            text = routeEstLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RouteWarnings(
                            shortfallMinutes = scheduledDeparture.shortfallMinutes,
                            unpinnedPreviousVenueName = scheduledDeparture.unpinnedPreviousVenueName,
                        )
                    }
                }
            } else if (cachedRoute != null) {
                Text(
                    text = routeSummaryText(
                        distanceKm = cachedRoute.distanceKm,
                        travelMinutes = cachedRoute.travelMinutes,
                        originKind = cachedRoute.originKind,
                        originVenueName = cachedRoute.originVenueName,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onQuickMessageSupplier,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.success.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.successBg,
                        contentColor = colors.successText,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("agenda_quick_message_${activity.id}"),
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

                UtilityIconTile(
                    icon = Icons.Outlined.Event,
                    contentDescription = stringResource(R.string.calendar_export_cd),
                    onClick = onExportToCalendar,
                    testTag = "agenda_export_calendar_${activity.id}",
                    borderColor = colors.line,
                    backgroundColor = colors.neutralChipBg,
                    tint = colors.ink,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
