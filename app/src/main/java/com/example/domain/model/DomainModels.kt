package com.example.domain.model

import com.example.domain.rules.RouteOriginKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val SANTO_DOMINGO_ZONE: ZoneId = ZoneId.of("America/Santo_Domingo")

enum class Currency(val code: String, val symbol: String) {
    DOP("DOP", "RD$"),
    USD("USD", "US$");

    companion object {
        fun fromCode(code: String?): Currency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DOP
    }
}

enum class ActivityType(val dbValue: String) {
    FIJO("fijo"),
    EVENTUAL("eventual");

    companion object {
        fun fromDb(value: String?): ActivityType =
            if (value.equals("fijo", ignoreCase = true)) FIJO else EVENTUAL
    }
}

enum class GigCategory(val label: String, val activityType: ActivityType) {
    ACTIVIDAD_FIJA("Actividad fija", ActivityType.FIJO),
    BODA("Boda", ActivityType.EVENTUAL),
    COCTEL("Cóctel", ActivityType.EVENTUAL),
    PRIVADO("Privado", ActivityType.EVENTUAL);

    companion object {
        fun infer(activityType: ActivityType, description: String?): GigCategory {
            if (activityType == ActivityType.FIJO) return ACTIVIDAD_FIJA
            val firstLine = description?.lineSequence()?.firstOrNull()?.trim().orEmpty().lowercase()
            return when {
                firstLine.startsWith("boda") -> BODA
                firstLine.startsWith("cóctel") || firstLine.startsWith("coctel") -> COCTEL
                firstLine.startsWith("privado") -> PRIVADO
                firstLine.startsWith("actividad fija") -> ACTIVIDAD_FIJA
                else -> BODA
            }
        }
    }
}

enum class PaymentStatus(val dbValue: String) {
    PENDIENTE("pendiente"),
    PAGADO("pagado");

    companion object {
        fun fromDb(value: String?): PaymentStatus =
            if (value.equals("pagado", ignoreCase = true)) PAGADO else PENDIENTE
    }
}

