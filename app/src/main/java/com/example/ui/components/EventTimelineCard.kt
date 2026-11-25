package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.LabSettings
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.EventTimeline
import com.example.domain.rules.EventTimelineCalculator
import com.example.domain.rules.TimelineStep
import com.example.domain.rules.TimelineStepKind
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun EventTimelineCard(
    eventDate: LocalDate,
    eventTime: LocalTime?,
    settings: LabSettings,
    routeTravelMinutes: Int?,
    routeDistanceKm: Double?,
    timingOverride: ActivityTimelineOverride?,
    onApplyActivityOverride: (ActivityTimelineOverride?) -> Unit,
    onSaveDefaultSettings: (LabSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    var showAdjustDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        modifier = modifier
            .fillMaxWidth()
            .testTag("event_timeline_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.accentHover,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.timeline_section_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (timingOverride?.hasAnyOverride == true) {
                            Text(
                                text = stringResource(R.string.timeline_custom_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentHover,
                            )
                        }
                    }
                }

                Surface(
                    onClick = { showAdjustDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    color = colors.accentSoft,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("timeline_adjust_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = colors.accentHover,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(R.string.timeline_adjust_cta),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentHover,
                        )
                    }
                }
            }

            if (eventTime == null) {
                Text(
                    text = stringResource(R.string.timeline_no_time_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            } else {
                val timeline = remember(
                    eventDate,
                    eventTime,
                    settings,
                    routeTravelMinutes,
                    routeDistanceKm,
                    timingOverride,
                ) {
                    EventTimelineCalculator.calculate(
                        eventDate = eventDate,
                        eventTime = eventTime,
                        settings = settings,
                        routeTravelMinutes = routeTravelMinutes,
                        routeDistanceKm = routeDistanceKm,
                        override = timingOverride,
                    )
                }

                // Quick 3-metric strip: Prepárate / Sales / Te desocupas
                TimelineQuickStrip(timeline = timeline)

                if (!timeline.hasTravelEstimate) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.neutralChipBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_no_travel_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }

                // Vertical timeline milestones
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    timeline.steps.forEachIndexed { index, step ->
                        TimelineStepRow(
                            step = step,
                            isLast = index == timeline.steps.lastIndex,
                        )
                    }
                }
            }
        }
    }

    if (showAdjustDialog && eventTime != null) {
        TimelineAdjustDialog(
            eventDate = eventDate,
            eventTime = eventTime,
            settings = settings,
            routeTravelMinutes = routeTravelMinutes,
            routeDistanceKm = routeDistanceKm,
            currentOverride = timingOverride,
            onDismiss = { showAdjustDialog = false },
            onApplyToActivity = { newOverride ->
                showAdjustDialog = false
                onApplyActivityOverride(newOverride)
            },
            onSaveAsDefaults = { newSettings ->
                showAdjustDialog = false
                onSaveDefaultSettings(newSettings)
                onApplyActivityOverride(null)
            },
            onResetDefaults = {
                showAdjustDialog = false
                onApplyActivityOverride(null)
            },
        )
    }
}

@Composable
private fun TimelineQuickStrip(timeline: EventTimeline) {
    val colors = ApptividadesThemeDefaults.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.ground,
        border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickMetric(
                label = stringResource(R.string.timeline_strip_prepare),
                value = DominicanFormatter.formatTime12h(timeline.prepareStartAt.toLocalTime()),
                valueColor = colors.ink,
            )
            QuickMetric(
                label = stringResource(R.string.timeline_strip_leave),
                value = DominicanFormatter.formatTime12h(timeline.leaveAt.toLocalTime()),
                valueColor = colors.accentHover,
            )
            QuickMetric(
                label = stringResource(R.string.timeline_strip_free),
                value = DominicanFormatter.formatTime12h(timeline.freeAt.toLocalTime()),
                valueColor = colors.successText,
            )
            QuickMetric(
                label = stringResource(R.string.timeline_strip_total),
                value = EventTimelineCalculator.formatDurationCompact(timeline.totalMinutes),
                valueColor = colors.muted,
            )
        }
    }
}

