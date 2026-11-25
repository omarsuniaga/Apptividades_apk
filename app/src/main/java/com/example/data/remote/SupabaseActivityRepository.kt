package com.example.data.remote

import com.example.data.port.ActivityRepository
import com.example.data.port.AuthRepository
import com.example.data.remote.dto.ActivityDto
import com.example.data.remote.dto.ActivityInsertDto
import com.example.data.remote.dto.ActivityPatchDto
import com.example.data.remote.dto.PaymentPatchDto
import com.example.domain.model.Activity
import com.example.domain.model.ActivityDraft
import com.example.domain.model.ActivityType
import com.example.domain.model.AppError
import com.example.domain.model.Currency
import com.example.domain.model.PaymentStatus
import com.example.domain.rules.ActivityTitleDeriver
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@JsonClass(generateAdapter = true)
internal data class ActivitySeriesPatchDto(
    @Json(name = "supplier_name") val supplierName: String,
    val title: String,
    @Json(name = "activity_type") val activityType: String,
    @Json(name = "event_time") val eventTime: String?,
    @Json(name = "venue_name") val venueName: String,
    @Json(name = "venue_sub_location") val venueSubLocation: String?,
    @Json(name = "venue_address") val venueAddress: String?,
    val city: String?,
    val notes: String?,
    val description: String?,
    val amount: Double,
    val currency: String,
    @Json(name = "payment_status") val paymentStatus: String,
    @Json(name = "paid_at") val paidAt: String?,
    val tags: List<String>,
    val latitude: Double?,
    val longitude: Double?,
)