data class Activity(
    val id: String,
    val ownerId: String,
    val supplierId: String? = null,
    val supplierName: String = "",
    val title: String,
    val activityType: ActivityType = ActivityType.EVENTUAL,
    val eventDate: LocalDate,
    val eventTime: LocalTime? = null,
    val venueId: String? = null,
    val venueName: String,
    val venueSubLocation: String? = null,
    val venueAddress: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val description: String? = null,
    val amount: BigDecimal,
    val currency: Currency = Currency.DOP,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDIENTE,
    val paidAt: String? = null,
    val tags: List<String> = emptyList(),
    val seriesId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val melodyflowId: String? = null,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

data class ActivityDraft(
    val supplierName: String,
    val category: GigCategory,
    val eventDate: LocalDate,
    val eventTime: LocalTime?,
    val venueId: String? = null,
    val venueName: String,
    val description: String?,
    val amount: BigDecimal,
    val currency: Currency,
    val isPaid: Boolean,
    val venueSubLocation: String? = null,
    val venueAddress: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class Venue(
    val id: String = "",
    val ownerId: String = "",
    val name: String,
    val placeId: String? = null,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val notes: String? = null,
)

data class CachedActivityRoute(
    val activityId: String,
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    val travelMinutes: Int,
    val distanceKm: Double? = null,
    val fromOsrm: Boolean = true,
    val originKind: RouteOriginKind? = null,
    val originVenueName: String? = null,
    val shortfallMinutes: Int = 0,
    val unpinnedPreviousVenueName: String? = null,
    /** Times from the artist's tracked route; [travelMinutes] stays the measured estimate. */
    val trackedDriveMinutes: Int? = null,
    val trackedWalkMinutes: Int? = null,
    val trackedSetupMinutes: Int? = null,
    val trackedMarginMinutes: Int? = null,
) {
    /**
     * Returns true when origin (~1 km tolerance) and destination (~50 m tolerance) match,
     * allowing reuse of the stored OSRM estimate without repeating network requests.
     */
    fun matchesCoordinates(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): Boolean {
        val sameDest = kotlin.math.abs(this.destLat - destLat) < 0.0005 &&
            kotlin.math.abs(this.destLng - destLng) < 0.0005
        val sameOrigin = kotlin.math.abs(this.originLat - originLat) < 0.01 &&
            kotlin.math.abs(this.originLng - originLng) < 0.01
        return sameDest && sameOrigin
    }
}

data class ScheduledDeparture(
    val activityId: String,
    val venueName: String,
    val eventDate: LocalDate,
    val eventTime: LocalTime,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val travelMinutes: Int,
    val distanceKm: Double? = null,
    val prepareAtEpochMillis: Long,
    val leaveAtEpochMillis: Long,
    val estimatedArrivalTime: LocalTime,
    val isOsrmEstimated: Boolean,
    val alarmsScheduled: Boolean = false,
    val supplierName: String? = null,
    val description: String? = null,
    val contingencyAtEpochMillis: Long? = null,
    val contingencyFinalAtEpochMillis: Long? = null,
    val originKind: RouteOriginKind? = null,
    val originVenueName: String? = null,
    val originLatitude: Double? = null,
    val originLongitude: Double? = null,
    val shortfallMinutes: Int = 0,
    val unpinnedPreviousVenueName: String? = null,
    /** True when [travelMinutes] is the artist's own driving time. */
    val isTrackedDrive: Boolean = false,
    /** Set with a tracked route: leaving after this means arriving late even if all goes well. */
    val latestLeaveAtEpochMillis: Long? = null,
) {
    /** The artist leaves for this event straight from the previous one. */
    val isChained: Boolean get() = originKind == RouteOriginKind.PREVIOUS_EVENT

    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

data class Supplier(
    val id: String,
    val ownerId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
)

data class LabSettings(
    val ownerId: String = "",
    val defaultCurrency: Currency = Currency.DOP,
    val setupMinutes: Int = 45,
    val eventMinutes: Int = 120,
    val teardownMinutes: Int = 45,
    val monthlyGoal: BigDecimal = BigDecimal("100000"),
    val prepMinutes: Int = 60,
    val travelBufferMinutes: Int = 15,
    val internalTransferInMinutes: Int = 15,
    val preStartMarginMinutes: Int = 15,
    val internalTransferOutMinutes: Int = 15,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val scoreReminderDays: Int = 3,
    val contingencyEnabled: Boolean = true,
    val contingencyLeadMinutes: Int = 30,
    val monthlyGoalSyncedOnServer: Boolean = false,
)

data class ActivityTimelineOverride(
    val activityId: String = "",
    val prepMinutes: Int? = null,
    val travelMinutes: Int? = null,
    val travelBufferMinutes: Int? = null,
    val internalTransferInMinutes: Int? = null,
    val setupMinutes: Int? = null,
    val preStartMarginMinutes: Int? = null,
    val eventMinutes: Int? = null,
    val teardownMinutes: Int? = null,
    val internalTransferOutMinutes: Int? = null,
) {
    val hasAnyOverride: Boolean
        get() = prepMinutes != null ||
            travelMinutes != null ||
            travelBufferMinutes != null ||
            internalTransferInMinutes != null ||
            setupMinutes != null ||
            preStartMarginMinutes != null ||
            eventMinutes != null ||
            teardownMinutes != null ||
            internalTransferOutMinutes != null
}

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    fun isExpiredOrExpiringSoon(nowEpochSeconds: Long, leewaySeconds: Long = 60): Boolean =
        expiresAtEpochSeconds <= nowEpochSeconds + leewaySeconds
}

enum class BankAccountType(val dbValue: String, val label: String) {
    AHORROS("ahorros", "Ahorros"),
    CORRIENTE("corriente", "Corriente"),
    OTRO("otro", "Otro");

    companion object {
        fun fromDb(value: String?): BankAccountType? =
            entries.firstOrNull { it.dbValue.equals(value?.trim(), ignoreCase = true) }
    }
}

data class BankAccount(
    val id: String,
    val ownerId: String = "",
    val label: String,
    val bankName: String,
    val holderName: String,
    val accountNumber: String,
    val accountType: BankAccountType? = null,
    val currency: Currency = Currency.DOP,
    val isDefault: Boolean = false,
)

data class BankAccountDraft(
    val label: String,
    val bankName: String,
    val holderName: String,
    val accountNumber: String,
    val accountType: BankAccountType? = null,
    val currency: Currency = Currency.DOP,
    val isDefault: Boolean = false,
)

data class LoginSessionEvent(
    val id: String,
    val userId: String,
    val email: String,
    val timestampEpochMillis: Long,
    val ipAddress: String,
    val deviceName: String,
    val androidVersion: String,
    val eventType: String,
    val success: Boolean,
)

sealed interface SignUpOutcome {
    data class Authenticated(val session: AuthSession) : SignUpOutcome
    data class EmailConfirmationRequired(val email: String) : SignUpOutcome
}

sealed class AppError(override val message: String) : Exception(message) {
    data object ScheduleConflict : AppError(
        "Conflicto de horario: ya existe otro servicio en el intervalo reservado."
    )
    data class Unauthorized(
        override val message: String = "Tu sesión venció. Inicia sesión de nuevo.",
        val refreshTokenRejected: Boolean = false,
    ) : AppError(message)
    data class EmailNotConfirmed(
        override val message: String = "Tu correo aún no ha sido confirmado. Revisa tu bandeja de entrada o reenvía el correo de confirmación."
    ) : AppError(message)
    data class RateLimited(
        override val message: String = "Demasiados intentos. Espera un minuto e inténtalo de nuevo."
    ) : AppError(message)
    data object MissingConfig : AppError(
        "Falta configurar SUPABASE_PUBLISHABLE_KEY en el panel de Secrets de AI Studio."
    )
    data object ServerFeaturePending : AppError("Función pendiente de activar en el servidor.")
    data class Validation(override val message: String) : AppError(message)
    data class Network(override val message: String) : AppError(message)
}
