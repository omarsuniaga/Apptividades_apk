package com.example.data

import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.domain.model.CachedActivityRoute
import com.example.domain.model.LabSettings
import com.example.domain.rules.ChainDecision
import com.example.domain.rules.DepartureTimeCalculator
import com.example.domain.rules.EventChainPlanner
import com.example.domain.rules.RouteOriginKind

/**
 * Single place that decides where the trip to an activity starts and measures it.
 * Used by both the departure alarms and the activity screens so they never disagree.
 */
class EventRouteResolver(private val maps: MapsAndLocationPort) {

    /** @return null when the destination has no map pin. */
    suspend fun resolve(
        target: Activity,
        activities: List<Activity>,
        settings: LabSettings,
        overrides: Map<String, ActivityTimelineOverride>,
        cached: CachedActivityRoute?,
        forceRefresh: Boolean,
    ): CachedActivityRoute? {
        val destLat = target.latitude ?: return null
        val destLng = target.longitude ?: return null
        val destination = GeoPoint(destLat, destLng)

        var unpinnedPreviousVenueName: String? = null
        val previous = EventChainPlanner.previousSameDay(target, activities)
        if (previous != null) {
            val previousLat = previous.latitude
            val previousLng = previous.longitude
            val fromPrevious = if (previousLat != null && previousLng != null) {
                measure(target.id, GeoPoint(previousLat, previousLng), destination, cached, forceRefresh)
            } else {
                null
            }
            val decision = EventChainPlanner.plan(
                target = target,
                activities = activities,
                settings = settings,
                overrides = overrides,
                travelMinutesFromPrevious = fromPrevious?.let {
                    overrides[target.id]?.travelMinutes ?: it.travelMinutes
                },
            )
            when {
                decision is ChainDecision.Chained && fromPrevious != null ->
                    return fromPrevious.copy(
                        originKind = RouteOriginKind.PREVIOUS_EVENT,
                        originVenueName = previous.venueName,
                        shortfallMinutes = decision.shortfallMinutes,
                        unpinnedPreviousVenueName = null,
                    )
                decision is ChainDecision.PreviousWithoutPin ->
                    unpinnedPreviousVenueName = previous.venueName
            }
        }

        val current = if (settings.homeLatitude == null || settings.homeLongitude == null) {
            runCatching { maps.getLastKnownOrCurrentLocation() }.getOrNull()
        } else {
            null
        }
        val origin = EventChainPlanner.fallbackOrigin(
            homeLatitude = settings.homeLatitude,
            homeLongitude = settings.homeLongitude,
            currentLatitude = current?.latitude,
            currentLongitude = current?.longitude,
        )
        return measure(target.id, GeoPoint(origin.latitude, origin.longitude), destination, cached, forceRefresh)
            .copy(
                originKind = origin.kind,
                originVenueName = null,
                shortfallMinutes = 0,
                unpinnedPreviousVenueName = unpinnedPreviousVenueName,
            )
    }

    /** OSRM estimate with a cache hit when possible and an offline estimate as last resort. */
    suspend fun measure(
        activityId: String,
        origin: GeoPoint,
        destination: GeoPoint,
        cached: CachedActivityRoute?,
        forceRefresh: Boolean = false,
    ): CachedActivityRoute {
        val reusable = cached?.takeIf {
            it.fromOsrm && it.matchesCoordinates(
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude,
            )
        }
        if (reusable != null && !forceRefresh) return reusable
        val online = maps.computeRouteEstimate(origin, destination).getOrNull()
        // Never trade an online estimate for the straight-line one just because a refresh failed.
        if (online == null && reusable != null) return reusable
        return CachedActivityRoute(
            activityId = activityId,
            originLat = origin.latitude,
            originLng = origin.longitude,
            destLat = destination.latitude,
            destLng = destination.longitude,
            travelMinutes = online?.travelMinutes ?: DepartureTimeCalculator.estimateOfflineTravelMinutes(
                origin.latitude,
                origin.longitude,
                destination.latitude,
                destination.longitude,
            ),
            distanceKm = online?.distanceKm,
            fromOsrm = online != null,
        )
    }

    companion object {
        /**
         * Events beyond the alarm horizon still show a saved route, so it must be recalculated
         * when it chains from another event or when the day gained an earlier one.
         */
        fun needsOriginRefresh(
            target: Activity,
            activities: List<Activity>,
            cached: CachedActivityRoute?,
        ): Boolean = target.hasCoordinates && (
            cached?.originKind == RouteOriginKind.PREVIOUS_EVENT ||
                EventChainPlanner.previousSameDay(target, activities) != null
            )
    }
}
