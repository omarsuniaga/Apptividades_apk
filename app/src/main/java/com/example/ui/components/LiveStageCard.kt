package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alarms.NotificationHelper
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.FeaturedLiveActivity
import com.example.domain.rules.LiveMilestoneKind
import com.example.domain.rules.LiveStagePhase
import com.example.domain.rules.SupplierMessagePreset
import com.example.ui.theme.ApptividadesThemeDefaults
import com.example.ui.theme.SpaceGroteskFontFamily

/**
 * Editorial Studio "En vivo hoy" card shown at the top of Inicio when the artist has a gig today.
 * Displays the live phase along the timeline, animated progress, next milestone countdown,
 * and one-tap stage actions (WhatsApp, Google Maps, Waze, and Modo Contingencia control).
 */
@Composable
fun LiveStageCard(
    featured: FeaturedLiveActivity,
    venueDisplay: String,
    supplierDisplay: String,
    showContingencyControl: Boolean,
    isUnderControl: Boolean,
    onOpenActivity: () -> Unit,
    onQuickMessage: (SupplierMessagePreset?) -> Unit,
    onToggleUnderControl: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val activity = featured.activity
    val snapshot = featured.snapshot
    val timeline = snapshot.timeline
    val phase = snapshot.phase

    val isLiveActive = phase != LiveStagePhase.UPCOMING_TODAY && phase != LiveStagePhase.COMPLETED
    val animatedProgress by animateFloatAsState(
        targetValue = snapshot.progressFraction,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "live_stage_progress",
    )

    val phaseBadgeText = when (phase) {
        LiveStagePhase.UPCOMING_TODAY -> stringResource(R.string.live_stage_phase_upcoming)
        LiveStagePhase.PREPARING -> stringResource(R.string.live_stage_phase_preparing)
        LiveStagePhase.IN_TRANSIT -> stringResource(R.string.live_stage_phase_transit)
        LiveStagePhase.ON_SITE_SETUP -> stringResource(R.string.live_stage_phase_setup)
        LiveStagePhase.ON_STAGE -> stringResource(R.string.live_stage_phase_on_stage)
        LiveStagePhase.WRAPPING_UP -> stringResource(R.string.live_stage_phase_wrapping)
        LiveStagePhase.COMPLETED -> stringResource(R.string.live_stage_phase_completed)
    }

    val phaseDotColor = when {
        snapshot.isUrgent -> colors.danger
        phase == LiveStagePhase.ON_STAGE -> colors.success
        phase == LiveStagePhase.COMPLETED -> colors.success
        isLiveActive -> colors.accent
        else -> colors.info
    }

    val countdownText = if (phase == LiveStagePhase.COMPLETED) {
        ""
    } else {
        val mins = snapshot.minutesToNextMilestone
        val h = mins / 60L
        val m = mins % 60L
        when {
            mins <= 0L -> stringResource(R.string.live_stage_countdown_now)
            h > 0L && m > 0L -> stringResource(R.string.live_stage_countdown_hr_min, h.toInt(), m.toInt())
            h > 0L -> stringResource(R.string.live_stage_countdown_hr, h.toInt())
            else -> stringResource(R.string.live_stage_countdown_min, mins.toInt())
        }
    }

    val nextMilestoneTime = snapshot.nextMilestoneAt?.toLocalTime()?.let {
        DominicanFormatter.formatTime12h(it)
    }.orEmpty()

    val nextMilestoneLine = when (snapshot.nextMilestoneKind) {
        LiveMilestoneKind.PREPARE -> stringResource(R.string.live_stage_next_prepare, nextMilestoneTime)
        LiveMilestoneKind.LEAVE -> stringResource(R.string.live_stage_next_leave, nextMilestoneTime)
        LiveMilestoneKind.ARRIVE -> stringResource(R.string.live_stage_next_arrive, nextMilestoneTime)
        LiveMilestoneKind.EVENT_START -> stringResource(R.string.live_stage_next_start, nextMilestoneTime)
        LiveMilestoneKind.EVENT_END -> stringResource(R.string.live_stage_next_end, nextMilestoneTime)
        LiveMilestoneKind.FREE -> stringResource(R.string.live_stage_next_free, nextMilestoneTime)
        LiveMilestoneKind.NONE -> stringResource(R.string.live_stage_done_note)
    }

    val venueLabel = venueDisplay.ifBlank { activity.venueName }.ifBlank { activity.title }
    val timeWindow = "${DominicanFormatter.formatTime12h(timeline.eventStartAt.toLocalTime())} – ${
        DominicanFormatter.formatTime12h(timeline.eventEndAt.toLocalTime())
    }"
    val subtitle = buildList {
        add(timeWindow)
        if (supplierDisplay.isNotBlank()) add(supplierDisplay)
    }.joinToString(" · ")

    val destinationQuery = listOfNotNull(
        venueLabel.takeIf { it.isNotBlank() },
        activity.venueAddress?.takeIf { it.isNotBlank() },
    ).joinToString(", ").ifBlank { null }
    val hasDestination = (activity.latitude != null && activity.longitude != null) || destinationQuery != null

    val (messageCtaText, smartPreset) = when (phase) {
        LiveStagePhase.ON_SITE_SETUP ->
            stringResource(R.string.live_stage_cta_arrived) to SupplierMessagePreset.ARRIVED
        LiveStagePhase.WRAPPING_UP, LiveStagePhase.COMPLETED ->
            stringResource(R.string.live_stage_cta_finished) to SupplierMessagePreset.FINISHED
        else ->
            stringResource(R.string.live_stage_cta_message) to null
    }

    val cardBorder = when {
        snapshot.isUrgent -> BorderStroke(1.5.dp, colors.danger.copy(alpha = 0.65f))
        isLiveActive -> BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.55f))
        else -> BorderStroke(1.dp, colors.line)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .testTag("inicio_live_stage_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top eyebrow pill + countdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenActivity),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (snapshot.isUrgent) colors.dangerBg else colors.accentSoft,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(phaseDotColor),
                        )
                        Text(
                            text = "${stringResource(R.string.live_stage_eyebrow)} · $phaseBadgeText",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (snapshot.isUrgent) colors.dangerText else colors.accentHover,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (countdownText.isNotEmpty()) {
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (snapshot.isUrgent) colors.danger else colors.ink,
                        maxLines = 1,
                    )
                }
            }

            // Venue + Time window
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenActivity),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = venueLabel,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 5-node stage progress pipeline
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.neutralChipBg),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                when {
                                    phase == LiveStagePhase.COMPLETED -> colors.success
                                    snapshot.isUrgent -> colors.danger
                                    else -> colors.accent
                                }
                            ),
                    )
                }

                val stepLabels = listOf(
                    stringResource(R.string.live_stage_step_prep),
                    stringResource(R.string.live_stage_step_leave),
                    stringResource(R.string.live_stage_step_setup),
                    stringResource(R.string.live_stage_step_show),
                    stringResource(R.string.live_stage_step_free),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    stepLabels.forEachIndexed { idx, label ->
                        val isCurrent = isLiveActive && idx == phase.stepIndex
                        val isReached = (isLiveActive && idx <= phase.stepIndex) || phase == LiveStagePhase.COMPLETED
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = when {
                                isCurrent -> colors.accentHover
                                isReached -> colors.ink
                                else -> colors.muted
                            },
                            textAlign = when (idx) {
                                0 -> TextAlign.Start
                                stepLabels.lastIndex -> TextAlign.End
                                else -> TextAlign.Center
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                    }
                }
            }

            // Next milestone helper
            Text(
                text = nextMilestoneLine,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (snapshot.isUrgent) colors.dangerText else colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Primary 1-tap actions: Smart WhatsApp + Maps + Waze
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQuickMessage(smartPreset)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("live_stage_whatsapp_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = messageCtaText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hasDestination) {
                    Surface(
                        onClick = {
                            NotificationHelper.openGoogleMaps(
                                context = context,
                                venueName = destinationQuery,
                                latitude = activity.latitude,
                                longitude = activity.longitude,
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.neutralChipBg,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("live_stage_maps_btn"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Directions,
                                contentDescription = stringResource(R.string.action_open_google_maps),
                                tint = colors.ink,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            NotificationHelper.openWaze(
                                context = context,
                                venueName = destinationQuery,
                                latitude = activity.latitude,
                                longitude = activity.longitude,
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.infoBg,
                        border = BorderStroke(1.dp, colors.info.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("live_stage_waze_btn"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.NearMe,
                                contentDescription = stringResource(R.string.action_open_waze),
                                tint = colors.info,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Compact contingency control strip for today's upcoming/in-progress gig
            if (showContingencyControl) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUnderControl) colors.successBg else colors.ground,
                    border = BorderStroke(
                        1.dp,
                        if (isUnderControl) colors.success.copy(alpha = 0.35f) else colors.line,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_stage_control_btn"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleUnderControl(!isUnderControl)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = if (isUnderControl) colors.success else colors.accentHover,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (isUnderControl) {
                                stringResource(R.string.contingency_status_under_control)
                            } else {
                                stringResource(R.string.contingency_cta_mark_under_control)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isUnderControl) colors.successText else colors.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isUnderControl) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleUnderControl(false)
                                },
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.contingency_cta_reactivate),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
