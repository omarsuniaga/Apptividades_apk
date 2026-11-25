package com.example.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.AppContainer
import com.example.data.local.SessionDataStore
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.ContingencyEvaluator
import com.example.domain.rules.ContingencyState
import com.example.domain.rules.DepartureTimeCalculator
import com.example.domain.rules.DominicanFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime

object DepartureNotificationDispatcher {

    fun dedupKey(activityId: String, alarmType: String, triggerEpochMillis: Long): String =
        "${activityId}_${alarmType}_$triggerEpochMillis"

    suspend fun dispatchIfNew(
        context: Context,
        activityId: String,
        alarmType: String,
        venueName: String,
        latitude: Double?,
        longitude: Double?,
        travelMinutes: Int,
        eventTimeLabel: String,
        leaveTimeLabel: String,
        arrivalTimeLabel: String,
        triggerEpochMillis: Long,
    ): Boolean {
        val appContext = context.applicationContext
        val key = dedupKey(activityId, alarmType, triggerEpochMillis)
        val isNew = SessionDataStore(appContext).markNotificationDeliveredIfNew(key)
        if (!isNew) return false

        when (alarmType) {
            DepartureAlarmScheduler.ALARM_TYPE_PREPARE -> {
                NotificationHelper.postPrepareNotification(
                    context = appContext,
                    activityId = activityId,
                    venueName = venueName,
                    eventTimeLabel = eventTimeLabel,
                    leaveTimeLabel = leaveTimeLabel,
                )
            }

            DepartureAlarmScheduler.ALARM_TYPE_LEAVE -> {
                NotificationHelper.postLeaveNotification(
                    context = appContext,
                    activityId = activityId,
                    venueName = venueName,
                    latitude = latitude,
                    longitude = longitude,
                    travelMinutes = travelMinutes,
                    arrivalTimeLabel = arrivalTimeLabel,
                )
            }

            else -> return false
        }
        return true
    }

    suspend fun dispatchContingencyIfNeeded(
        context: Context,
        stored: ScheduledDeparture,
        alarmType: String,
        triggerEpochMillis: Long,
    ): Boolean {
        val appContext = context.applicationContext
        val dataStore = SessionDataStore(appContext)
        val depPrefs = dataStore.loadDeparturePrefs()
        if (!depPrefs.contingencyEnabled) return false

        val underControlIds = dataStore.loadContingencyUnderControlIds()
        if (stored.activityId in underControlIds) return false

        val container = AppContainer.getInstance(appContext)
        val currentLocation = if (stored.hasCoordinates) {
            runCatching { container.mapsAndLocationPort.getLastKnownOrCurrentLocation() }.getOrNull()
        } else {
            null
        }

        val nowLocal = ZonedDateTime.now(SANTO_DOMINGO_ZONE).toLocalDateTime()
        val eventStartLocal = stored.eventDate.atTime(stored.eventTime)
        val assessment = ContingencyEvaluator.evaluate(
            now = nowLocal,
            eventStart = eventStartLocal,
            contingencyEnabled = depPrefs.contingencyEnabled,
            leadMinutes = depPrefs.contingencyLeadMinutes,
            isMarkedUnderControl = false,
            venueLat = stored.latitude,
            venueLng = stored.longitude,
            currentLat = currentLocation?.latitude,
            currentLng = currentLocation?.longitude,
        )

        if (assessment.state == ContingencyState.ARRIVED_NEARBY) {
            // Artist is already at/near the venue: auto-silence contingency silently.
            container.departureAlarmScheduler.setContingencyUnderControl(stored.activityId, true)
            return false
        }
        if (!assessment.shouldNotify) return false

        val key = dedupKey(stored.activityId, alarmType, triggerEpochMillis)
        val isNew = dataStore.markNotificationDeliveredIfNew(key)
        if (!isNew) return false

        val isFinal = alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY_FINAL ||
            assessment.isFinalEscalation
        NotificationHelper.postContingencyNotification(
            context = appContext,
            activityId = stored.activityId,
            venueName = stored.venueName,
            minutesUntilStart = assessment.minutesUntilStart.toInt().coerceAtLeast(1),
            eventTimeLabel = DominicanFormatter.formatTime12h(stored.eventTime),
            distanceLabel = ContingencyEvaluator.formatDistanceLabel(assessment.distanceMeters),
            isFinalEscalation = isFinal,
        )
        return true
    }
}

class ContingencyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_ACTIVITY_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.getInstance(context.applicationContext)
                container.departureAlarmScheduler.setContingencyUnderControl(activityId, true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class DepartureAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_ACTIVITY_ID) ?: return
        val alarmType = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_ALARM_TYPE) ?: return
        val fallbackVenueName = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_VENUE_NAME).orEmpty()
        val rawLat = intent.getDoubleExtra(DepartureAlarmScheduler.EXTRA_LAT, Double.NaN)
        val rawLng = intent.getDoubleExtra(DepartureAlarmScheduler.EXTRA_LNG, Double.NaN)
        val fallbackLat = rawLat.takeIf { !it.isNaN() }
        val fallbackLng = rawLng.takeIf { !it.isNaN() }
        val fallbackTravelMin = intent.getIntExtra(DepartureAlarmScheduler.EXTRA_TRAVEL_MIN, 0)
        val fallbackEventTime = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_EVENT_TIME).orEmpty()
        val fallbackLeaveTime = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_LEAVE_TIME).orEmpty()
        val fallbackArrivalTime = intent.getStringExtra(DepartureAlarmScheduler.EXTRA_ARRIVAL_TIME).orEmpty()
        val triggerMillis = intent.getLongExtra(DepartureAlarmScheduler.EXTRA_TRIGGER_MILLIS, 0L)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ghost-alarm guard: skip if activity was deleted or user signed out
                val stored = SessionDataStore(context.applicationContext)
                    .loadScheduledDepartures()[activityId] ?: return@launch
                val nowMillis = ZonedDateTime.now(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
                val eventStartMillis = ZonedDateTime.of(stored.eventDate, stored.eventTime, SANTO_DOMINGO_ZONE)
                    .toInstant()
                    .toEpochMilli()
                if (eventStartMillis <= nowMillis) return@launch

                if (alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY ||
                    alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY_FINAL
                ) {
                    val expectedTrigger = if (alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY) {
                        stored.contingencyAtEpochMillis
                    } else {
                        stored.contingencyFinalAtEpochMillis
                    } ?: return@launch
                    if (triggerMillis > 0L && DepartureTimeCalculator.shouldReschedule(triggerMillis, expectedTrigger)) {
                        return@launch
                    }
                    DepartureNotificationDispatcher.dispatchContingencyIfNeeded(
                        context = context,
                        stored = stored,
                        alarmType = alarmType,
                        triggerEpochMillis = triggerMillis,
                    )
                    return@launch
                }

                val expectedTrigger = when (alarmType) {
                    DepartureAlarmScheduler.ALARM_TYPE_PREPARE -> stored.prepareAtEpochMillis
                    DepartureAlarmScheduler.ALARM_TYPE_LEAVE -> stored.leaveAtEpochMillis
                    else -> triggerMillis
                }
                if (triggerMillis > 0L && DepartureTimeCalculator.shouldReschedule(triggerMillis, expectedTrigger)) {
                    return@launch
                }

                DepartureNotificationDispatcher.dispatchIfNew(
                    context = context,
                    activityId = activityId,
                    alarmType = alarmType,
                    venueName = stored.venueName.ifBlank { fallbackVenueName },
                    latitude = stored.latitude ?: fallbackLat,
                    longitude = stored.longitude ?: fallbackLng,
                    travelMinutes = stored.travelMinutes.takeIf { it >= 0 } ?: fallbackTravelMin,
                    eventTimeLabel = DominicanFormatter.formatTime12h(stored.eventTime).ifBlank { fallbackEventTime },
                    leaveTimeLabel = DominicanFormatter.formatTime12h(
                        Instant.ofEpochMilli(stored.leaveAtEpochMillis)
                            .atZone(SANTO_DOMINGO_ZONE)
                            .toLocalTime()
                    ).ifBlank { fallbackLeaveTime },
                    arrivalTimeLabel = DominicanFormatter.formatTime24(stored.estimatedArrivalTime).ifBlank { fallbackArrivalTime },
                    triggerEpochMillis = triggerMillis,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class DailySummaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailySummaryRunner.postSummaryAndReschedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootAndTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in ALLOWED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.getInstance(context.applicationContext)
                container.departureAlarmPort.rescheduleAllFromStorage()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/**
 * WorkManager worker that guarantees "Sal a esta hora", "Prepárate" and "Modo Contingencia"
 * notifications fire reliably even when the app process is closed or after a device reboot.
 */
class DepartureNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val activityId = inputData.getString(DepartureAlarmScheduler.EXTRA_ACTIVITY_ID)
            ?: return Result.success()
        val alarmType = inputData.getString(DepartureAlarmScheduler.EXTRA_ALARM_TYPE)
            ?: return Result.success()
        val triggerMillis = inputData.getLong(DepartureAlarmScheduler.EXTRA_TRIGGER_MILLIS, 0L)

        // Ghost-alarm guard: if activity was deleted or user signed out, skip silently
        val stored = SessionDataStore(applicationContext).loadScheduledDepartures()[activityId]
            ?: return Result.success()

        val nowMillis = ZonedDateTime.now(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
        val eventStartMillis = ZonedDateTime.of(stored.eventDate, stored.eventTime, SANTO_DOMINGO_ZONE)
            .toInstant()
            .toEpochMilli()
        if (eventStartMillis <= nowMillis) {
            return Result.success()
        }

        if (alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY ||
            alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY_FINAL
        ) {
            val expectedTrigger = if (alarmType == DepartureAlarmScheduler.ALARM_TYPE_CONTINGENCY) {
                stored.contingencyAtEpochMillis
            } else {
                stored.contingencyFinalAtEpochMillis
            } ?: return Result.success()
            if (triggerMillis > 0L && DepartureTimeCalculator.shouldReschedule(triggerMillis, expectedTrigger)) {
                return Result.success()
            }
            DepartureNotificationDispatcher.dispatchContingencyIfNeeded(
                context = applicationContext,
                stored = stored,
                alarmType = alarmType,
                triggerEpochMillis = triggerMillis,
            )
            return Result.success()
        }

        val expectedTrigger = when (alarmType) {
            DepartureAlarmScheduler.ALARM_TYPE_PREPARE -> stored.prepareAtEpochMillis
            DepartureAlarmScheduler.ALARM_TYPE_LEAVE -> stored.leaveAtEpochMillis
            else -> triggerMillis
        }
        if (triggerMillis > 0L && DepartureTimeCalculator.shouldReschedule(triggerMillis, expectedTrigger)) {
            return Result.success()
        }

        val eventTime = DominicanFormatter.formatTime12h(stored.eventTime)
        val leaveTime = DominicanFormatter.formatTime12h(
            Instant.ofEpochMilli(stored.leaveAtEpochMillis)
                .atZone(SANTO_DOMINGO_ZONE)
                .toLocalTime()
        )
        val arrivalTime = DominicanFormatter.formatTime24(stored.estimatedArrivalTime)

        DepartureNotificationDispatcher.dispatchIfNew(
            context = applicationContext,
            activityId = activityId,
            alarmType = alarmType,
            venueName = stored.venueName,
            latitude = stored.latitude,
            longitude = stored.longitude,
            travelMinutes = stored.travelMinutes,
            eventTimeLabel = eventTime,
            leaveTimeLabel = leaveTime,
            arrivalTimeLabel = arrivalTime,
            triggerEpochMillis = triggerMillis,
        )
        return Result.success()
    }
}

/**
 * WorkManager worker that refreshes OSRM travel time in the background at T-24h / T-3h
 * and updates the scheduled "Sal a esta hora" WorkManager notification accordingly.
 */
class OsrmDepartureRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val activityId = inputData.getString(DepartureAlarmScheduler.EXTRA_ACTIVITY_ID)
            ?: return Result.success()
        return try {
            val container = AppContainer.getInstance(applicationContext)
            container.departureAlarmScheduler.recomputeSingleActivityRoute(activityId)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

/**
 * Periodic WorkManager safeguard (every 6h) that keeps upcoming departure notifications
 * armed even if the user leaves the app closed for several days.
 */
class DepartureSyncPeriodicWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val container = AppContainer.getInstance(applicationContext)
            container.departureAlarmPort.rescheduleAllFromStorage()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DailySummaryRunner.postSummaryAndReschedule(applicationContext)
        return Result.success()
    }
}

