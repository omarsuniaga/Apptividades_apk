package com.example.domain.rules

import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.LabSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class TimelineStepKind {
    PREPARE,
    LEAVE,
    TRAVEL,
    INTERNAL_TRANSFER_IN,
    SETUP,
    PRE_START_MARGIN,
    EVENT,
    TEARDOWN,
    INTERNAL_TRANSFER_OUT,
    FREE,
}

data class TimelineStep(
    val kind: TimelineStepKind,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime? = null,
    val durationMinutes: Int? = null,
    val travelBufferMinutes: Int = 0,
    val distanceKm: Double? = null,
    val isEstimatedWithoutTraffic: Boolean = false,
)

data class EffectiveTimelineConfig(
    val prepMinutes: Int,
    val travelMinutes: Int?,
    val travelBufferMinutes: Int,
    val internalTransferInMinutes: Int,
    val setupMinutes: Int,
    val preStartMarginMinutes: Int,
    val eventMinutes: Int,
    val teardownMinutes: Int,
    val internalTransferOutMinutes: Int,
    val distanceKm: Double?,
    val travelIsManualOverride: Boolean,
)

data class EventTimeline(
    val config: EffectiveTimelineConfig,
    val prepareStartAt: LocalDateTime,
    val leaveAt: LocalDateTime,
    val arriveAtVenueAt: LocalDateTime,
    val setupStartAt: LocalDateTime,
    val marginStartAt: LocalDateTime,
    val eventStartAt: LocalDateTime,
    val eventEndAt: LocalDateTime,
    val teardownEndAt: LocalDateTime,
    val freeAt: LocalDateTime,
    val totalMinutes: Int,
    val hasTravelEstimate: Boolean,
    val steps: List<TimelineStep>,
) {
    /** Duration without the home preparation, for trips that start at another event. */
    val minutesFromLeaving: Int
        get() = Duration.between(leaveAt, freeAt).toMinutes().toInt().coerceAtLeast(0)
}

/**
 * Computes the complete artist event timeline from initial preparation through
 * getting completely free after teardown and internal exit transfer.
 */
object EventTimelineCalculator {

