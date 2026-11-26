package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.LabSettings
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.TrackedRoute
import com.example.domain.rules.TrackedRoutes
import com.example.domain.rules.effectiveDriveMinutes
import com.example.domain.rules.latestLeaveAt
import com.example.domain.rules.timingOverride
import com.example.ui.theme.ApptividadesThemeDefaults
import java.time.LocalDate
import java.time.LocalTime

/**
 * The artist's own times for the venue: shows the latest and recommended departures and
 * lets them save or remove the tracked route.
 */
@Composable
fun MyRouteSection(
    venueName: String,
    route: CachedActivityRoute,
    tracked: TrackedRoute?,
    eventDate: LocalDate,
    eventTime: LocalTime?,
    settings: LabSettings,
    dayOverride: ActivityTimelineOverride?,
    onSave: (venueKey: String, route: TrackedRoute?) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    var isEditing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (tracked != null && eventTime != null) {
            val timeline = EventTimelineCalculator.calculate(
                eventDate = eventDate,
                eventTime = eventTime,
                settings = settings,
                routeTravelMinutes = route.effectiveDriveMinutes,
                override = route.timingOverride(dayOverride),
            )
            val config = timeline.config
            Text(
                text = stringResource(
                    R.string.my_route_minimum,
                    (config.travelMinutes ?: 0) + config.internalTransferInMinutes + config.setupMinutes,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.ink,
            )
            Text(
                text = stringResource(
                    R.string.my_route_departures,
                    DominicanFormatter.formatTime12h(timeline.leaveAt.toLocalTime()),
                    DominicanFormatter.formatTime12h(timeline.latestLeaveAt.toLocalTime()),
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.accentHover,
                modifier = Modifier.testTag("my_route_departures"),
            )
        }
        OutlinedButton(
            onClick = { isEditing = true },
            modifier = Modifier.testTag("my_route_edit_button"),
        ) {
            Text(
                text = stringResource(
                    if (tracked == null) R.string.my_route_add else R.string.my_route_edit,
                ),
            )
        }
    }

    if (isEditing) {
        val originKey = TrackedRoutes.originKey(route.originKind, route.originVenueName)
        var drive by remember { mutableStateOf(route.effectiveDriveMinutes.toString()) }
        var walk by remember {
            mutableStateOf((tracked?.walkMinutes ?: settings.internalTransferInMinutes).toString())
        }
        var setup by remember {
            mutableStateOf((tracked?.setupMinutes ?: settings.setupMinutes).toString())
        }
        var margin by remember {
            mutableStateOf((tracked?.marginMinutes ?: settings.travelBufferMinutes).toString())
        }
        val values = listOf(drive, walk, setup, margin).map { it.toIntOrNull() }

        AlertDialog(
            onDismissRequest = { isEditing = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.my_route_title, venueName)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    val originName = route.originVenueName
                    MinutesField(
                        label = if (originKey == TrackedRoutes.HOME_ORIGIN) {
                            stringResource(R.string.my_route_drive_home)
                        } else {
                            stringResource(R.string.my_route_drive_from, originName.orEmpty())
                        },
                        value = drive,
                        tag = "my_route_drive",
                        onChange = { drive = it },
                    )
                    MinutesField(stringResource(R.string.my_route_walk), walk, "my_route_walk") { walk = it }
                    MinutesField(stringResource(R.string.my_route_setup), setup, "my_route_setup") { setup = it }
                    MinutesField(stringResource(R.string.my_route_margin), margin, "my_route_margin") { margin = it }
                    Text(
                        text = stringResource(R.string.my_route_scope_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                    )
                    if (tracked != null) {
                        TextButton(
                            onClick = {
                                isEditing = false
                                onSave(tracked.venueKey, null)
                            },
                            modifier = Modifier.testTag("my_route_delete"),
                        ) {
                            Text(stringResource(R.string.my_route_delete), color = colors.dangerText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = values.all { it != null },
                    onClick = {
                        isEditing = false
                        val updated = TrackedRoutes.update(
                            existing = tracked,
                            venueName = venueName,
                            originKey = originKey,
                            driveMinutes = values[0] ?: 0,
                            walkMinutes = values[1] ?: 0,
                            setupMinutes = values[2] ?: 0,
                            marginMinutes = values[3] ?: 0,
                        )
                        onSave(updated.venueKey, updated)
                    },
                    modifier = Modifier.testTag("my_route_save"),
                ) {
                    Text(stringResource(R.string.my_route_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) {
                    Text(stringResource(R.string.my_route_cancel))
                }
            },
        )
    }
}

@Composable
private fun MinutesField(
    label: String,
    value: String,
    tag: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onChange(next.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}