internal object DailySummaryRunner {
    suspend fun postSummaryAndReschedule(context: Context) {
        val appContext = context.applicationContext
        val dataStore = SessionDataStore(appContext)
        val nowZoned = ZonedDateTime.now(SANTO_DOMINGO_ZONE)
        val today = nowZoned.toLocalDate()
        val allScheduled = dataStore.loadScheduledDepartures().values
        val depPrefs = dataStore.loadDeparturePrefs()

        // 1. CHANNEL_SUMMARY: Today's gig schedule only
        val todaysDepartures = allScheduled
            .filter { it.eventDate == today }
            .sortedBy { it.eventTime }

        if (todaysDepartures.isNotEmpty()) {
            val lines = todaysDepartures.map { dep ->
                val leaveLocal = Instant.ofEpochMilli(dep.leaveAtEpochMillis)
                    .atZone(SANTO_DOMINGO_ZONE)
                    .toLocalTime()
                "${DominicanFormatter.formatTime24(dep.eventTime)} · ${dep.venueName} (sal ${DominicanFormatter.formatTime12h(leaveLocal)})"
            }
            NotificationHelper.postDailySummaryNotification(
                context = appContext,
                summaryLines = lines.joinToString("\n"),
            )
        }

        // 2. CHANNEL_RECEIVABLES: Independent Monday overdue reminder with direct deep-link to Cobros
        if (today.dayOfWeek == DayOfWeek.MONDAY) {
            val overdue = dataStore.loadDailyOverdueSummary()
            if (!overdue.isNullOrBlank()) {
                NotificationHelper.postReceivablesReminderNotification(
                    context = appContext,
                    overdueAmountText = overdue,
                )
            }
        }

        // 3. CHANNEL_SCORES: Independent repertoire / scores reminder N days before upcoming gigs
        val scoreDays = depPrefs.scoreReminderDays
        if (scoreDays > 0) {
            val targetDate = today.plusDays(scoreDays.toLong())
            val upcomingForScores = allScheduled
                .filter { it.eventDate == targetDate }
                .sortedBy { it.eventTime }
            if (upcomingForScores.isNotEmpty()) {
                val detailLines = upcomingForScores.map { dep ->
                    val timeLabel = DominicanFormatter.formatTime12h(dep.eventTime)
                    val supplierPart = dep.supplierName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    val notesPart = dep.description?.takeIf { it.isNotBlank() }?.let { " — ${it.take(80)}" }.orEmpty()
                    "$timeLabel · ${dep.venueName}$supplierPart$notesPart"
                }
                NotificationHelper.postScoreReminderNotification(
                    context = appContext,
                    daysAhead = scoreDays,
                    targetDateLabel = DominicanFormatter.formatChipDate(targetDate),
                    detailsLines = detailLines.joinToString("\n"),
                )
            }
        }

        val container = AppContainer.getInstance(appContext)
        container.departureAlarmScheduler.scheduleNextDailySummary(nowZoned.plusMinutes(2))
    }
}
