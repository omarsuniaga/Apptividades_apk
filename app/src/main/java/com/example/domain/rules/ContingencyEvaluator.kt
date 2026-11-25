package com.example.domain.rules

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ContingencyState {
    /** Event is still outside the critical pre-start window or already started. */
    MONITORING,
    /** Artist is close to start time, hasn't confirmed arrival/under-control, and is not near the venue. */
    ACTIVE_ALERT,
    /** Artist explicitly told the system "Todo bajo control" (silences contingency alerts). */
    UNDER_CONTROL,
    /** Device GPS shows artist is already within 500 m of the venue. */
    ARRIVED_NEARBY,
}

data class ContingencyAssessment(
    val state: ContingencyState,
    val minutesUntilStart: Long,
    val distanceMeters: Double?,
    val shouldNotify: Boolean,
    val isFinalEscalation: Boolean,
)

/**
 * Pure domain evaluator for "Modo Contingencia":
 * detects when an artist is very close to event start time and has neither confirmed
 * "Todo bajo control" nor arrived near the venue, triggering supplier-notification alerts.
 */
object ContingencyEvaluator {

    const val DEFAULT_LEAD_MINUTES = 30
    const val FINAL_ESCALATION_LEAD_MINUTES = 10
    const val NEARBY_RADIUS_METERS = 500.0

    fun haversineDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    fun evaluate(
        now: LocalDateTime,
        eventStart: LocalDateTime,
        contingencyEnabled: Boolean,
        leadMinutes: Int = DEFAULT_LEAD_MINUTES,
        isMarkedUnderControl: Boolean,
        venueLat: Double?,
        venueLng: Double?,
        currentLat: Double?,
        currentLng: Double?,
        nearbyRadiusMeters: Double = NEARBY_RADIUS_METERS,
    ): ContingencyAssessment {
        val minutesUntilStart = Duration.between(now, eventStart).toMinutes()
        val safeLead = leadMinutes.coerceIn(5, 120).toLong()

        val distanceMeters: Double? = if (
            venueLat != null && venueLng != null &&
            currentLat != null && currentLng != null
        ) {
            haversineDistanceMeters(currentLat, currentLng, venueLat, venueLng)
        } else {
            null
        }

        if (isMarkedUnderControl) {
            return ContingencyAssessment(
                state = ContingencyState.UNDER_CONTROL,
                minutesUntilStart = minutesUntilStart,
                distanceMeters = distanceMeters,
                shouldNotify = false,
                isFinalEscalation = false,
            )
        }

        if (!contingencyEnabled || minutesUntilStart <= 0L || minutesUntilStart > safeLead) {
            return ContingencyAssessment(
                state = ContingencyState.MONITORING,
                minutesUntilStart = minutesUntilStart,
                distanceMeters = distanceMeters,
                shouldNotify = false,
                isFinalEscalation = false,
            )
        }

        if (distanceMeters != null && distanceMeters <= nearbyRadiusMeters) {
            return ContingencyAssessment(
                state = ContingencyState.ARRIVED_NEARBY,
                minutesUntilStart = minutesUntilStart,
                distanceMeters = distanceMeters,
                shouldNotify = false,
                isFinalEscalation = false,
            )
        }

        return ContingencyAssessment(
            state = ContingencyState.ACTIVE_ALERT,
            minutesUntilStart = minutesUntilStart,
            distanceMeters = distanceMeters,
            shouldNotify = true,
            isFinalEscalation = minutesUntilStart <= FINAL_ESCALATION_LEAD_MINUTES,
        )
    }

    fun formatDistanceLabel(distanceMeters: Double?): String? {
        if (distanceMeters == null || distanceMeters.isNaN()) return null
        return if (distanceMeters < 1000.0) {
            "${distanceMeters.toInt()} m"
        } else {
            val km = distanceMeters / 1000.0
            "${DominicanFormatter.formatOneDecimal(km)} km"
        }
    }
}
