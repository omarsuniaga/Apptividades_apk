package com.example.data.remote

import com.example.data.port.AuthRepository
import com.example.data.port.VenueRepository
import com.example.domain.model.AppError
import com.example.domain.model.Venue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class VenueDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    @Json(name = "place_id") val placeId: String? = null,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class VenueUpsertDto(
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    @Json(name = "place_id") val placeId: String? = null,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val notes: String? = null,
)

class SupabaseVenueRepository(
    private val httpClient: SupabaseHttpClient,
    private val authRepository: AuthRepository,
) : VenueRepository {

    private val listType = Types.newParameterizedType(List::class.java, VenueDto::class.java)
    private val listAdapter = httpClient.moshi.adapter<List<VenueDto>>(listType)
    private val upsertAdapter = httpClient.moshi.adapter(VenueUpsertDto::class.java)

    override suspend fun fetchVenues(ownerId: String): Result<List<Venue>> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_venues")
            .addQueryParameter("select", "*")
            .addQueryParameter("owner_id", "eq.$ownerId")
            .addQueryParameter("order", "name.asc")
            .build()

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "GET",
            url = url,
        ).getOrElse { return Result.failure(it) }

        val dtos = listAdapter.fromJson(response.body).orEmpty()
        return Result.success(dtos.map { it.toDomain() })
    }

    override suspend fun upsertVenue(ownerId: String, venue: Venue): Result<Venue> {
        val builder = httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_venues")
        val headers = mutableMapOf("Prefer" to "return=representation")
        if (!venue.placeId.isNullOrBlank()) {
            builder.addQueryParameter("on_conflict", "owner_id,place_id")
            headers["Prefer"] = "resolution=merge-duplicates,return=representation"
        }

        val dto = VenueUpsertDto(
            ownerId = ownerId,
            name = venue.name.trim().take(200),
            placeId = venue.placeId?.trim()?.takeIf { it.isNotEmpty() },
            address = venue.address?.trim()?.takeIf { it.isNotEmpty() },
            latitude = venue.latitude,
            longitude = venue.longitude,
            notes = venue.notes?.trim()?.takeIf { it.isNotEmpty() },
        )

        val response = httpClient.executeAuthenticated(
            authRepository = authRepository,
            method = "POST",
            url = builder.build(),
            jsonBody = upsertAdapter.toJson(dto),
            extraHeaders = headers,
        ).getOrElse { return Result.failure(it) }

        val saved = listAdapter.fromJson(response.body)?.firstOrNull()?.toDomain()
            ?: return Result.failure(AppError.Network("No se pudo guardar el lugar."))
        return Result.success(saved)
    }

    private fun VenueDto.toDomain() = Venue(
        id = id,
        ownerId = ownerId,
        name = name,
        placeId = placeId,
        address = address,
        latitude = latitude,
        longitude = longitude,
        notes = notes,
    )
}
