package com.example.alarms

import com.example.domain.rules.withTracked
import com.example.domain.rules.timingOverride
import com.example.domain.rules.effectiveDriveMinutes
import com.example.domain.rules.TrackedRoutes
import com.example.domain.rules.TrackedRoute
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.MainActivity
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.SessionDataStore
import com.example.data.port.DepartureAlarmPort
import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.domain.model.Activity
import com.example.domain.model.ActivityTimelineOverride
import com.example.data.EventRouteResolver
import com.example.domain.model.CachedActivityRoute
import com.example.domain.rules.EventChainPlanner
import com.example.domain.rules.RouteOriginKind
import com.example.domain.model.LabSettings
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.ContingencyEvaluator
import com.example.domain.rules.DepartureTimeCalculator
import com.example.domain.rules.DominicanFormatter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DepartureAlarmScheduler(
    private val context: Context,
    private val sessionDataStore: SessionDataStore,
    private val mapsAndLocationPort: MapsAndLocationPort,
) : DepartureAlarmPort {

    private val routeResolver = EventRouteResolver(mapsAndLocationPort)

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun observeScheduledDepartures(): Flow<Map<String, ScheduledDeparture>> =
        sessionDataStore.observeScheduledDepartures()

    override fun observeActivityRoutes(): Flow<Map<String, CachedActivityRoute>> =
        sessionDataStore.observeActivityRoutes()

    override fun observeActivityTimings(): Flow<Map<String, ActivityTimelineOverride>> =
        sessionDataStore.observeActivityTimings()

    override fun observeTrackedRoutes(): Flow<Map<String, TrackedRoute>> =
        sessionDataStore.observeTrackedRoutes()

    override suspend fun saveTrackedRoute(venueKey: String, route: TrackedRoute?) {
        sessionDataStore.saveTrackedRoute(venueKey, route)
    }

    override suspend fun clearTrackedRoutes() {
        sessionDataStore.clearTrackedRoutes()
    }

    override fun observeContingencyUnderControlIds(): Flow<Set<String>> =
        sessionDataStore.observeContingencyUnderControlIds()

    override suspend fun setContingencyUnderControl(activityId: String, underControl: Boolean) {
        sessionDataStore.setContingencyUnderControl(activityId, underControl)
        if (underControl) {
            cancelContingencyAlarmsOnly(activityId)
            NotificationHelper.cancelContingencyNotification(context, activityId)
        } else {
            val entry = sessionDataStore.loadScheduledDepartures()[activityId] ?: return
            val depPrefs = sessionDataStore.loadDeparturePrefs()
            if (!depPrefs.contingencyEnabled) return
            val nowMillis = ZonedDateTime.now(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
            val eventStartMillis = ZonedDateTime.of(entry.eventDate, entry.eventTime, SANTO_DOMINGO_ZONE)
                .toInstant()
                .toEpochMilli()
            val hoursUntil = (eventStartMillis - nowMillis) / (1000.0 * 3600.0)
            if (eventStartMillis > nowMillis && hoursUntil <= 24.0) {
                scheduleContingencyAlarmsFor(entry, nowMillis)
            }
        }
    }

    override suspend fun saveCachedRouteForActivity(activityId: String, route: CachedActivityRoute) {
        sessionDataStore.saveSingleActivityRoute(activityId, route)
    }

    override suspend fun saveActivityTimingOverride(activityId: String, override: ActivityTimelineOverride?) {
        sessionDataStore.saveActivityTimingOverride(activityId, override)
    }

    override fun canScheduleExactAlarms(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    override suspend fun syncUpcomingActivities(
        activities: List<Activity>,
        settings: LabSettings,
        overdueSummaryText: String?,
        forceRecalculateRoutes: Boolean,
    ) {
        NotificationHelper.ensureChannels(context)
        ensurePeriodicSyncScheduled()
        sessionDataStore.saveDailyOverdueSummary(overdueSummaryText)

        val nowZoned = ZonedDateTime.now(SANTO_DOMINGO_ZONE)
        val nowMillis = nowZoned.toInstant().toEpochMilli()
        val today = nowZoned.toLocalDate()
        val horizonDate = today.plusDays(14)

        val previousMap = sessionDataStore.loadScheduledDepartures()
        val cachedRoutes = sessionDataStore.loadActivityRoutes().toMutableMap()
        val timingOverrides = sessionDataStore.loadActivityTimings()
        val trackedRoutes = sessionDataStore.loadTrackedRoutes()
        val nextMap = linkedMapOf<String, ScheduledDeparture>()

        // Include ALL upcoming activities with an eventTime (even manual-address gigs without map pins)
        val candidates = activities.filter { act ->
            act.eventTime != null &&
                !act.eventDate.isBefore(today) &&
                !act.eventDate.isAfter(horizonDate)
        }

        // Cancel alarms for activities that were deleted, moved into the past, or lost eventTime
        val candidateIds = candidates.map { it.id }.toSet()
        for (oldId in previousMap.keys) {
            if (oldId !in candidateIds) {
                cancelPlatformAlarms(oldId)
            }
        }
        sessionDataStore.pruneContingencyUnderControlIds(candidateIds)
        val underControlIds = sessionDataStore.loadContingencyUnderControlIds()

        var routesChanged = false

        // Prune cached routes belonging to activities that were deleted
        val liveActivityIds = activities.map { it.id }.toSet()
        val staleRouteIds = cachedRoutes.keys.filter { it !in liveActivityIds }
        if (staleRouteIds.isNotEmpty()) {
            staleRouteIds.forEach { cachedRoutes.remove(it) }
            routesChanged = true
        }

        for (act in candidates) {
            val eventTime = act.eventTime ?: continue
            val destLat = act.latitude
            val destLng = act.longitude

            val eventStartZoned = ZonedDateTime.of(act.eventDate, eventTime, SANTO_DOMINGO_ZONE)
            val eventStartMillis = eventStartZoned.toInstant().toEpochMilli()
            if (eventStartMillis <= nowMillis) {
                cancelPlatformAlarms(act.id)
                continue
            }

            val hoursUntilEvent = (eventStartMillis - nowMillis) / (1000.0 * 3600.0)
            val resolvedRoute = routeResolver.resolve(
                target = act,
                activities = activities,
                settings = settings,
                overrides = timingOverrides,
                cached = cachedRoutes[act.id],
                forceRefresh = forceRecalculateRoutes,
                trackedRoutes = trackedRoutes,
            )
            val override = resolvedRoute.timingOverride(timingOverrides[act.id])
            // Offline estimates are saved too, so screens never show an outdated origin.
            // They are not reused: the next sync asks for an online one again.
            if (resolvedRoute != null && cachedRoutes[act.id] != resolvedRoute) {
                cachedRoutes[act.id] = resolvedRoute
                routesChanged = true
            }

            val routeTravelMinutes: Int? = resolvedRoute?.effectiveDriveMinutes
            val isOsrmEstimated = resolvedRoute?.fromOsrm == true
            val distanceKm = resolvedRoute?.distanceKm
            val isChained = resolvedRoute?.originKind == RouteOriginKind.PREVIOUS_EVENT

            val schedule = DepartureTimeCalculator.calculateFromTimeline(
                eventDate = act.eventDate,
                eventTime = eventTime,
                travelMinutes = routeTravelMinutes,
                settings = settings,
                override = override,
            )

            val leaveAtMillis = schedule.leaveAt.atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
            // Leaving from the previous event leaves nothing to prepare at home.
            val prepareAtMillis = if (isChained) {
                leaveAtMillis
            } else {
                schedule.prepareAt.atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
            }
            val (contingencyAtMillis, contingencyFinalAtMillis) = computeContingencyTimestamps(
                eventStartMillis = eventStartMillis,
                contingencyEnabled = settings.contingencyEnabled,
                contingencyLeadMinutes = settings.contingencyLeadMinutes,
            )

            val previous = previousMap[act.id]
            val timeChangedSignificantly = previous == null ||
                previous.eventDate != act.eventDate ||
                previous.eventTime != eventTime ||
                previous.venueName != act.venueName ||
                previous.originKind != resolvedRoute?.originKind ||
                previous.originVenueName != resolvedRoute?.originVenueName ||
                DepartureTimeCalculator.shouldReschedule(previous.leaveAtEpochMillis, leaveAtMillis) ||
                DepartureTimeCalculator.shouldReschedule(previous.prepareAtEpochMillis, prepareAtMillis) ||
                previous.contingencyAtEpochMillis != contingencyAtMillis ||
                previous.contingencyFinalAtEpochMillis != contingencyFinalAtMillis

            val within24h = hoursUntilEvent <= 24.0
            val needsInitialArming = within24h && (previous == null || !previous.alarmsScheduled)
            val isUnderControl = act.id in underControlIds

            var armedNow = previous?.alarmsScheduled == true && within24h
            val baseEntry = ScheduledDeparture(
                activityId = act.id,
                venueName = act.venueName,
                supplierName = act.supplierName,
                description = act.description,
                eventDate = act.eventDate,
                eventTime = eventTime,
                latitude = destLat,
                longitude = destLng,
                travelMinutes = schedule.travelMinutes,
                distanceKm = distanceKm,
                prepareAtEpochMillis = prepareAtMillis,
                leaveAtEpochMillis = leaveAtMillis,
                estimatedArrivalTime = schedule.estimatedArrivalAt.toLocalTime(),
                isOsrmEstimated = isOsrmEstimated,
                alarmsScheduled = armedNow,
                contingencyAtEpochMillis = contingencyAtMillis,
                contingencyFinalAtEpochMillis = contingencyFinalAtMillis,
                originKind = resolvedRoute?.originKind,
                originVenueName = resolvedRoute?.originVenueName,
                originLatitude = resolvedRoute?.originLat,
                originLongitude = resolvedRoute?.originLng,
                shortfallMinutes = resolvedRoute?.shortfallMinutes ?: 0,
                unpinnedPreviousVenueName = resolvedRoute?.unpinnedPreviousVenueName,
                isTrackedDrive = resolvedRoute?.trackedDriveMinutes != null,
                latestLeaveAtEpochMillis = latestLeaveMillis(resolvedRoute, leaveAtMillis, override, settings),
            )

            // Schedule platform alarms for gigs within T-24h (and re-schedule on >=5 min shifts)
            if (within24h) {
                if (leaveAtMillis <= nowMillis && eventStartMillis > nowMillis) {
                    val arrivalNow = DepartureTimeCalculator.estimatedArrivalIfLeavingNow(
                        now = nowZoned.toLocalDateTime(),
                        travelMinutes = schedule.travelMinutes,
                    )
                    if (previous == null || previous.leaveAtEpochMillis > nowMillis) {
                        NotificationHelper.postLateNotification(
                            context = context,
                            activityId = act.id,
                            venueName = act.venueName,
                            latitude = destLat,
                            longitude = destLng,
                            arrivalTimeLabel = DominicanFormatter.formatTime24(arrivalNow.toLocalTime()),
                        )
                    }
                    cancelDepartureAlarmsOnly(act.id)
                    if (!isUnderControl && settings.contingencyEnabled) {
                        scheduleContingencyAlarmsFor(baseEntry, nowMillis)
                    } else {
                        cancelContingencyAlarmsOnly(act.id)
                    }
                    armedNow = false
                } else if (needsInitialArming || timeChangedSignificantly) {
                    schedulePlatformAlarmsFor(baseEntry, nowMillis, isUnderControl)
                    armedNow = true
                } else if (isUnderControl || !settings.contingencyEnabled) {
                    cancelContingencyAlarmsOnly(act.id)
                }
            } else {
                // Beyond 24h: arm a single wake-up at T-24h to arm exact alarms once inside 24h
                armedNow = false
                scheduleProvisionalWakeup(act.id, eventStartMillis, nowMillis)
            }

            nextMap[act.id] = baseEntry.copy(alarmsScheduled = armedNow)
        }

        if (routesChanged) {
            sessionDataStore.saveActivityRoutes(cachedRoutes)
        }
        sessionDataStore.saveScheduledDepartures(nextMap)
        scheduleNextDailySummary(nowZoned)

        // Runs last: alarm receivers check the saved departures, so slow requests for events
        // beyond the alarm horizon must not delay that save.
        var farRoutesChanged = false
        for (act in activities) {
            if (!act.eventDate.isAfter(horizonDate)) continue
            if (!EventRouteResolver.needsOriginRefresh(act, activities, cachedRoutes[act.id], trackedRoutes)) {
                continue
            }
            val route = routeResolver.resolve(
                target = act,
                activities = activities,
                settings = settings,
                overrides = timingOverrides,
                cached = cachedRoutes[act.id],
                forceRefresh = false,
                trackedRoutes = trackedRoutes,
            ) ?: continue
            if (cachedRoutes[act.id] != route) {
                cachedRoutes[act.id] = route
                farRoutesChanged = true
            }
        }
        if (farRoutesChanged) {
            sessionDataStore.saveActivityRoutes(cachedRoutes)
        }
    }

    override suspend fun cancelForActivity(activityId: String) {
        cancelPlatformAlarms(activityId)
        val current = sessionDataStore.loadScheduledDepartures().toMutableMap()
        current.remove(activityId)
        sessionDataStore.saveScheduledDepartures(current)
        sessionDataStore.removeActivityRoute(activityId)
        sessionDataStore.saveActivityTimingOverride(activityId, null)
        sessionDataStore.setContingencyUnderControl(activityId, false)
    }

    override suspend fun cancelAllScheduledAlarms() {
        val stored = sessionDataStore.loadScheduledDepartures()
        stored.keys.forEach { cancelPlatformAlarms(it) }
        val dailyIntent = Intent(context, DailySummaryAlarmReceiver::class.java)
        val dailyPi = PendingIntent.getBroadcast(
            context,
            DAILY_SUMMARY_REQUEST_CODE,
            dailyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { alarmManager?.cancel(dailyPi) }
        runCatching { dailyPi.cancel() }
        runCatching { workManager.cancelUniqueWork(DAILY_SUMMARY_WORK_NAME) }
        runCatching { workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME) }
        runCatching { workManager.cancelAllWorkByTag(TAG_DEPARTURE_WORK) }
        runCatching { workManager.cancelAllWorkByTag(TAG_CONTINGENCY) }
        sessionDataStore.saveScheduledDepartures(emptyMap())
    }

    override suspend fun rescheduleAllFromStorage() {
        NotificationHelper.ensureChannels(context)
        ensurePeriodicSyncScheduled()
        val nowZoned = ZonedDateTime.now(SANTO_DOMINGO_ZONE)
        val nowMillis = nowZoned.toInstant().toEpochMilli()
        val stored = sessionDataStore.loadScheduledDepartures()
        val underControlIds = sessionDataStore.loadContingencyUnderControlIds()
        val depPrefs = sessionDataStore.loadDeparturePrefs()
        val stillValid = linkedMapOf<String, ScheduledDeparture>()

        for ((id, item) in stored) {
            val eventStartMillis = ZonedDateTime.of(item.eventDate, item.eventTime, SANTO_DOMINGO_ZONE)
                .toInstant()
                .toEpochMilli()
            if (eventStartMillis <= nowMillis) {
                cancelPlatformAlarms(id)
                continue
            }
            val (contAt, contFinalAt) = computeContingencyTimestamps(
                eventStartMillis = eventStartMillis,
                contingencyEnabled = depPrefs.contingencyEnabled,
                contingencyLeadMinutes = depPrefs.contingencyLeadMinutes,
            )
            val refreshed = item.copy(
                contingencyAtEpochMillis = contAt,
                contingencyFinalAtEpochMillis = contFinalAt,
            )
            val isUnderControl = id in underControlIds
            val hoursUntil = (eventStartMillis - nowMillis) / (1000.0 * 3600.0)
            if (hoursUntil <= 24.0) {
                if (refreshed.leaveAtEpochMillis <= nowMillis) {
                    cancelDepartureAlarmsOnly(id)
                    if (!isUnderControl && depPrefs.contingencyEnabled) {
                        scheduleContingencyAlarmsFor(refreshed, nowMillis)
                    } else {
                        cancelContingencyAlarmsOnly(id)
                    }
                    stillValid[id] = refreshed.copy(alarmsScheduled = false)
                } else {
                    schedulePlatformAlarmsFor(refreshed, nowMillis, isUnderControl)
                    stillValid[id] = refreshed.copy(alarmsScheduled = true)
                }
            } else {
                scheduleProvisionalWakeup(id, eventStartMillis, nowMillis)
                stillValid[id] = refreshed.copy(alarmsScheduled = false)
            }
        }

        sessionDataStore.saveScheduledDepartures(stillValid)
        scheduleNextDailySummary(nowZoned)
    }

    suspend fun recomputeSingleActivityRoute(activityId: String) {
        val stored = sessionDataStore.loadScheduledDepartures().toMutableMap()
        val entry = stored[activityId] ?: return
        val depPrefs = sessionDataStore.loadDeparturePrefs()
        val settings = LabSettings(
            setupMinutes = depPrefs.setupMinutes,
            prepMinutes = depPrefs.prepMinutes,
            travelBufferMinutes = depPrefs.travelBufferMinutes,
            internalTransferInMinutes = depPrefs.internalTransferInMinutes,
            preStartMarginMinutes = depPrefs.preStartMarginMinutes,
            internalTransferOutMinutes = depPrefs.internalTransferOutMinutes,
            homeLatitude = depPrefs.homeLatitude,
            homeLongitude = depPrefs.homeLongitude,
            scoreReminderDays = depPrefs.scoreReminderDays,
            contingencyEnabled = depPrefs.contingencyEnabled,
            contingencyLeadMinutes = depPrefs.contingencyLeadMinutes,
        )
        val dayOverride = sessionDataStore.loadActivityTimings()[activityId]
        val isUnderControl = activityId in sessionDataStore.loadContingencyUnderControlIds()

        val nowZoned = ZonedDateTime.now(SANTO_DOMINGO_ZONE)
        val nowMillis = nowZoned.toInstant().toEpochMilli()
        val eventStartMillis = ZonedDateTime.of(entry.eventDate, entry.eventTime, SANTO_DOMINGO_ZONE)
            .toInstant()
            .toEpochMilli()
        if (eventStartMillis <= nowMillis) {
            cancelForActivity(activityId)
            return
        }

        // The origin was chosen by the last full sync; this refresh only measures it again.
        val storedOriginLat = entry.originLatitude
        val storedOriginLng = entry.originLongitude
        val origin = if (entry.isChained && storedOriginLat != null && storedOriginLng != null) {
            GeoPoint(storedOriginLat, storedOriginLng)
        } else {
            resolveOrigin(settings)
        }
        val destLat = entry.latitude
        val destLng = entry.longitude

        val resolvedRoute = if (destLat != null && destLng != null) {
            val cachedRoutes = sessionDataStore.loadActivityRoutes().toMutableMap()
            val measured = routeResolver
                .measure(activityId, origin, GeoPoint(destLat, destLng), cachedRoutes[activityId])
                .copy(
                    originKind = entry.originKind,
                    originVenueName = entry.originVenueName,
                    shortfallMinutes = entry.shortfallMinutes,
                    unpinnedPreviousVenueName = entry.unpinnedPreviousVenueName,
                )
            if (cachedRoutes[activityId] != measured) {
                cachedRoutes[activityId] = measured
                sessionDataStore.saveActivityRoutes(cachedRoutes)
            }
            measured
        } else {
            null
        }

        val savedRoute = resolvedRoute?.withTracked(
            TrackedRoutes.find(sessionDataStore.loadTrackedRoutes(), entry.venueName)
        )
        val override = savedRoute.timingOverride(dayOverride)
        val routeTravelMinutes: Int? = savedRoute?.effectiveDriveMinutes
        val isOsrm = resolvedRoute?.fromOsrm == true
        val distanceKm = resolvedRoute?.distanceKm ?: entry.distanceKm

        val schedule = DepartureTimeCalculator.calculateFromTimeline(
            eventDate = entry.eventDate,
            eventTime = entry.eventTime,
            travelMinutes = routeTravelMinutes,
            settings = settings,
            override = override,
        )
        val newLeaveMillis = schedule.leaveAt.atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
        val newPrepareMillis = if (entry.isChained) {
            newLeaveMillis
        } else {
            schedule.prepareAt.atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
        }
        val (contingencyAtMillis, contingencyFinalAtMillis) = computeContingencyTimestamps(
            eventStartMillis = eventStartMillis,
            contingencyEnabled = settings.contingencyEnabled,
            contingencyLeadMinutes = settings.contingencyLeadMinutes,
        )

        var updated = entry.copy(
            travelMinutes = schedule.travelMinutes,
            distanceKm = distanceKm,
            prepareAtEpochMillis = newPrepareMillis,
            leaveAtEpochMillis = newLeaveMillis,
            estimatedArrivalTime = schedule.estimatedArrivalAt.toLocalTime(),
            isOsrmEstimated = isOsrm,
            isTrackedDrive = savedRoute?.trackedDriveMinutes != null,
            latestLeaveAtEpochMillis = latestLeaveMillis(savedRoute, newLeaveMillis, override, settings),
            contingencyAtEpochMillis = contingencyAtMillis,
            contingencyFinalAtEpochMillis = contingencyFinalAtMillis,
        )

        if (newLeaveMillis <= nowMillis) {
            val arrivalNow = DepartureTimeCalculator.estimatedArrivalIfLeavingNow(
                now = nowZoned.toLocalDateTime(),
                travelMinutes = schedule.travelMinutes,
            )
            NotificationHelper.postLateNotification(
                context = context,
                activityId = entry.activityId,
                venueName = entry.venueName,
                latitude = entry.latitude,
                longitude = entry.longitude,
                arrivalTimeLabel = DominicanFormatter.formatTime24(arrivalNow.toLocalTime()),
            )
            cancelDepartureAlarmsOnly(activityId)
            if (!isUnderControl && settings.contingencyEnabled) {
                scheduleContingencyAlarmsFor(updated, nowMillis)
            } else {
                cancelContingencyAlarmsOnly(activityId)
            }
            updated = updated.copy(alarmsScheduled = false)
        } else {
            val shouldArm = !entry.alarmsScheduled ||
                DepartureTimeCalculator.shouldReschedule(entry.leaveAtEpochMillis, newLeaveMillis) ||
                DepartureTimeCalculator.shouldReschedule(entry.prepareAtEpochMillis, newPrepareMillis) ||
                entry.contingencyAtEpochMillis != contingencyAtMillis
            if (shouldArm) {
                schedulePlatformAlarmsFor(updated, nowMillis, isUnderControl)
                updated = updated.copy(alarmsScheduled = true)
            }
        }

        stored[activityId] = updated
        sessionDataStore.saveScheduledDepartures(stored)
    }

    fun ensurePeriodicSyncScheduled() {
        val periodicRequest = PeriodicWorkRequestBuilder<DepartureSyncPeriodicWorker>(
            6,
            TimeUnit.HOURS,
        )
            .addTag(TAG_DEPARTURE_WORK)
            .build()
        runCatching {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest,
            )
        }
    }

    fun scheduleNextDailySummary(nowZoned: ZonedDateTime = ZonedDateTime.now(SANTO_DOMINGO_ZONE)) {
        val todayAt8 = nowZoned.toLocalDate().atTime(LocalTime.of(8, 0)).atZone(SANTO_DOMINGO_ZONE)
        val nextRun = if (nowZoned.isBefore(todayAt8)) todayAt8 else todayAt8.plusDays(1)
        val triggerMillis = nextRun.toInstant().toEpochMilli()
        val delayMillis = (triggerMillis - nowZoned.toInstant().toEpochMilli()).coerceAtLeast(60_000L)

        if (canScheduleExactAlarms()) {
            val intent = Intent(context, DailySummaryAlarmReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                DAILY_SUMMARY_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } else {
            val req = OneTimeWorkRequestBuilder<DailySummaryWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            runCatching {
                workManager.enqueueUniqueWork(DAILY_SUMMARY_WORK_NAME, ExistingWorkPolicy.REPLACE, req)
            }
        }
    }

    /** Only a tracked route has a meaningful latest departure: the one without the margin. */
    private fun latestLeaveMillis(
        route: CachedActivityRoute?,
        leaveAtMillis: Long,
        override: ActivityTimelineOverride?,
        settings: LabSettings,
    ): Long? {
        if (route?.trackedMarginMinutes == null) return null
        val margin = override?.travelBufferMinutes ?: settings.travelBufferMinutes
        return leaveAtMillis + TimeUnit.MINUTES.toMillis(margin.coerceIn(0, 180).toLong())
    }

    private fun computeContingencyTimestamps(
        eventStartMillis: Long,
        contingencyEnabled: Boolean,
        contingencyLeadMinutes: Int,
    ): Pair<Long?, Long?> {
        if (!contingencyEnabled) return null to null
        val safeLead = contingencyLeadMinutes.coerceIn(5, 120)
        val primaryAt = eventStartMillis - TimeUnit.MINUTES.toMillis(safeLead.toLong())
        val finalAt = if (safeLead > ContingencyEvaluator.FINAL_ESCALATION_LEAD_MINUTES + 4) {
            eventStartMillis - TimeUnit.MINUTES.toMillis(ContingencyEvaluator.FINAL_ESCALATION_LEAD_MINUTES.toLong())
        } else {
            null
        }
        return primaryAt to finalAt
    }

    private suspend fun resolveOrigin(settings: LabSettings): GeoPoint {
        val current = runCatching { mapsAndLocationPort.getLastKnownOrCurrentLocation() }.getOrNull()
        val origin = EventChainPlanner.fallbackOrigin(
            homeLatitude = settings.homeLatitude,
            homeLongitude = settings.homeLongitude,
            currentLatitude = current?.latitude,
            currentLongitude = current?.longitude,
        )
        return GeoPoint(origin.latitude, origin.longitude)
    }

    private fun schedulePlatformAlarmsFor(
        entry: ScheduledDeparture,
        nowMillis: Long,
        isUnderControl: Boolean,
    ) {
        val prepareDelay = entry.prepareAtEpochMillis - nowMillis
        val leaveDelay = entry.leaveAtEpochMillis - nowMillis

        val eventTimeLabel = DominicanFormatter.formatTime12h(entry.eventTime)
        val leaveTimeLabel = DominicanFormatter.formatTime12h(
            Instant.ofEpochMilli(entry.leaveAtEpochMillis).atZone(SANTO_DOMINGO_ZONE).toLocalTime()
        )
        val arrivalLabel = DominicanFormatter.formatTime24(entry.estimatedArrivalTime)

        if (entry.isChained) {
            // Drop any home preparation alarm armed before this event became chained.
            cancelDepartureAlarmsOnly(entry.activityId)
        }
        if (prepareDelay > 0L && !entry.isChained) {
            scheduleSingleAlarm(
                activityId = entry.activityId,
                alarmType = ALARM_TYPE_PREPARE,
                triggerMillis = entry.prepareAtEpochMillis,
                delayMillis = prepareDelay,
                venueName = entry.venueName,
                latitude = entry.latitude,
                longitude = entry.longitude,
                travelMinutes = entry.travelMinutes,
                eventTimeLabel = eventTimeLabel,
                leaveTimeLabel = leaveTimeLabel,
                arrivalLabel = arrivalLabel,
            )
        }
        if (leaveDelay > 0L) {
            scheduleSingleAlarm(
                activityId = entry.activityId,
                alarmType = ALARM_TYPE_LEAVE,
                triggerMillis = entry.leaveAtEpochMillis,
                delayMillis = leaveDelay,
                venueName = entry.venueName,
                latitude = entry.latitude,
                longitude = entry.longitude,
                travelMinutes = entry.travelMinutes,
                eventTimeLabel = eventTimeLabel,
                leaveTimeLabel = leaveTimeLabel,
                arrivalLabel = arrivalLabel,
            )
        }
        if (!isUnderControl) {
            scheduleContingencyAlarmsFor(entry, nowMillis)
        } else {
            cancelContingencyAlarmsOnly(entry.activityId)
        }
    }

    private fun scheduleContingencyAlarmsFor(entry: ScheduledDeparture, nowMillis: Long) {
        val eventTimeLabel = DominicanFormatter.formatTime12h(entry.eventTime)
        val leaveTimeLabel = DominicanFormatter.formatTime12h(
            Instant.ofEpochMilli(entry.leaveAtEpochMillis).atZone(SANTO_DOMINGO_ZONE).toLocalTime()
        )
        val arrivalLabel = DominicanFormatter.formatTime24(entry.estimatedArrivalTime)

        entry.contingencyAtEpochMillis?.let { primaryAt ->
            val delay = primaryAt - nowMillis
            if (delay > 0L) {
                scheduleSingleAlarm(
                    activityId = entry.activityId,
                    alarmType = ALARM_TYPE_CONTINGENCY,
                    triggerMillis = primaryAt,
                    delayMillis = delay,
                    venueName = entry.venueName,
                    latitude = entry.latitude,
                    longitude = entry.longitude,
                    travelMinutes = entry.travelMinutes,
                    eventTimeLabel = eventTimeLabel,
                    leaveTimeLabel = leaveTimeLabel,
                    arrivalLabel = arrivalLabel,
                )
            }
        }
        entry.contingencyFinalAtEpochMillis?.let { finalAt ->
            val delay = finalAt - nowMillis
            if (delay > 0L) {
                scheduleSingleAlarm(
                    activityId = entry.activityId,
                    alarmType = ALARM_TYPE_CONTINGENCY_FINAL,
                    triggerMillis = finalAt,
                    delayMillis = delay,
                    venueName = entry.venueName,
                    latitude = entry.latitude,
                    longitude = entry.longitude,
                    travelMinutes = entry.travelMinutes,
                    eventTimeLabel = eventTimeLabel,
                    leaveTimeLabel = leaveTimeLabel,
                    arrivalLabel = arrivalLabel,
                )
            }
        }
    }

    private fun scheduleSingleAlarm(
        activityId: String,
        alarmType: String,
        triggerMillis: Long,
        delayMillis: Long,
        venueName: String,
        latitude: Double?,
        longitude: Double?,
        travelMinutes: Int,
        eventTimeLabel: String,
        leaveTimeLabel: String,
        arrivalLabel: String,
    ) {
        val exactGranted = canScheduleExactAlarms()

        // 1. PRIMARY: Exact AlarmManager alarm so critical notices fire on the exact minute even in Doze mode.
        if (exactGranted) {
            val pi = buildAlarmPendingIntent(
                activityId = activityId,
                alarmType = alarmType,
                venueName = venueName,
                latitude = latitude,
                longitude = longitude,
                travelMinutes = travelMinutes,
                eventTimeLabel = eventTimeLabel,
                leaveTimeLabel = leaveTimeLabel,
                arrivalLabel = arrivalLabel,
                triggerMillis = triggerMillis,
            )
            runCatching {
                val isCriticalWake = alarmType == ALARM_TYPE_LEAVE ||
                    alarmType == ALARM_TYPE_CONTINGENCY ||
                    alarmType == ALARM_TYPE_CONTINGENCY_FINAL
                if (isCriticalWake) {
                    val showIntent = PendingIntent.getActivity(
                        context,
                        requestCodeFor(activityId, "SHOW"),
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    alarmManager?.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerMillis, showIntent),
                        pi,
                    )
                } else {
                    alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                }
            }
        }

        // 2. FALLBACK: Persistent WorkManager job so the notification still fires if exact alarms are denied
        // or killed by aggressive OEM battery optimizers. Offset by +2 min when exact alarms are active so
        // AlarmManager always delivers first, while SessionDataStore deduplication prevents any duplicate.
        val input = Data.Builder()
            .putString(EXTRA_ACTIVITY_ID, activityId)
            .putString(EXTRA_ALARM_TYPE, alarmType)
            .putString(EXTRA_VENUE_NAME, venueName)
            .putDouble(EXTRA_LAT, latitude ?: Double.NaN)
            .putDouble(EXTRA_LNG, longitude ?: Double.NaN)
            .putInt(EXTRA_TRAVEL_MIN, travelMinutes)
            .putString(EXTRA_EVENT_TIME, eventTimeLabel)
            .putString(EXTRA_LEAVE_TIME, leaveTimeLabel)
            .putString(EXTRA_ARRIVAL_TIME, arrivalLabel)
            .putLong(EXTRA_TRIGGER_MILLIS, triggerMillis)
            .build()

        val fallbackDelayMillis = if (exactGranted) {
            delayMillis.coerceAtLeast(1_000L) + WORKMANAGER_FALLBACK_OFFSET_MS
        } else {
            delayMillis.coerceAtLeast(1_000L)
        }
        val typeTag = when (alarmType) {
            ALARM_TYPE_LEAVE -> TAG_DEPARTURE_LEAVE
            ALARM_TYPE_CONTINGENCY, ALARM_TYPE_CONTINGENCY_FINAL -> TAG_CONTINGENCY
            else -> TAG_DEPARTURE_PREPARE
        }
        val request = OneTimeWorkRequestBuilder<DepartureNotificationWorker>()
            .setInitialDelay(fallbackDelayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(TAG_DEPARTURE_WORK)
            .addTag(typeTag)
            .build()
        runCatching {
            workManager.enqueueUniqueWork(
                departureWorkName(activityId, alarmType),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    private fun scheduleProvisionalWakeup(activityId: String, eventStartMillis: Long, nowMillis: Long) {
        val tMinus24hMillis = eventStartMillis - TimeUnit.HOURS.toMillis(24)
        val delayMinutes = TimeUnit.MILLISECONDS.toMinutes((tMinus24hMillis - nowMillis).coerceAtLeast(60_000L))
        enqueueRouteWorker(activityId, delayMinutes)
    }

    private fun enqueueRouteWorker(activityId: String, delayMinutes: Long) {
        val input = Data.Builder()
            .putString(EXTRA_ACTIVITY_ID, activityId)
            .build()
        // No NetworkType.CONNECTED constraint so T-24h arming ALWAYS runs even offline
        // (reuses cached OSRM route or offline Haversine estimate gracefully).
        val req = OneTimeWorkRequestBuilder<OsrmDepartureRefreshWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(input)
            .addTag(TAG_DEPARTURE_WORK)
            .addTag(TAG_OSRM_REFRESH)
            .build()
        runCatching {
            workManager.enqueueUniqueWork(
                routeWorkName(activityId),
                ExistingWorkPolicy.REPLACE,
                req,
            )
        }
    }

    private fun cancelDepartureAlarmsOnly(activityId: String) {
        for (type in listOf(ALARM_TYPE_PREPARE, ALARM_TYPE_LEAVE)) {
            val pi = buildAlarmPendingIntent(activityId, type)
            runCatching { alarmManager?.cancel(pi) }
            runCatching { pi.cancel() }
            runCatching { workManager.cancelUniqueWork(departureWorkName(activityId, type)) }
        }
    }

    private fun cancelContingencyAlarmsOnly(activityId: String) {
        for (type in listOf(ALARM_TYPE_CONTINGENCY, ALARM_TYPE_CONTINGENCY_FINAL)) {
            val pi = buildAlarmPendingIntent(activityId, type)
            runCatching { alarmManager?.cancel(pi) }
            runCatching { pi.cancel() }
            runCatching { workManager.cancelUniqueWork(departureWorkName(activityId, type)) }
        }
    }

    private fun cancelPlatformAlarms(activityId: String) {
        cancelDepartureAlarmsOnly(activityId)
        cancelContingencyAlarmsOnly(activityId)
        NotificationHelper.cancelContingencyNotification(context, activityId)
        runCatching { workManager.cancelUniqueWork(routeWorkName(activityId)) }
    }

    private fun buildAlarmPendingIntent(
        activityId: String,
        alarmType: String,
        venueName: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        travelMinutes: Int = 0,
        eventTimeLabel: String = "",
        leaveTimeLabel: String = "",
        arrivalLabel: String = "",
        triggerMillis: Long = 0L,
    ): PendingIntent {
        val intent = Intent(context, DepartureAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ACTIVITY_ID, activityId)
            putExtra(EXTRA_ALARM_TYPE, alarmType)
            putExtra(EXTRA_VENUE_NAME, venueName)
            putExtra(EXTRA_LAT, latitude ?: Double.NaN)
            putExtra(EXTRA_LNG, longitude ?: Double.NaN)
            putExtra(EXTRA_TRAVEL_MIN, travelMinutes)
            putExtra(EXTRA_EVENT_TIME, eventTimeLabel)
            putExtra(EXTRA_LEAVE_TIME, leaveTimeLabel)
            putExtra(EXTRA_ARRIVAL_TIME, arrivalLabel)
            putExtra(EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        val requestCode = requestCodeFor(activityId, alarmType)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ALARM_TYPE_PREPARE = "PREPARE"
        const val ALARM_TYPE_LEAVE = "LEAVE"
        const val ALARM_TYPE_CONTINGENCY = "CONTINGENCY"
        const val ALARM_TYPE_CONTINGENCY_FINAL = "CONTINGENCY_FINAL"
        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val EXTRA_VENUE_NAME = "extra_venue_name"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_TRAVEL_MIN = "extra_travel_min"
        const val EXTRA_EVENT_TIME = "extra_event_time"
        const val EXTRA_LEAVE_TIME = "extra_leave_time"
        const val EXTRA_ARRIVAL_TIME = "extra_arrival_time"
        const val EXTRA_TRIGGER_MILLIS = "extra_trigger_millis"

        const val TAG_DEPARTURE_WORK = "apptividades_departure_work"
        const val TAG_DEPARTURE_LEAVE = "apptividades_departure_leave"
        const val TAG_DEPARTURE_PREPARE = "apptividades_departure_prepare"
        const val TAG_CONTINGENCY = "apptividades_contingency"
        const val TAG_OSRM_REFRESH = "apptividades_osrm_refresh"
        const val PERIODIC_SYNC_WORK_NAME = "apptividades_periodic_departure_sync"

        private const val DAILY_SUMMARY_REQUEST_CODE = 80800
        private const val DAILY_SUMMARY_WORK_NAME = "apptividades_daily_summary"
        private const val WORKMANAGER_FALLBACK_OFFSET_MS = 120_000L

        fun requestCodeFor(activityId: String, alarmType: String): Int {
            val offset = when (alarmType) {
                ALARM_TYPE_PREPARE -> 1
                ALARM_TYPE_LEAVE -> 2
                ALARM_TYPE_CONTINGENCY -> 3
                ALARM_TYPE_CONTINGENCY_FINAL -> 4
                else -> 5
            }
            return ((activityId.hashCode() * 31) + offset) and 0x7FFFFFFF
        }

        fun departureWorkName(activityId: String, alarmType: String): String =
            "apptividades_departure_${activityId}_$alarmType"

        fun routeWorkName(activityId: String): String =
            "apptividades_osrm_route_$activityId"
    }
}
