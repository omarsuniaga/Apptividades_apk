package com.example.ui.components

import com.example.domain.model.SANTO_DOMINGO_ZONE
import java.time.Instant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.RouteOriginKind
import com.example.ui.theme.ApptividadesThemeDefaults

/** "19.6 km · 20 min · desde Hotel A". The origin is omitted when it is unknown. */
@Composable
fun routeSummaryText(
    distanceKm: Double?,
    travelMinutes: Int,
    originKind: RouteOriginKind?,
    originVenueName: String?,
    isTrackedDrive: Boolean = false,
): String {
    val measure = if (distanceKm != null) {
        stringResource(
            if (isTrackedDrive) R.string.route_distance_tracked else R.string.route_distance_time,
            DominicanFormatter.formatOneDecimal(distanceKm),
            travelMinutes,
        )
    } else {
        stringResource(
            if (isTrackedDrive) R.string.route_time_tracked else R.string.route_time_only,
            travelMinutes,
        )
    }
    val origin = when (originKind) {
        RouteOriginKind.PREVIOUS_EVENT -> stringResource(R.string.route_from_venue, originVenueName.orEmpty())
        RouteOriginKind.HOME -> stringResource(R.string.route_from_home)
        RouteOriginKind.CURRENT_LOCATION -> stringResource(R.string.route_from_current)
        RouteOriginKind.DEFAULT_AREA -> stringResource(R.string.route_from_default)
        null -> null
    }
    return if (origin == null) measure else "$measure · $origin"
}

/** Problems the artist must see about a chained trip. */
@Composable
fun RouteWarnings(
    shortfallMinutes: Int,
    unpinnedPreviousVenueName: String?,
) {
    val colors = ApptividadesThemeDefaults.colors
    if (shortfallMinutes > 0) {
        Text(
            text = stringResource(R.string.route_shortfall_warning, shortfallMinutes),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.dangerText,
        )
    }
    if (!unpinnedPreviousVenueName.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.route_previous_unpinned, unpinnedPreviousVenueName),
            style = MaterialTheme.typography.labelSmall,
            color = colors.dangerText,
        )
    }
}

/** Appends the latest possible departure when the artist has a tracked route. */
@Composable
fun withLatestLeave(headline: String, latestLeaveAtEpochMillis: Long?): String {
    if (latestLeaveAtEpochMillis == null) return headline
    val latest = Instant.ofEpochMilli(latestLeaveAtEpochMillis)
        .atZone(SANTO_DOMINGO_ZONE)
        .toLocalTime()
    return stringResource(R.string.route_latest_leave, headline, DominicanFormatter.formatTime12h(latest))
}
