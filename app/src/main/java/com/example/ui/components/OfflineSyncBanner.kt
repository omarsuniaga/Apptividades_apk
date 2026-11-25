package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.ui.theme.ApptividadesThemeDefaults
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SYNC_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("es", "DO"))

/**
 * Editorial offline & pending-sync banner shown when the device is offline,
 * displaying cached Room data, or has queued local mutations waiting to sync.
 */
@Composable
fun OfflineSyncBanner(
    isOnline: Boolean,
    isOfflineCacheActive: Boolean,
    lastSyncedAtMillis: Long?,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOnline && !isOfflineCacheActive && pendingSyncCount == 0) return

    val colors = ApptividadesThemeDefaults.colors
    val isOfflineState = !isOnline || isOfflineCacheActive

    val formattedSyncTime = remember(lastSyncedAtMillis) {
        lastSyncedAtMillis?.let { millis ->
            runCatching {
                Instant.ofEpochMilli(millis)
                    .atZone(SANTO_DOMINGO_ZONE)
                    .format(SYNC_TIME_FORMATTER)
            }.getOrNull()
        }
    }

    val title = if (isOfflineState) {
        stringResource(R.string.offline_banner_title)
    } else {
        stringResource(R.string.offline_banner_pending_title)
    }

    val metaParts = buildList {
        if (formattedSyncTime != null) {
            add(stringResource(R.string.offline_banner_synced_at, formattedSyncTime))
        }
        if (pendingSyncCount > 0) {
            add(stringResource(R.string.offline_banner_pending_count, pendingSyncCount))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.accentSoft,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_sync_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.25f)),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOfflineState) Icons.Outlined.CloudOff else Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = colors.accentHover,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentHover,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            OutlinedButton(
                onClick = onSyncNow,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surface,
                    contentColor = colors.ink,
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("offline_sync_now_button"),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        color = colors.accentHover,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.offline_banner_syncing),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.offline_banner_sync_now),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
