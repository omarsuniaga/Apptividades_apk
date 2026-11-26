package com.example.ui.screens

import com.example.ui.components.withLatestLeave
import com.example.ui.components.RouteWarnings
import com.example.ui.components.routeSummaryText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.ContingencyEvaluator
import com.example.domain.rules.ContingencyState
import com.example.domain.rules.DominicanFormatter
import com.example.ui.components.NavigationActionButtons
import com.example.ui.components.StatusPill
import com.example.ui.theme.ApptividadesThemeDefaults
import java.time.Instant
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsAndDeparturesSheet(
    scheduledDepartures: List<ScheduledDeparture>,
    isRecalculatingRoutes: Boolean,
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onRecalculateRoutes: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onTestNotification: () -> Unit = {},
    isIgnoringBatteryOptimizations: Boolean = true,
    onOpenBatterySettings: () -> Unit = {},
    contingencyEnabled: Boolean = true,
    contingencyLeadMinutes: Int = ContingencyEvaluator.DEFAULT_LEAD_MINUTES,
    contingencyUnderControlIds: Set<String> = emptySet(),
    onSetContingencyUnderControl: (String, Boolean) -> Unit = { _, _ -> },
    onOpenContingencyMessage: (String) -> Unit = {},
) {
    val colors = ApptividadesThemeDefaults.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nowLocal = ZonedDateTime.now(SANTO_DOMINGO_ZONE).toLocalDateTime()
    val today = nowLocal.toLocalDate()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.ground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.alerts_sheet_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                )
                TextButton(
                    onClick = onRecalculateRoutes,
                    enabled = !isRecalculatingRoutes,
                    modifier = Modifier.testTag("alerts_refresh_routes_button"),
                ) {
                    if (isRecalculatingRoutes) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.alerts_sheet_refresh_routes),
                        color = colors.accentHover,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // Daily summary indicator
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.alerts_sheet_daily_active),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onTestNotification,
                        modifier = Modifier.testTag("alerts_test_notification_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.alerts_test_notification_cta),
                            color = colors.accentHover,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }

            // Missing permission prompt banner
            if (!hasNotificationPermission || !hasLocationPermission || !canScheduleExactAlarms) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentSoft,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.perm_rationale_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.ink,
                            )
                            val missing = buildList {
                                if (!hasNotificationPermission) add("notificaciones")
                                if (!hasLocationPermission) add("ubicación")
                                if (!canScheduleExactAlarms) add("alarmas exactas")
                            }.joinToString(", ")
                            Text(
                                text = "Pendiente: $missing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.nav_settings),
                                color = colors.ink,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            // OEM Battery Optimization warning banner (Xiaomi / Samsung / Huawei)
            if (!isIgnoringBatteryOptimizations) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentSoft,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alerts_battery_warning_banner"),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryAlert,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.alerts_battery_warning_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.ink,
                            )
                            Text(
                                text = stringResource(R.string.alerts_battery_warning_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink,
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenBatterySettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("alerts_battery_open_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_battery_open),
                                color = colors.ink,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }

            if (scheduledDepartures.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.alerts_sheet_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((scheduledDepartures.size * 158).coerceAtMost(380).dp),
                ) {
                    items(scheduledDepartures, key = { it.activityId }) { dep ->
                        val prepareTime = Instant.ofEpochMilli(dep.prepareAtEpochMillis)
                            .atZone(SANTO_DOMINGO_ZONE)
                            .toLocalTime()
                        val leaveTime = Instant.ofEpochMilli(dep.leaveAtEpochMillis)
                            .atZone(SANTO_DOMINGO_ZONE)
                            .toLocalTime()

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("departure_card_${dep.activityId}"),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = dep.venueName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${DominicanFormatter.formatChipDate(dep.eventDate)} · ${DominicanFormatter.formatTime24(dep.eventTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                }

                                val estimateLabel = if (dep.distanceKm != null || dep.travelMinutes > 0) {
                                    routeSummaryText(
                                        distanceKm = dep.distanceKm,
                                        travelMinutes = dep.travelMinutes,
                                        originKind = dep.originKind,
                                        originVenueName = dep.originVenueName,
                                        isTrackedDrive = dep.isTrackedDrive,
                                    )
                                } else {
                                    stringResource(R.string.departure_no_coords_note)
                                }
                                StatusPill(
                                    label = estimateLabel,
                                    backgroundColor = colors.neutralChipBg,
                                    textColor = colors.neutralChipText,
                                )
                                RouteWarnings(
                                    shortfallMinutes = dep.shortfallMinutes,
                                    unpinnedPreviousVenueName = dep.unpinnedPreviousVenueName,
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Alarm,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = withLatestLeave(
                                            if (dep.isChained) {
                                                stringResource(
                                                    R.string.route_leave_from_venue,
                                                    dep.originVenueName.orEmpty(),
                                                    DominicanFormatter.formatTime12h(leaveTime),
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.departure_leave_and_prepare,
                                                    DominicanFormatter.formatTime12h(prepareTime),
                                                    DominicanFormatter.formatTime12h(leaveTime),
                                                )
                                            },
                                            dep.latestLeaveAtEpochMillis,
                                        ),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.ink,
                                    )
                                }

                                NavigationActionButtons(
                                    venueName = dep.venueName,
                                    originLatitude = dep.originLatitude.takeIf { dep.isChained },
                                    originLongitude = dep.originLongitude.takeIf { dep.isChained },
                                    latitude = dep.latitude,
                                    longitude = dep.longitude,
                                    idTag = "alert_${dep.activityId}",
                                )

                                // Today's Modo Contingencia controls
                                if (contingencyEnabled && dep.eventDate == today) {
                                    val isUnderControl = dep.activityId in contingencyUnderControlIds
                                    val assessment = ContingencyEvaluator.evaluate(
                                        now = nowLocal,
                                        eventStart = dep.eventDate.atTime(dep.eventTime),
                                        contingencyEnabled = true,
                                        leadMinutes = contingencyLeadMinutes,
                                        isMarkedUnderControl = isUnderControl,
                                        venueLat = dep.latitude,
                                        venueLng = dep.longitude,
                                        currentLat = null,
                                        currentLng = null,
                                    )
                                    if (isUnderControl) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = colors.success,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = stringResource(R.string.contingency_status_under_control),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.successText,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            TextButton(
                                                onClick = { onSetContingencyUnderControl(dep.activityId, false) },
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.contingency_cta_reactivate),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.muted,
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (assessment.state == ContingencyState.ACTIVE_ALERT) {
                                                Button(
                                                    onClick = {
                                                        onDismiss()
                                                        onOpenContingencyMessage(dep.activityId)
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = colors.danger,
                                                        contentColor = colors.surface,
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.contingency_cta_notify_supplier),
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { onSetContingencyUnderControl(dep.activityId, true) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.contingency_cta_under_control),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = colors.ink,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.nav_real_traffic_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.muted,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