class SupabaseActivityRepository(
    private val httpClient: SupabaseHttpClient,
    private val authRepository: AuthRepository,
) : ActivityRepository {

    private val listType = Types.newParameterizedType(List::class.java, ActivityDto::class.java)
    private val listAdapter = httpClient.moshi.adapter<List<ActivityDto>>(listType)
    private val insertAdapter = httpClient.moshi.adapter(ActivityInsertDto::class.java)
    private val insertListType = Types.newParameterizedType(List::class.java, ActivityInsertDto::class.java)
    private val insertListAdapter = httpClient.moshi.adapter<List<ActivityInsertDto>>(insertListType)
    private val patchAdapter = httpClient.moshi.adapter(ActivityPatchDto::class.java).serializeNulls()
    private val seriesPatchAdapter = httpClient.moshi.adapter(ActivitySeriesPatchDto::class.java).serializeNulls()
    private val paymentPatchAdapter = httpClient.moshi.adapter(PaymentPatchDto::class.java).serializeNulls()

    private val dbTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    override suspend fun fetchAllActivities(ownerId: String): Result<List<Activity>> {
        val collected = mutableListOf<Activity>()
        var offset = 0
        val pageSize = SupabaseConfig.PAGE_SIZE

        while (true) {
            val url = httpClient.baseUrl.newBuilder()
                .addPathSegments("rest/v1/lab_activities")
                .addQueryParameter("select", "*")
                .addQueryParameter("owner_id", "eq.$ownerId")
                .addQueryParameter("order", "event_date.asc,id.asc")
                .build()

            val rangeHeader = "$offset-${offset + pageSize - 1}"
            val response = httpClient.executeAuthenticated(
                authRepository = authRepository,
                method = "GET",
                url = url,
                extraHeaders = mapOf("Range" to rangeHeader),
            ).getOrElse { return Result.failure(it) }

            val dtos = listAdapter.fromJson(response.body).orEmpty()
            collected.addAll(dtos.mapNotNull { it.toDomainOrNull() })

            if (dtos.size < pageSize) break
            offset += pageSize
        }

        return Result.success(collected)
    }

    override suspend fun createActivity(ownerId: String, draft: ActivityDraft): Result<Activity> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .build()

        val dto = draft.toInsertDto(ownerId = ownerId, date = draft.eventDate, seriesId = null, includeVenueId = true)
        var attempt = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = url,
            jsonBody = insertAdapter.toJson(dto),
            extraHeaders = mapOf("Prefer" to "return=representation"),
        )
        if (attempt.isFailure && attempt.exceptionOrNull() is AppError.ServerFeaturePending && draft.venueId != null) {
            val fallbackDto = draft.toInsertDto(ownerId = ownerId, date = draft.eventDate, seriesId = null, includeVenueId = false)
            attempt = httpClient.executeAuthenticated(
                authRepository = authRepository,
                method = "POST",
                url = url,
                jsonBody = insertAdapter.toJson(fallbackDto),
                extraHeaders = mapOf("Prefer" to "return=representation"),
            )
        }
        val response = attempt.getOrElse { return Result.failure(it) }

        val created = listAdapter.fromJson(response.body)?.firstOrNull()?.toDomainOrNull()
            ?: return Result.failure(AppError.Network("No se pudo interpretar la actividad creada."))
        return Result.success(created)
    }

    override suspend fun createWeeklySeries(
        ownerId: String,
        draft: ActivityDraft,
        repeatWeeks: Int,
    ): Result<List<Activity>> {
        val count = repeatWeeks.coerceIn(2, 52)
        val seriesId = UUID.randomUUID().toString()
        fun buildRows(includeVenueId: Boolean) = (0 until count).map { weekOffset ->
            draft.toInsertDto(
                ownerId = ownerId,
                date = draft.eventDate.plusWeeks(weekOffset.toLong()),
                seriesId = seriesId,
                includeVenueId = includeVenueId,
            )
        }

        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .build()

        var attempt = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = url,
            jsonBody = insertListAdapter.toJson(buildRows(includeVenueId = true)),
            extraHeaders = mapOf("Prefer" to "return=representation"),
        )
        if (attempt.isFailure && attempt.exceptionOrNull() is AppError.ServerFeaturePending && draft.venueId != null) {
            attempt = httpClient.executeAuthenticated(
                authRepository = authRepository,
                method = "POST",
                url = url,
                jsonBody = insertListAdapter.toJson(buildRows(includeVenueId = false)),
                extraHeaders = mapOf("Prefer" to "return=representation"),
            )
        }
        val response = attempt.getOrElse { return Result.failure(it) }

        val created = listAdapter.fromJson(response.body).orEmpty().mapNotNull { it.toDomainOrNull() }
        return Result.success(created)
    }

    override suspend fun updateActivity(
        ownerId: String,
        activityId: String,
        draft: ActivityDraft,
    ): Result<Activity> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .addQueryParameter("id", "eq.$activityId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        val effectiveDescription = buildEffectiveDescription(draft)
        val title = ActivityTitleDeriver.derive(draft.supplierName, effectiveDescription, draft.venueName)
        val paidAt = if (draft.isPaid) Instant.now().toString() else null
        val bothCoords = if (draft.latitude != null && draft.longitude != null) draft.latitude to draft.longitude else null

        val patch = ActivityPatchDto(
            supplierName = draft.supplierName.trim(),
            title = title,
            activityType = draft.category.activityType.dbValue,
            eventDate = draft.eventDate.toString(),
            eventTime = draft.eventTime?.format(dbTimeFormatter),
            venueName = draft.venueName.trim().take(200),
            venueSubLocation = draft.venueSubLocation,
            venueAddress = draft.venueAddress,
            city = draft.city,
            notes = draft.notes,
            description = effectiveDescription,
            amount = draft.amount.toDouble(),
            currency = draft.currency.code,
            paymentStatus = if (draft.isPaid) PaymentStatus.PAGADO.dbValue else PaymentStatus.PENDIENTE.dbValue,
            paidAt = paidAt,
            tags = draft.tags,
            latitude = bothCoords?.first,
            longitude = bothCoords?.second,
        )

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = patchAdapter.toJson(patch),
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).getOrElse { return Result.failure(it) }

        val updated = listAdapter.fromJson(response.body)?.firstOrNull()?.toDomainOrNull()
            ?: return Result.failure(AppError.Network("No se encontró la actividad actualizada."))
        return Result.success(updated)
    }

    override suspend fun updateSeries(
        ownerId: String,
        seriesId: String,
        draft: ActivityDraft,
    ): Result<List<Activity>> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .addQueryParameter("series_id", "eq.$seriesId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        val effectiveDescription = buildEffectiveDescription(draft)
        val title = ActivityTitleDeriver.derive(draft.supplierName, effectiveDescription, draft.venueName)
        val paidAt = if (draft.isPaid) Instant.now().toString() else null
        val bothCoords = if (draft.latitude != null && draft.longitude != null) draft.latitude to draft.longitude else null

        val patch = ActivitySeriesPatchDto(
            supplierName = draft.supplierName.trim(),
            title = title,
            activityType = draft.category.activityType.dbValue,
            eventTime = draft.eventTime?.format(dbTimeFormatter),
            venueName = draft.venueName.trim().take(200),
            venueSubLocation = draft.venueSubLocation,
            venueAddress = draft.venueAddress,
            city = draft.city,
            notes = draft.notes,
            description = effectiveDescription,
            amount = draft.amount.toDouble(),
            currency = draft.currency.code,
            paymentStatus = if (draft.isPaid) PaymentStatus.PAGADO.dbValue else PaymentStatus.PENDIENTE.dbValue,
            paidAt = paidAt,
            tags = draft.tags,
            latitude = bothCoords?.first,
            longitude = bothCoords?.second,
        )

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = seriesPatchAdapter.toJson(patch),
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).getOrElse { return Result.failure(it) }

        val updated = listAdapter.fromJson(response.body).orEmpty().mapNotNull { it.toDomainOrNull() }
        return Result.success(updated)
    }

    override suspend fun markActivitiesPaid(ownerId: String, activityIds: List<String>): Result<Unit> {
        if (activityIds.isEmpty()) return Result.success(Unit)
        val idFilter = "in.(${activityIds.joinToString(",")})"
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .addQueryParameter("payment_status", "eq.${PaymentStatus.PENDIENTE.dbValue}")
            .addQueryParameter("id", idFilter)
            .build()

        val payload = paymentPatchAdapter.toJson(
            PaymentPatchDto(
                paymentStatus = PaymentStatus.PAGADO.dbValue,
                paidAt = Instant.now().toString(),
            )
        )

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = payload,
            extraHeaders = mapOf("Prefer" to "return=minimal"),
        ).map { Unit }
    }

    override suspend fun togglePaymentStatus(ownerId: String, activity: Activity): Result<Activity> {
        val nextStatus = if (activity.paymentStatus == PaymentStatus.PAGADO) {
            PaymentStatus.PENDIENTE
        } else {
            PaymentStatus.PAGADO
        }
        val paidAt = if (nextStatus == PaymentStatus.PAGADO) Instant.now().toString() else null

        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .addQueryParameter("id", "eq.${activity.id}")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        val payload = paymentPatchAdapter.toJson(
            PaymentPatchDto(
                paymentStatus = nextStatus.dbValue,
                paidAt = paidAt,
            )
        )

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "PATCH",
            url = url,
            jsonBody = payload,
            extraHeaders = mapOf("Prefer" to "return=representation"),
        ).getOrElse { return Result.failure(it) }

        val updated = listAdapter.fromJson(response.body)?.firstOrNull()?.toDomainOrNull()
            ?: return Result.failure(AppError.Network("No se pudo actualizar el estado de pago."))
        return Result.success(updated)
    }

    override suspend fun deleteActivity(ownerId: String, activityId: String): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .addQueryParameter("id", "eq.$activityId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "DELETE",
            url = url,
        ).map { Unit }
    }

    override suspend fun deleteSeries(ownerId: String, seriesId: String): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .addQueryParameter("series_id", "eq.$seriesId")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .build()

        return httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "DELETE",
            url = url,
        ).map { Unit }
    }

    private fun ActivityDraft.toInsertDto(
        ownerId: String,
        date: LocalDate,
        seriesId: String?,
        includeVenueId: Boolean,
    ): ActivityInsertDto {
        val effectiveDescription = buildEffectiveDescription(this)
        val title = ActivityTitleDeriver.derive(supplierName, effectiveDescription, venueName)
        val bothCoords = if (latitude != null && longitude != null) latitude to longitude else null
        return ActivityInsertDto(
            ownerId = ownerId,
            supplierId = null,
            supplierName = supplierName.trim(),
            title = title,
            activityType = category.activityType.dbValue,
            eventDate = date.toString(),
            eventTime = eventTime?.format(dbTimeFormatter),
            venueId = if (includeVenueId) venueId?.takeIf { it.isNotBlank() } else null,
            venueName = venueName.trim().take(200),
            venueSubLocation = venueSubLocation,
            venueAddress = venueAddress,
            city = city,
            notes = notes,
            description = effectiveDescription,
            amount = amount.toDouble(),
            currency = currency.code,
            paymentStatus = if (isPaid) PaymentStatus.PAGADO.dbValue else PaymentStatus.PENDIENTE.dbValue,
            paidAt = if (isPaid) Instant.now().toString() else null,
            tags = tags,
            seriesId = seriesId,
            latitude = bothCoords?.first,
            longitude = bothCoords?.second,
        )
    }

    private fun buildEffectiveDescription(draft: ActivityDraft): String? {
        val clean = draft.description?.trim().orEmpty()
        return clean.ifEmpty { draft.category.label }
    }

    private fun ActivityDto.toDomainOrNull(): Activity? {
        val parsedDate = runCatching { LocalDate.parse(eventDate.take(10)) }.getOrNull() ?: return null
        val parsedTime = eventTime?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { LocalTime.parse(raw.take(8)) }.getOrNull()
                ?: runCatching { LocalTime.parse(raw.take(5)) }.getOrNull()
        }
        return Activity(
            id = id,
            ownerId = ownerId,
            supplierId = supplierId,
            supplierName = supplierName.orEmpty(),
            title = title?.takeIf { it.isNotBlank() }
                ?: ActivityTitleDeriver.derive(supplierName, description, venueName),
            activityType = ActivityType.fromDb(activityType),
            eventDate = parsedDate,
            eventTime = parsedTime,
            venueId = venueId,
            venueName = venueName.orEmpty(),
            venueSubLocation = venueSubLocation,
            venueAddress = venueAddress,
            city = city,
            notes = notes,
            description = description,
            amount = BigDecimal.valueOf(amount ?: 0.0),
            currency = Currency.fromCode(currency),
            paymentStatus = PaymentStatus.fromDb(paymentStatus),
            paidAt = paidAt,
            tags = tags.orEmpty(),
            seriesId = seriesId,
            latitude = latitude,
            longitude = longitude,
            melodyflowId = melodyflowId,
        )
    }
}
