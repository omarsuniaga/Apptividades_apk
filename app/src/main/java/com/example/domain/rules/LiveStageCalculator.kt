package com.example.domain.rules

import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.LabSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class LiveStagePhase(val stepIndex: Int) {
    UPCOMING_TODAY(0),
    PREPARING(0),
    IN_TRANSIT(1),
    ON_SITE_SETUP(2),
    ON_STAGE(3),
    WRAPPING_UP(4),
    COMPLETED(4),
}

enum class LiveMilestoneKind {
    PREPARE,
    LEAVE,
    ARRIVE,
    EVENT_START,
    EVENT_END,
    FREE,
    NONE,
}

data class LiveStageSnapshot(
    val phase: LiveStagePhase,
    val timeline: EventTimeline,
    val progressFraction: Float,
    val minutesToNextMilestone: Long,
    val nextMilestoneKind: LiveMilestoneKind,
    val nextMilestoneAt: LocalDateTime?,
    val isUrgent: Boolean,
)

data class FeaturedLiveActivity(
    val activity: Activity,
    val snapshot: LiveStageSnapshot,
)

/**
 * Pure domain evaluator that determines the artist's live phase along an [EventTimeline]
 * and selects the single most relevant activity for the "En vivo hoy" card on Inicio.
 */
object LiveStageCalculator {

    private const val RECENTLY_COMPLETED_WINDOW_MINUTES = 45L
    private const val URGENT_MILESTONE_WINDOW_MINUTES = 10L

    fun evaluate(
        now: LocalDateTime,
        timeline: EventTimeline,
    ): LiveStageSnapshot {
        val phase = when {
            now.isBefore(timeline.prepareStartAt) -> LiveStagePhase.UPCOMING_TODAY
            now.isBefore(timeline.leaveAt) -> LiveStagePhase.PREPARING
            timeline.leaveAt.isBefore(timeline.arriveAtVenueAt) && now.isBefore(timeline.arriveAtVenueAt) ->
                LiveStagePhase.IN_TRANSIT
            now.isBefore(timeline.eventStartAt) -> LiveStagePhase.ON_SITE_SETUP
            now.isBefore(timeline.eventEndAt) -> LiveStagePhase.ON_STAGE
            now.isBefore(timeline.freeAt) -> LiveStagePhase.WRAPPING_UP
            else -> LiveStagePhase.COMPLETED
        }

        val (milestoneKind, milestoneAt) = when (phase) {
            LiveStagePhase.UPCOMING_TODAY -> LiveMilestoneKind.PREPARE to timeline.prepareStartAt
            LiveStagePhase.PREPARING -> LiveMilestoneKind.LEAVE to timeline.leaveAt
            LiveStagePhase.IN_TRANSIT -> LiveMilestoneKind.ARRIVE to timeline.arriveAtVenueAt
            LiveStagePhase.ON_SITE_SETUP -> LiveMilestoneKind.EVENT_START to timeline.eventStartAt
            LiveStagePhase.ON_STAGE -> LiveMilestoneKind.EVENT_END to timeline.eventEndAt
            LiveStagePhase.WRAPPING_UP -> LiveMilestoneKind.FREE to timeline.freeAt
            LiveStagePhase.COMPLETED -> LiveMilestoneKind.NONE to null
        }

        val minutesToNext = if (milestoneAt != null) {
            Duration.between(now, milestoneAt).toMinutes().coerceAtLeast(0L)
        } else {
            0L
        }

        val totalSeconds = Duration.between(timeline.prepareStartAt, timeline.freeAt).seconds
        val progress = when {
            now.isBefore(timeline.prepareStartAt) -> 0f
            !now.isBefore(timeline.freeAt) -> 1f
            totalSeconds <= 0L -> 1f
            else -> {
                val elapsed = Duration.between(timeline.prepareStartAt, now).seconds.coerceAtLeast(0L)
                (elapsed.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
            }
        }

        val isUrgent = when (phase) {
            LiveStagePhase.PREPARING -> minutesToNext <= URGENT_MILESTONE_WINDOW_MINUTES
            LiveStagePhase.IN_TRANSIT -> minutesToNext <= URGENT_MILESTONE_WINDOW_MINUTES
            LiveStagePhase.ON_SITE_SETUP -> minutesToNext <= URGENT_MILESTONE_WINDOW_MINUTES
            else -> false
        }

        return LiveStageSnapshot(
            phase = phase,
            timeline = timeline,
            progressFraction = progress,
            minutesToNextMilestone = minutesToNext,
            nextMilestoneKind = milestoneKind,
            nextMilestoneAt = milestoneAt,
            isUrgent = isUrgent,
        )
    }

    /**
     * Picks the featured gig for today's Live Stage Card:
     * 1. First gig currently in progress (PREPARING..WRAPPING_UP).
     * 2. Otherwise the next upcoming gig today (UPCOMING_TODAY).
     * 3. Otherwise the most recently completed gig if finished within the last 45 minutes.
     */
    fun selectFeaturedTodayActivity(
        now: LocalDateTime,
        today: LocalDate,
        activities: List<Activity>,
        settings: LabSettings,
        routes: Map<String, CachedActivityRoute> = emptyMap(),
        timings: Map<String, ActivityTimelineOverride> = emptyMap(),
    ): FeaturedLiveActivity? {
        val candidates = activities
            .asSequence()
            .filter { it.eventDate == today && it.eventTime != null }
            .sortedBy { it.eventTime }
            .map { act ->
                val route = routes[act.id]
                val timeline = EventTimelineCalculator.calculate(
                    eventDate = act.eventDate,
                    eventTime = act.eventTime!!,
                    settings = settings,
                    routeTravelMinutes = route?.effectiveDriveMinutes,
                    routeDistanceKm = route?.distanceKm,
                    override = route.timingOverride(timings[act.id]),
                )
                FeaturedLiveActivity(
                    activity = act,
                    snapshot = evaluate(now, timeline),
                )
            }
            .toList()

        if (candidates.isEmpty()) return null

        val inProgress = candidates.firstOrNull { item ->
            item.snapshot.phase != LiveStagePhase.UPCOMING_TODAY &&
                item.snapshot.phase != LiveStagePhase.COMPLETED
        }
        if (inProgress != null) return inProgress

        val upcoming = candidates.firstOrNull { it.snapshot.phase == LiveStagePhase.UPCOMING_TODAY }
        if (upcoming != null) return upcoming

        return candidates.lastOrNull { item ->
            val minutesSinceFree = Duration.between(item.snapshot.timeline.freeAt, now).toMinutes()
            minutesSinceFree in 0L..RECENTLY_COMPLETED_WINDOW_MINUTES
        }
    }
}
