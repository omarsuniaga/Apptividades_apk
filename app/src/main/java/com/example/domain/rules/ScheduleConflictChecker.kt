package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.LabSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Rule 2.3 #5: Local schedule conflict warning.
 *
 * Same date, and:
 *   start - setup < other_start + event + teardown
 *   AND
 *   other_start - setup < start + event + teardown
 */
object ScheduleConflictChecker {

    fun findConflict(
        candidateDate: LocalDate,
        candidateTime: LocalTime?,
        existingActivities: List<Activity>,
        settings: LabSettings,
        excludeActivityId: String? = null,
        excludeSeriesId: String? = null,
        candidateEventMinutes: Int? = null,
        activityEventMinutesProvider: (Activity) -> Int? = { null },
    ): Activity? {
        if (candidateTime == null) return null

        val candidateStartMin = candidateTime.hour * 60 + candidateTime.minute
        val setup = settings.setupMinutes
        val candidateDuration = (candidateEventMinutes ?: settings.eventMinutes).coerceAtLeast(1)
        val candidateEndPlusTeardown = candidateDuration + settings.teardownMinutes

        return existingActivities.firstOrNull { other ->
            if (excludeActivityId != null && other.id == excludeActivityId) return@firstOrNull false
            if (excludeSeriesId != null && other.seriesId == excludeSeriesId) return@firstOrNull false
            if (other.eventDate != candidateDate) return@firstOrNull false
            val otherTime = other.eventTime ?: return@firstOrNull false

            val otherStartMin = otherTime.hour * 60 + otherTime.minute
            val otherDuration = (activityEventMinutesProvider(other) ?: settings.eventMinutes).coerceAtLeast(1)
            val otherEndPlusTeardown = otherDuration + settings.teardownMinutes
            (candidateStartMin - setup) < (otherStartMin + otherEndPlusTeardown) &&
                (otherStartMin - setup) < (candidateStartMin + candidateEndPlusTeardown)
        }
    }
}

/**
 * Rule 2.3 #8: Departure & preparation time calculation.
 *
 * leave_at   = event_start - travel_minutes - travel_buffer_minutes - setup_minutes
 * prepare_at = leave_at - prep_minutes
 */
data class DepartureSchedule(
    val prepareAt: LocalDateTime,
    val leaveAt: LocalDateTime,
    val estimatedArrivalAt: LocalDateTime,
    val travelMinutes: Int,
)

object DepartureTimeCalculator {

    // Default fallback origin in Bávaro / Punta Cana when GPS and home coordinates are unset
    const val DEFAULT_BAVARO_LAT = 18.6813
    const val DEFAULT_BAVARO_LNG = -68.4543
    const val RESCHEDULE_THRESHOLD_MINUTES = 5L

    fun calculate(
        eventDate: LocalDate,
        eventTime: LocalTime,
        travelMinutes: Int,
        settings: LabSettings,
    ): DepartureSchedule {
        val safeTravel = travelMinutes.coerceAtLeast(0)
        val eventStart = LocalDateTime.of(eventDate, eventTime)
        val totalBeforeEvent = safeTravel.toLong() +
            settings.travelBufferMinutes.coerceAtLeast(0).toLong() +
            settings.setupMinutes.coerceAtLeast(0).toLong()
        val leaveAt = eventStart.minusMinutes(totalBeforeEvent)
        val prepareAt = leaveAt.minusMinutes(settings.prepMinutes.coerceAtLeast(0).toLong())
        val estimatedArrival = leaveAt.plusMinutes(safeTravel.toLong())
        return DepartureSchedule(
            prepareAt = prepareAt,
            leaveAt = leaveAt,
            estimatedArrivalAt = estimatedArrival,
            travelMinutes = safeTravel,
        )
    }

    /**
     * Full timeline-aligned departure schedule used by platform alarms so notifications
     * match the artist's EventTimelineCard (including internal transfer, pre-start margin,
     * and any per-activity timing overrides).
     */
    fun calculateFromTimeline(
        eventDate: LocalDate,
        eventTime: LocalTime,
        travelMinutes: Int?,
        settings: LabSettings,
        override: com.example.domain.model.ActivityTimelineOverride? = null,
    ): DepartureSchedule {
        val timeline = EventTimelineCalculator.calculate(
            eventDate = eventDate,
            eventTime = eventTime,
            settings = settings,
            routeTravelMinutes = travelMinutes,
            override = override,
        )
        val effectiveTravel = timeline.config.travelMinutes ?: 0
        val estimatedArrival = timeline.leaveAt.plusMinutes(effectiveTravel.toLong())
        return DepartureSchedule(
            prepareAt = timeline.prepareStartAt,
            leaveAt = timeline.leaveAt,
            estimatedArrivalAt = estimatedArrival,
            travelMinutes = effectiveTravel,
        )
    }

    /**
     * Offline / provisional travel-time estimate between two GPS coordinates using Haversine distance
     * assuming Punta Cana boulevard/resort road speed (~40 km/h + 5 min hotel gate/parking).
     */
    fun estimateOfflineTravelMinutes(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): Int {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(destLat - originLat)
        val dLng = Math.toRadians(destLng - originLng)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(originLat)) *
            kotlin.math.cos(Math.toRadians(destLat)) *
            kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        val straightKm = earthRadiusKm * c
        val roadKm = straightKm * 1.28 // road winding factor
        val drivingMinutes = (roadKm / 40.0) * 60.0
        return kotlin.math.ceil(drivingMinutes + 5.0).toInt().coerceIn(10, 120)
    }

    fun shouldReschedule(
        previousLeaveAtMillis: Long?,
        newLeaveAtMillis: Long,
    ): Boolean {
        if (previousLeaveAtMillis == null) return true
        val diffMinutes = kotlin.math.abs(newLeaveAtMillis - previousLeaveAtMillis) / 60_000L
        return diffMinutes >= RESCHEDULE_THRESHOLD_MINUTES
    }

    fun estimatedArrivalIfLeavingNow(
        now: LocalDateTime,
        travelMinutes: Int,
    ): LocalDateTime = now.plusMinutes(travelMinutes.coerceAtLeast(0).toLong())
}
