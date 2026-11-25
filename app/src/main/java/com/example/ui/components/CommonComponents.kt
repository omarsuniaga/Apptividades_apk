package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alarms.NotificationHelper
import com.example.ui.theme.ApptividadesThemeDefaults

enum class MainTab(
    val route: String,
    val labelRes: Int,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val testTag: String,
) {
    INICIO("inicio", R.string.nav_inicio, Icons.Rounded.Home, Icons.Outlined.Home, "tab_inicio"),
    AGENDA("agenda", R.string.nav_agenda, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_agenda"),
    COBROS("cobros", R.string.nav_cobros, Icons.Rounded.CreditCard, Icons.Outlined.CreditCard, "tab_cobros"),
    BALANCE("balance", R.string.nav_balance, Icons.Rounded.BarChart, Icons.Outlined.BarChart, "tab_balance"),
}

/**
 * Approved bottom bar: 4 tabs (Inicio, Agenda, Cobros, Balance) + central raised + button
 * (56dp square, 18dp radius, filled with Accent and soft amber shadow, raised 24dp above the bar).
 */
@Composable
fun ApptividadesBottomBar(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onNewActivityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = colors.line)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomTabItem(
                    tab = MainTab.INICIO,
                    selected = currentTab == MainTab.INICIO,
                    onClick = { onSelectTab(MainTab.INICIO) },
                    modifier = Modifier.weight(1f),
                )
                BottomTabItem(
                    tab = MainTab.AGENDA,
                    selected = currentTab == MainTab.AGENDA,
                    onClick = { onSelectTab(MainTab.AGENDA) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(68.dp))
                BottomTabItem(
                    tab = MainTab.COBROS,
                    selected = currentTab == MainTab.COBROS,
                    onClick = { onSelectTab(MainTab.COBROS) },
                    modifier = Modifier.weight(1f),
                )
                BottomTabItem(
                    tab = MainTab.BALANCE,
                    selected = currentTab == MainTab.BALANCE,
                    onClick = { onSelectTab(MainTab.BALANCE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Central raised + button
        Surface(
            onClick = onNewActivityClick,
            shape = RoundedCornerShape(18.dp),
            color = colors.accent,
            contentColor = colors.surface,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(56.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = colors.accent,
                    spotColor = colors.accent,
                )
                .testTag("fab_new_activity"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.nav_new_activity),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val tint = if (selected) colors.accent else colors.muted

    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(tab.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
            contentDescription = stringResource(tab.labelRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Compact icon-button cluster for secondary destinations: Reportes, Proveedores, Ajustes.
 */
@Composable
fun TopUtilityActions(
    onOpenReports: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UtilityIconTile(
            icon = Icons.Outlined.Description,
            contentDescription = stringResource(R.string.nav_reports),
            onClick = onOpenReports,
            testTag = "top_action_reports",
            borderColor = colors.line,
            backgroundColor = colors.surface,
            tint = colors.ink,
        )
        UtilityIconTile(
            icon = Icons.Outlined.Groups,
            contentDescription = stringResource(R.string.nav_suppliers),
            onClick = onOpenSuppliers,
            testTag = "top_action_suppliers",
            borderColor = colors.line,
            backgroundColor = colors.surface,
            tint = colors.ink,
        )
        UtilityIconTile(
            icon = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.nav_settings),
            onClick = onOpenSettings,
            testTag = "top_action_settings",
            borderColor = colors.line,
            backgroundColor = colors.surface,
            tint = colors.ink,
        )
    }
}

@Composable
fun UtilityIconTile(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    borderColor: Color,
    backgroundColor: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .size(44.dp)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.dangerBg,
        border = BorderStroke(1.dp, colors.dangerBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("error_retry_button"),
                ) {
                    Text(
                        text = stringResource(R.string.action_retry),
                        color = colors.dangerText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.neutralChipBg,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
fun LoadingStateBlock(
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp)
        Text(
            text = stringResource(R.string.loading_data),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
        )
    }
}

@Composable
fun RefreshIconButton(
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    IconButton(
        onClick = onRefresh,
        enabled = !isLoading,
        modifier = Modifier.testTag("refresh_button"),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.action_refresh),
                tint = colors.ink,
            )
        }
    }
}

/**
 * External zero-API-key navigation actions ("Abrir en Google Maps" and "Abrir en Waze")
 * launched via Android Intent.ACTION_VIEW.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavigationActionButtons(
    venueName: String?,
    latitude: Double?,
    longitude: Double?,
    idTag: String,
    modifier: Modifier = Modifier,
) {
    val hasDestination = (latitude != null && longitude != null) || !venueName.isNullOrBlank()
    if (!hasDestination) return

    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = {
                NotificationHelper.openGoogleMaps(
                    context = context,
                    venueName = venueName,
                    latitude = latitude,
                    longitude = longitude,
                )
            },
            shape = RoundedCornerShape(10.dp),
            color = colors.neutralChipBg,
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.testTag("open_google_maps_$idTag"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Directions,
                    contentDescription = null,
                    tint = colors.ink,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.action_open_google_maps),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            onClick = {
                NotificationHelper.openWaze(
                    context = context,
                    venueName = venueName,
                    latitude = latitude,
                    longitude = longitude,
                )
            },
            shape = RoundedCornerShape(10.dp),
            color = colors.infoBg,
            border = BorderStroke(1.dp, colors.info.copy(alpha = 0.28f)),
            modifier = Modifier.testTag("open_waze_$idTag"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NearMe,
                    contentDescription = null,
                    tint = colors.info,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.action_open_waze),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.info,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
