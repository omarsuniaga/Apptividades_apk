package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.LabSettings
import java.time.Duration
import java.time.LocalDateTime

/** Where the trip to an event starts. */
enum class RouteOriginKind {
    PREVIOUS_EVENT,
    HOME,
    CURRENT_LOCATION,
    DEFAULT_AREA,
}

data class FallbackOrigin(
    val kind: RouteOriginKind,
    val latitude: Double,
    val longitude: Double,
)

sealed interface ChainDecision {
    data object NoPreviousEvent : ChainDecision

    /** The trip starts at [previous]. [shortfallMinutes] is positive when there is not enough time. */
    data class Chained(
        val previous: Activity,
        val freeAt: LocalDateTime,
        val shortfallMinutes: Int,
    ) : ChainDecision

    data class TooMuchFreeTime(val previous: Activity) : ChainDecision

    /** The events are close enough to chain, but [previous] has no map pin to start from. */
    data class PreviousWithoutPin(val previous: Activity) : ChainDecision
}

/**
 * Decides whether the artist travels to an event straight from the previous one of the day:
 * when the free time between being done there and having to leave is [MAX_FREE_MINUTES] or less.
 */
object EventChainPlanner {

    const val MAX_FREE_MINUTES = 120L

    fun previousSameDay(target: Activity, activities: List<Activity>): Activity? {
        val targetTime = target.eventTime ?: return null
        return activities
            .filter { other ->
                val time = other.eventTime
                other.id != target.id &&
                    other.eventDate == target.eventDate &&
                    time != null &&
                    time.isBefore(targetTime)
            }
            .maxByOrNull { it.eventTime!! }
    }

    /**
     * @param travelMinutesFromPrevious driving time from the previous event to [target];
     * null when either of them has no map pin.
     */
    fun plan(
        target: Activity,
        activities: List<Activity>,
        settings: LabSettings,
        overrides: Map<String, ActivityTimelineOverride>,
        travelMinutesFromPrevious: Int?,
    ): ChainDecision {
        val targetTime = target.eventTime ?: return ChainDecision.NoPreviousEvent
        if (!target.hasCoordinates) return ChainDecision.NoPreviousEvent
        val previous = previousSameDay(target, activities) ?: return ChainDecision.NoPreviousEvent

        val freeAt = EventTimelineCalculator.calculate(
            eventDate = previous.eventDate,
            eventTime = previous.eventTime!!,
            settings = settings,
            override = overrides[previous.id],
        ).freeAt
        val leaveAt = EventTimelineCalculator.calculate(
            eventDate = target.eventDate,
            eventTime = targetTime,
            settings = settings,
            routeTravelMinutes = travelMinutesFromPrevious ?: 0,
            override = overrides[target.id],
        ).leaveAt
        val freeMinutes = Duration.between(freeAt, leaveAt).toMinutes()

        return when {
            freeMinutes > MAX_FREE_MINUTES -> ChainDecision.TooMuchFreeTime(previous)
            !previous.hasCoordinates || travelMinutesFromPrevious == null ->
                ChainDecision.PreviousWithoutPin(previous)
            else -> ChainDecision.Chained(
                previous = previous,
                freeAt = freeAt,
                shortfallMinutes = (-freeMinutes).coerceAtLeast(0).toInt(),
            )
        }
    }

    /** Origin for a trip that does not start at a previous event. */
    fun fallbackOrigin(
        homeLatitude: Double?,
        homeLongitude: Double?,
        currentLatitude: Double?,
        currentLongitude: Double?,
    ): FallbackOrigin = when {
        homeLatitude != null && homeLongitude != null ->
            FallbackOrigin(RouteOriginKind.HOME, homeLatitude, homeLongitude)
        currentLatitude != null && currentLongitude != null ->
            FallbackOrigin(RouteOriginKind.CURRENT_LOCATION, currentLatitude, currentLongitude)
        else -> FallbackOrigin(
            RouteOriginKind.DEFAULT_AREA,
            DepartureTimeCalculator.DEFAULT_BAVARO_LAT,
            DepartureTimeCalculator.DEFAULT_BAVARO_LNG,
        )
    }
}
