package com.example.domain.rules

import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import java.time.LocalDateTime

/**
 * Times the artist measured for a venue. Walking, setup and margin belong to the venue;
 * driving depends on where the trip starts.
 */
data class TrackedRoute(
    val venueKey: String,
    val walkMinutes: Int,
    val setupMinutes: Int,
    val marginMinutes: Int,
    val driveMinutesByOrigin: Map<String, Int> = emptyMap(),
)

object TrackedRoutes {

    const val HOME_ORIGIN = "home"

    private const val MIN_VARIANT_KEY_LENGTH = 5

    fun venueKey(venueName: String): String = NameNormalizer.normalizeKey(venueName)

    /**
     * The saved route for [venueName]: the exact name first, so a spot inside a hotel can
     * have its own times, otherwise another variant of the same hotel name.
     */
    fun find(routes: Map<String, TrackedRoute>, venueName: String): TrackedRoute? =
        routes[matchingKey(routes.keys, venueKey(venueName))]

    /** Same rule the app uses to merge hotel names: one key is a prefix of the other. */
    private fun matchingKey(keys: Set<String>, wanted: String): String? {
        if (wanted in keys) return wanted
        if (wanted.length < MIN_VARIANT_KEY_LENGTH) return null
        val variants = keys.filter {
            it.length >= MIN_VARIANT_KEY_LENGTH && (it.startsWith(wanted) || wanted.startsWith(it))
        }
        // The closest one: the longest saved name contained in the wanted one comes first.
        return variants.filter { wanted.startsWith(it) }.maxByOrNull { it.length }
            ?: variants.minByOrNull { it.length }
    }

    fun driveFor(route: TrackedRoute, originKey: String): Int? =
        route.driveMinutesByOrigin[matchingKey(route.driveMinutesByOrigin.keys, originKey) ?: originKey]

    /** Every trip that does not start at another venue counts as leaving from home. */
    fun originKey(kind: RouteOriginKind?, originVenueName: String?): String =
        if (kind == RouteOriginKind.PREVIOUS_EVENT) {
            "venue:${venueKey(originVenueName.orEmpty())}"
        } else {
            HOME_ORIGIN
        }

    fun update(
        existing: TrackedRoute?,
        venueName: String,
        originKey: String,
        driveMinutes: Int,
        walkMinutes: Int,
        setupMinutes: Int,
        marginMinutes: Int,
    ): TrackedRoute = TrackedRoute(
        venueKey = venueKey(venueName),
        walkMinutes = walkMinutes.coerceIn(0, 180),
        setupMinutes = setupMinutes.coerceIn(0, 360),
        marginMinutes = marginMinutes.coerceIn(0, 180),
        driveMinutesByOrigin = existing?.driveMinutesByOrigin.orEmpty() +
            (originKey to driveMinutes.coerceIn(0, 360)),
    )
}

/** Replaces any tracked values on this route with the ones of [tracked]. */
fun CachedActivityRoute.withTracked(tracked: TrackedRoute?): CachedActivityRoute = copy(
    trackedDriveMinutes = tracked?.let {
        TrackedRoutes.driveFor(it, TrackedRoutes.originKey(originKind, originVenueName))
    },
    trackedWalkMinutes = tracked?.walkMinutes,
    trackedSetupMinutes = tracked?.setupMinutes,
    trackedMarginMinutes = tracked?.marginMinutes,
)

val CachedActivityRoute.effectiveDriveMinutes: Int
    get() = trackedDriveMinutes ?: travelMinutes

/**
 * Timing for one activity: what was adjusted for that day wins, then the tracked route.
 * With a tracked route its margin is the only slack, so the latest departure is exactly
 * the sum of the measured times.
 */
fun CachedActivityRoute?.timingOverride(day: ActivityTimelineOverride?): ActivityTimelineOverride? {
    val route = this ?: return day
    val margin = route.trackedMarginMinutes ?: return day
    val base = day ?: ActivityTimelineOverride(activityId = route.activityId)
    return base.copy(
        travelMinutes = base.travelMinutes ?: route.trackedDriveMinutes,
        internalTransferInMinutes = base.internalTransferInMinutes ?: route.trackedWalkMinutes,
        setupMinutes = base.setupMinutes ?: route.trackedSetupMinutes,
        travelBufferMinutes = base.travelBufferMinutes ?: margin,
        preStartMarginMinutes = base.preStartMarginMinutes ?: 0,
    )
}

/** The last moment to leave when everything goes well: the departure without the margin. */
val EventTimeline.latestLeaveAt: LocalDateTime
    get() = leaveAt.plusMinutes(config.travelBufferMinutes.toLong())
