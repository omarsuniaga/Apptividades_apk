package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LoginHistoryStore
import com.example.domain.model.LoginSessionEvent
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.rules.DominicanFormatter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import java.time.Instant

@Composable
fun LoginHistoryScreen(
    events: List<LoginSessionEvent>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                testTag = "login_history_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = stringResource(R.string.login_history_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (events.isNotEmpty()) {
                UtilityIconTile(
                    icon = Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(R.string.login_history_clear),
                    onClick = { showClearConfirm = true },
                    testTag = "login_history_clear_button",
                    borderColor = colors.line,
                    backgroundColor = colors.surface,
                    tint = colors.dangerText,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (events.isEmpty()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    EmptyStateCard(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.login_history_title),
                        body = stringResource(R.string.login_history_empty),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(events, key = { it.id }) { event ->
                        LoginEventCard(event = event)
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = stringResource(R.string.login_history_clear_confirm_title),
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.login_history_clear_confirm_body),
                    color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearHistory()
                    },
                    modifier = Modifier.testTag("login_history_confirm_clear"),
                ) {
                    Text(
                        text = stringResource(R.string.login_history_clear),
                        color = colors.dangerText,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            },
        )
    }
}

@Composable
private fun LoginEventCard(event: LoginSessionEvent) {
    val colors = ApptividadesThemeDefaults.colors
    val zoned = remember(event.timestampEpochMillis) {
        Instant.ofEpochMilli(event.timestampEpochMillis).atZone(SANTO_DOMINGO_ZONE)
    }
    val dateLabel = DominicanFormatter.formatFormDate(zoned.toLocalDate())
    val timeLabel = DominicanFormatter.formatTime12h(zoned.toLocalTime())

    val typeLabel = when (event.eventType) {
        LoginHistoryStore.EVENT_SIGNUP -> stringResource(R.string.login_history_event_signup)
        LoginHistoryStore.EVENT_SESSION_RESTORED -> stringResource(R.string.login_history_event_restored)
        else -> stringResource(R.string.login_history_event_login)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, if (event.success) colors.line else colors.dangerBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_history_card_${event.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                StatusPill(
                    label = stringResource(
                        if (event.success) R.string.login_history_status_success
                        else R.string.login_history_status_failed
                    ),
                    backgroundColor = if (event.success) colors.successBg else colors.dangerBg,
                    textColor = if (event.success) colors.successText else colors.dangerText,
                )
            }

            if (event.email.isNotBlank()) {
                Text(
                    text = event.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
            }

            Text(
                text = "$dateLabel · $timeLabel",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily),
                color = colors.muted,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "IP: ${event.ipAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily),
                        color = colors.ink,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${event.deviceName} (${event.androidVersion})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                }
            }
        }
    }
}