    fun calculate(
        eventDate: LocalDate,
        eventTime: LocalTime,
        settings: LabSettings,
        routeTravelMinutes: Int? = null,
        routeDistanceKm: Double? = null,
        override: ActivityTimelineOverride? = null,
    ): EventTimeline {
        val prep = (override?.prepMinutes ?: settings.prepMinutes).coerceIn(0, 360)
        val travelOverride = override?.travelMinutes?.coerceIn(0, 360)
        val baseTravel = travelOverride ?: routeTravelMinutes?.coerceIn(0, 360)
        val travelBuffer = (override?.travelBufferMinutes ?: settings.travelBufferMinutes).coerceIn(0, 180)
        val internalIn = (override?.internalTransferInMinutes ?: settings.internalTransferInMinutes).coerceIn(0, 180)
        val setup = (override?.setupMinutes ?: settings.setupMinutes).coerceIn(0, 360)
        val preStartMargin = (override?.preStartMarginMinutes ?: settings.preStartMarginMinutes).coerceIn(0, 180)
        val eventDuration = (override?.eventMinutes ?: settings.eventMinutes).coerceIn(1, 1440)
        val teardown = (override?.teardownMinutes ?: settings.teardownMinutes).coerceIn(0, 360)
        val internalOut = (override?.internalTransferOutMinutes ?: settings.internalTransferOutMinutes).coerceIn(0, 180)

        val eventStart = LocalDateTime.of(eventDate, eventTime)
        val marginStart = eventStart.minusMinutes(preStartMargin.toLong())
        val setupStart = marginStart.minusMinutes(setup.toLong())
        val arriveAtVenue = setupStart.minusMinutes(internalIn.toLong())

        val hasTravel = baseTravel != null
        val totalTravelMinutes = if (baseTravel != null) baseTravel + travelBuffer else 0
        val leaveAt = arriveAtVenue.minusMinutes(totalTravelMinutes.toLong())
        val prepareStart = leaveAt.minusMinutes(prep.toLong())

        val eventEnd = eventStart.plusMinutes(eventDuration.toLong())
        val teardownEnd = eventEnd.plusMinutes(teardown.toLong())
        val freeAt = teardownEnd.plusMinutes(internalOut.toLong())

        val totalMinutes = Duration.between(prepareStart, freeAt).toMinutes().toInt().coerceAtLeast(0)
        val effectiveDistance = if (travelOverride != null) null else routeDistanceKm

        val steps = buildList {
            add(
                TimelineStep(
                    kind = TimelineStepKind.PREPARE,
                    startAt = prepareStart,
                    endAt = leaveAt,
                    durationMinutes = prep,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.LEAVE,
                    startAt = leaveAt,
                )
            )
            if (baseTravel != null) {
                add(
                    TimelineStep(
                        kind = TimelineStepKind.TRAVEL,
                        startAt = leaveAt,
                        endAt = arriveAtVenue,
                        durationMinutes = baseTravel,
                        travelBufferMinutes = travelBuffer,
                        distanceKm = effectiveDistance,
                        isEstimatedWithoutTraffic = travelOverride == null,
                    )
                )
            }
            add(
                TimelineStep(
                    kind = TimelineStepKind.INTERNAL_TRANSFER_IN,
                    startAt = arriveAtVenue,
                    endAt = setupStart,
                    durationMinutes = internalIn,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.SETUP,
                    startAt = setupStart,
                    endAt = marginStart,
                    durationMinutes = setup,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.PRE_START_MARGIN,
                    startAt = marginStart,
                    endAt = eventStart,
                    durationMinutes = preStartMargin,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.EVENT,
                    startAt = eventStart,
                    endAt = eventEnd,
                    durationMinutes = eventDuration,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.TEARDOWN,
                    startAt = eventEnd,
                    endAt = teardownEnd,
                    durationMinutes = teardown,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.INTERNAL_TRANSFER_OUT,
                    startAt = teardownEnd,
                    endAt = freeAt,
                    durationMinutes = internalOut,
                )
            )
            add(
                TimelineStep(
                    kind = TimelineStepKind.FREE,
                    startAt = freeAt,
                )
            )
        }

        return EventTimeline(
            config = EffectiveTimelineConfig(
                prepMinutes = prep,
                travelMinutes = baseTravel,
                travelBufferMinutes = travelBuffer,
                internalTransferInMinutes = internalIn,
                setupMinutes = setup,
                preStartMarginMinutes = preStartMargin,
                eventMinutes = eventDuration,
                teardownMinutes = teardown,
                internalTransferOutMinutes = internalOut,
                distanceKm = effectiveDistance,
                travelIsManualOverride = travelOverride != null,
            ),
            prepareStartAt = prepareStart,
            leaveAt = leaveAt,
            arriveAtVenueAt = arriveAtVenue,
            setupStartAt = setupStart,
            marginStartAt = marginStart,
            eventStartAt = eventStart,
            eventEndAt = eventEnd,
            teardownEndAt = teardownEnd,
            freeAt = freeAt,
            totalMinutes = totalMinutes,
            hasTravelEstimate = hasTravel,
            steps = steps,
        )
    }

    fun formatDurationCompact(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours > 0 && mins > 0 -> "${hours} h ${mins} min"
            hours > 0 -> "${hours} h"
            else -> "${mins} min"
        }
    }
}

object CalendarLabelFormatter {

    /**
     * Produces a concise supplier label for calendar grid cells without deforming layout.
     * Prefers the supplier display name (or venue/title fallback) trimmed cleanly; visual
     * width truncation is further enforced by maxLines=1 + TextOverflow.Ellipsis in Compose.
     */
    fun shortSupplierLabel(
        resolvedSupplierDisplay: String,
        rawSupplierName: String,
        fallbackLabel: String = "",
    ): String {
        val clean = resolvedSupplierDisplay.trim()
            .ifEmpty { rawSupplierName.trim() }
            .ifEmpty { fallbackLabel.trim() }
        if (clean.isEmpty()) return ""
        val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size <= 1) return clean
        val first = words[0]
        val keepTwo = first.length <= 3 && words.size >= 2
        return if (keepTwo) "$first ${words[1]}" else first
    }
}