@Composable
private fun QuickMetric(
    label: String,
    value: String,
    valueColor: Color,
) {
    val colors = ApptividadesThemeDefaults.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimelineStepRow(
    step: TimelineStep,
    isLast: Boolean,
) {
    val colors = ApptividadesThemeDefaults.colors

    val (icon, title, dotColor, isHighlight) = when (step.kind) {
        TimelineStepKind.PREPARE -> Quad(
            Icons.Outlined.SelfImprovement,
            stringResource(R.string.timeline_step_prepare),
            colors.ink,
            false,
        )
        TimelineStepKind.LEAVE -> Quad(
            Icons.Outlined.Alarm,
            stringResource(R.string.timeline_step_leave),
            colors.accent,
            true,
        )
        TimelineStepKind.TRAVEL -> Quad(
            Icons.Outlined.DirectionsCar,
            stringResource(R.string.timeline_step_travel),
            colors.info,
            false,
        )
        TimelineStepKind.INTERNAL_TRANSFER_IN -> Quad(
            Icons.AutoMirrored.Outlined.DirectionsWalk,
            stringResource(R.string.timeline_step_internal_in),
            colors.muted,
            false,
        )
        TimelineStepKind.SETUP -> Quad(
            Icons.Outlined.Build,
            stringResource(R.string.timeline_step_setup),
            colors.ink,
            false,
        )
        TimelineStepKind.PRE_START_MARGIN -> Quad(
            Icons.Outlined.HourglassBottom,
            stringResource(R.string.timeline_step_pre_margin),
            colors.accentHover,
            false,
        )
        TimelineStepKind.EVENT -> Quad(
            Icons.Outlined.MusicNote,
            stringResource(R.string.timeline_step_event),
            colors.ink,
            true,
        )
        TimelineStepKind.TEARDOWN -> Quad(
            Icons.Outlined.Inventory2,
            stringResource(R.string.timeline_step_teardown),
            colors.muted,
            false,
        )
        TimelineStepKind.INTERNAL_TRANSFER_OUT -> Quad(
            Icons.AutoMirrored.Outlined.DirectionsWalk,
            stringResource(R.string.timeline_step_internal_out),
            colors.muted,
            false,
        )
        TimelineStepKind.FREE -> Quad(
            Icons.Outlined.CheckCircle,
            stringResource(R.string.timeline_step_free),
            colors.success,
            true,
        )
    }

    val timeLabel = DominicanFormatter.formatTime12h(step.startAt.toLocalTime())
    val detailLabel = buildStepDetailLabel(step)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Time column (fixed width for clean vertical alignment)
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (isHighlight) dotColor else colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(66.dp)
                .padding(top = 3.dp),
            maxLines = 1,
        )

        // Vertical rail + node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(22.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (isHighlight) 0.16f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(26.dp)
                        .background(colors.line),
                )
            }
        }

        // Content column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 2.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (step.kind == TimelineStepKind.FREE) colors.successText else colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (step.durationMinutes != null) {
                    val durText = if (step.kind == TimelineStepKind.TRAVEL && step.travelBufferMinutes > 0) {
                        "${step.durationMinutes} + ${step.travelBufferMinutes} min"
                    } else {
                        EventTimelineCalculator.formatDurationCompact(step.durationMinutes)
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.neutralChipBg,
                    ) {
                        Text(
                            text = durText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = SpaceGroteskFontFamily,
                            ),
                            color = colors.neutralChipText,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!detailLabel.isNullOrBlank()) {
                Text(
                    text = detailLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun buildStepDetailLabel(step: TimelineStep): String? = when (step.kind) {
    TimelineStepKind.TRAVEL -> {
        val parts = buildList {
            step.distanceKm?.let { add("${DominicanFormatter.formatOneDecimal(it)} km") }
            if (step.isEstimatedWithoutTraffic) add("tiempo estimado (sin tráfico)")
            else add("recorrido ajustado manualmente")
            if (step.travelBufferMinutes > 0) add("incluye ${step.travelBufferMinutes} min de margen")
        }
        parts.joinToString(" · ")
    }
    TimelineStepKind.EVENT -> {
        step.endAt?.let { end ->
            "Hasta las ${DominicanFormatter.formatTime12h(end.toLocalTime())}"
        }
    }
    TimelineStepKind.LEAVE -> stringResource(R.string.timeline_step_leave_sub)
    TimelineStepKind.FREE -> stringResource(R.string.timeline_step_free_sub)
    else -> null
}

private data class Quad(
    val icon: ImageVector,
    val title: String,
    val color: Color,
    val isHighlight: Boolean,
)

@Composable
private fun TimelineAdjustDialog(
    eventDate: LocalDate,
    eventTime: LocalTime,
    settings: LabSettings,
    routeTravelMinutes: Int?,
    routeDistanceKm: Double?,
    currentOverride: ActivityTimelineOverride?,
    onDismiss: () -> Unit,
    onApplyToActivity: (ActivityTimelineOverride?) -> Unit,
    onSaveAsDefaults: (LabSettings) -> Unit,
    onResetDefaults: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors

    var prep by remember { mutableStateOf((currentOverride?.prepMinutes ?: settings.prepMinutes).toString()) }
    var travel by remember { mutableStateOf((currentOverride?.travelMinutes ?: routeTravelMinutes)?.toString().orEmpty()) }
    var travelBuffer by remember { mutableStateOf((currentOverride?.travelBufferMinutes ?: settings.travelBufferMinutes).toString()) }
    var internalIn by remember { mutableStateOf((currentOverride?.internalTransferInMinutes ?: settings.internalTransferInMinutes).toString()) }
    var setup by remember { mutableStateOf((currentOverride?.setupMinutes ?: settings.setupMinutes).toString()) }
    var preMargin by remember { mutableStateOf((currentOverride?.preStartMarginMinutes ?: settings.preStartMarginMinutes).toString()) }
    var eventMin by remember { mutableStateOf((currentOverride?.eventMinutes ?: settings.eventMinutes).toString()) }
    var teardown by remember { mutableStateOf((currentOverride?.teardownMinutes ?: settings.teardownMinutes).toString()) }
    var internalOut by remember { mutableStateOf((currentOverride?.internalTransferOutMinutes ?: settings.internalTransferOutMinutes).toString()) }

    fun parsedOverride(): ActivityTimelineOverride {
        val p = prep.toIntOrNull()?.coerceIn(0, 360)
        val t = travel.toIntOrNull()?.coerceIn(0, 360)
        val tb = travelBuffer.toIntOrNull()?.coerceIn(0, 180)
        val ii = internalIn.toIntOrNull()?.coerceIn(0, 180)
        val s = setup.toIntOrNull()?.coerceIn(0, 360)
        val pm = preMargin.toIntOrNull()?.coerceIn(0, 180)
        val ev = eventMin.toIntOrNull()?.coerceIn(1, 1440)
        val td = teardown.toIntOrNull()?.coerceIn(0, 360)
        val io = internalOut.toIntOrNull()?.coerceIn(0, 180)

        return ActivityTimelineOverride(
            prepMinutes = p?.takeIf { it != settings.prepMinutes },
            travelMinutes = t?.takeIf { it != routeTravelMinutes },
            travelBufferMinutes = tb?.takeIf { it != settings.travelBufferMinutes },
            internalTransferInMinutes = ii?.takeIf { it != settings.internalTransferInMinutes },
            setupMinutes = s?.takeIf { it != settings.setupMinutes },
            preStartMarginMinutes = pm?.takeIf { it != settings.preStartMarginMinutes },
            eventMinutes = ev?.takeIf { it != settings.eventMinutes },
            teardownMinutes = td?.takeIf { it != settings.teardownMinutes },
            internalTransferOutMinutes = io?.takeIf { it != settings.internalTransferOutMinutes },
        )
    }

    val livePreview = EventTimelineCalculator.calculate(
        eventDate = eventDate,
        eventTime = eventTime,
        settings = settings,
        routeTravelMinutes = routeTravelMinutes,
        routeDistanceKm = routeDistanceKm,
        override = parsedOverride(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.timeline_adjust_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimelineQuickStrip(timeline = livePreview)

                TimingStepperField(
                    label = stringResource(R.string.timeline_field_prep),
                    value = prep,
                    onValueChange = { prep = it },
                    step = 5,
                    max = 360,
                    testTag = "timeline_input_prep",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_travel),
                    value = travel,
                    onValueChange = { travel = it },
                    step = 5,
                    max = 360,
                    allowBlank = true,
                    placeholder = "Auto",
                    testTag = "timeline_input_travel",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_travel_buffer),
                    value = travelBuffer,
                    onValueChange = { travelBuffer = it },
                    step = 5,
                    max = 180,
                    testTag = "timeline_input_travel_buffer",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_internal_in),
                    value = internalIn,
                    onValueChange = { internalIn = it },
                    step = 5,
                    max = 180,
                    testTag = "timeline_input_internal_in",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_setup),
                    value = setup,
                    onValueChange = { setup = it },
                    step = 5,
                    max = 360,
                    testTag = "timeline_input_setup",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_pre_margin),
                    value = preMargin,
                    onValueChange = { preMargin = it },
                    step = 5,
                    max = 180,
                    testTag = "timeline_input_pre_margin",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_event),
                    value = eventMin,
                    onValueChange = { eventMin = it },
                    step = 15,
                    min = 15,
                    max = 1440,
                    testTag = "timeline_input_event",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_teardown),
                    value = teardown,
                    onValueChange = { teardown = it },
                    step = 5,
                    max = 360,
                    testTag = "timeline_input_teardown",
                )
                TimingStepperField(
                    label = stringResource(R.string.timeline_field_internal_out),
                    value = internalOut,
                    onValueChange = { internalOut = it },
                    step = 5,
                    max = 180,
                    testTag = "timeline_input_internal_out",
                )

                HorizontalDivider(thickness = 1.dp, color = colors.line)

                OutlinedButton(
                    onClick = {
                        val updatedSettings = settings.copy(
                            prepMinutes = (prep.toIntOrNull() ?: settings.prepMinutes).coerceIn(0, 240),
                            travelBufferMinutes = (travelBuffer.toIntOrNull() ?: settings.travelBufferMinutes).coerceIn(0, 120),
                            internalTransferInMinutes = (internalIn.toIntOrNull() ?: settings.internalTransferInMinutes).coerceIn(0, 180),
                            setupMinutes = (setup.toIntOrNull() ?: settings.setupMinutes).coerceIn(0, 240),
                            preStartMarginMinutes = (preMargin.toIntOrNull() ?: settings.preStartMarginMinutes).coerceIn(0, 180),
                            eventMinutes = (eventMin.toIntOrNull() ?: settings.eventMinutes).coerceIn(1, 1440),
                            teardownMinutes = (teardown.toIntOrNull() ?: settings.teardownMinutes).coerceIn(0, 240),
                            internalTransferOutMinutes = (internalOut.toIntOrNull() ?: settings.internalTransferOutMinutes).coerceIn(0, 180),
                        )
                        onSaveAsDefaults(updatedSettings)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("timeline_save_defaults_button"),
                ) {
                    Text(
                        text = stringResource(R.string.timeline_save_as_defaults),
                        color = colors.ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (currentOverride?.hasAnyOverride == true) {
                    TextButton(
                        onClick = onResetDefaults,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timeline_reset_defaults_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.timeline_reset_defaults),
                            color = colors.muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ov = parsedOverride()
                    onApplyToActivity(if (ov.hasAnyOverride) ov else null)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("timeline_apply_activity_button"),
            ) {
                Text(stringResource(R.string.timeline_apply_activity))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.form_cancel), color = colors.muted)
            }
        },
    )
}

@Composable
private fun TimingStepperField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    step: Int = 5,
    min: Int = 0,
    max: Int = 360,
    allowBlank: Boolean = false,
    placeholder: String = "0",
    testTag: String,
) {
    val colors = ApptividadesThemeDefaults.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val current = value.toIntOrNull() ?: min
                    val next = (current - step).coerceAtLeast(min)
                    onValueChange(next.toString())
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Text("−", color = colors.ink, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    if ((allowBlank && next.isEmpty()) || next.all { it.isDigit() }) {
                        onValueChange(next)
                    }
                },
                placeholder = { Text(placeholder) },
                suffix = { Text("min", style = MaterialTheme.typography.labelSmall, color = colors.muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.ink,
                    unfocusedBorderColor = colors.line,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag(testTag),
            )
            OutlinedButton(
                onClick = {
                    val current = value.toIntOrNull() ?: 0
                    val next = (current + step).coerceAtMost(max)
                    onValueChange(next.toString())
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Text("+", color = colors.ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}
