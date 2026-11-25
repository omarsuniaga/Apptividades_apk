package com.example.data.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.data.local.LocalPhotonPlaceCache
import com.example.data.local.PhotonPlaceCachePort
import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.data.port.RouteEstimate
import com.example.domain.model.AppError
import com.example.domain.model.Venue
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * 100% free, zero-API-key geo services client:
 * - Hotel/place autocomplete via Photon (Komoot / OpenStreetMap):
 *   https://photon.komoot.io/api/?q={texto}&lat=18.58&lon=-68.40&limit=8
 * - Estimated driving distance & duration (no traffic) via OSRM:
 *   https://router.project-osrm.org/route/v1/driving/{lng1},{lat1};{lng2},{lat2}?overview=false
 * - Device GPS foreground location via Android FusedLocationProviderClient (optional).
 */
class OpenMapsApiClient(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val placeCache: PhotonPlaceCachePort? = null,
) : MapsAndLocationPort {

    private val publicApiClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val photonResolvedCache = ConcurrentHashMap<String, ResolvedPlace>()

    override fun observeCachedPlaces(): Flow<List<Venue>> =
        placeCache?.observeSavedPlaces()?.map { places ->
            places.filter { it.useCount > 0 }.map { it.toVenue() }
        } ?: flowOf(emptyList())

    override suspend fun rememberSelectedPlace(
        name: String,
        address: String?,
        latitude: Double,
        longitude: Double,
        placeId: String?,
    ) {
        val resolved = ResolvedPlace(
            placeId = placeId.orEmpty(),
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
        )
        if (!placeId.isNullOrBlank()) {
            photonResolvedCache[placeId] = resolved
        }
        placeCache?.recordPlaceSelection(resolved)
    }

    override suspend fun searchPlaces(query: String): Result<List<PlacePrediction>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().take(MAX_QUERY_LENGTH)
        if (trimmed.length < MIN_SEARCH_CHARS) return@withContext Result.success(emptyList())

        // 1. Check persistent DataStore query cache first (0 network calls on repeated searches)
        val cachedQueryResults = placeCache?.findCachedQueryPredictions(trimmed)
        if (!cachedQueryResults.isNullOrEmpty()) {
            return@withContext Result.success(cachedQueryResults)
        }

        // 2. Check frequent previously selected hotels in DataStore (0 network calls on frequent hotels)
        val frequentMatches = placeCache?.findFrequentMatches(trimmed).orEmpty()
        if (LocalPhotonPlaceCache.isStrongFrequentMatch(trimmed, frequentMatches)) {
            frequentMatches.forEach { cached ->
                photonResolvedCache[cached.placeId] = cached.toResolvedPlace()
            }
            return@withContext Result.success(frequentMatches.map { it.toPlacePrediction() })
        }

        val url = buildPhotonUrl(trimmed)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        val networkResult = runCatching {
            publicApiClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 429) {
                    throw AppError.Network("Límite temporal de búsqueda alcanzado. Puedes escribir la dirección a mano.")
                }
                if (!resp.isSuccessful) {
                    throw AppError.Network("No se pudo conectar con la búsqueda de hoteles (HTTP ${resp.code}).")
                }
                val parsed = parsePhotonResponse(raw)
                if (photonResolvedCache.size > MAX_CACHE_ENTRIES) {
                    photonResolvedCache.clear()
                }
                parsed.forEach { (prediction, resolved) ->
                    photonResolvedCache[prediction.placeId] = resolved
                }
                placeCache?.saveSearchResults(trimmed, parsed)
                parsed.map { it.first }
            }
        }

        // 3. If Photon is offline/throttled, fall back to any local cached frequent matches seamlessly
        if (networkResult.isFailure && frequentMatches.isNotEmpty()) {
            frequentMatches.forEach { cached ->
                photonResolvedCache[cached.placeId] = cached.toResolvedPlace()
            }
            return@withContext Result.success(frequentMatches.map { it.toPlacePrediction() })
        }

        networkResult
    }

    override suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace> = withContext(Dispatchers.IO) {
        val cleanId = placeId.trim()
        val fromMemory = photonResolvedCache[cleanId]
        if (fromMemory != null) {
            placeCache?.recordPlaceSelection(fromMemory)
            return@withContext Result.success(fromMemory)
        }
        val fromDisk = placeCache?.findByPlaceId(cleanId)?.toResolvedPlace()
        if (fromDisk != null) {
            photonResolvedCache[cleanId] = fromDisk
            placeCache.recordPlaceSelection(fromDisk)
            return@withContext Result.success(fromDisk)
        }
        Result.failure(AppError.Network("No se encontraron coordenadas para el lugar seleccionado."))
    }

    override suspend fun computeRouteEstimate(
        origin: GeoPoint,
        destination: GeoPoint,
    ): Result<RouteEstimate> = withContext(Dispatchers.IO) {
        if (!isValidGeoPoint(origin) || !isValidGeoPoint(destination)) {
            return@withContext Result.failure(AppError.Validation("Coordenadas inválidas para calcular la ruta."))
        }
        val url = buildOsrmUrl(origin, destination)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        runCatching {
            publicApiClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 429) {
                    throw AppError.Network("Servicio de rutas ocupado temporalmente.")
                }
                if (!resp.isSuccessful) {
                    throw AppError.Network("No se pudo calcular la ruta en OSRM (HTTP ${resp.code}).")
                }
                parseOsrmRouteResponse(raw)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownOrCurrentLocation(): GeoPoint? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val fresh = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine<GeoPoint?> { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                fusedClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) {
                            cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) })
                        }
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }
        if (fresh != null) return fresh

        return withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine<GeoPoint?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) {
                            cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) })
                        }
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }
    }

    // Moshi DTOs for Photon GeoJSON and OSRM Route API (kept via proguard-rules.pro)
    internal data class PhotonResponse(val features: List<PhotonFeature>? = null)
    internal data class PhotonFeature(
        val geometry: PhotonGeometry? = null,
        val properties: PhotonProperties? = null,
    )
    internal data class PhotonGeometry(val coordinates: List<Double>? = null)
    internal data class PhotonProperties(
        val osm_id: Long? = null,
        val osm_type: String? = null,
        val name: String? = null,
        val street: String? = null,
        val housenumber: String? = null,
        val district: String? = null,
        val locality: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
    )

    internal data class OsrmRouteResponse(
        val code: String? = null,
        val routes: List<OsrmRoute>? = null,
    )
    internal data class OsrmRoute(
        val distance: Double? = null,
        val duration: Double? = null,
    )

    companion object {
        const val MIN_SEARCH_CHARS = 3
        const val SEARCH_DEBOUNCE_MS = 400L
        private const val MAX_QUERY_LENGTH = 120
        private const val MAX_CACHE_ENTRIES = 200

        // Punta Cana bias coordinates for Photon autocomplete
        const val PHOTON_BIAS_LAT = 18.58
        const val PHOTON_BIAS_LNG = -68.40
        const val PHOTON_LIMIT = 8

        private const val USER_AGENT = "Apptividades/1.0 (Android; Punta Cana)"

        private fun isValidGeoPoint(point: GeoPoint): Boolean =
            point.latitude.isFinite() &&
                point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 &&
                point.longitude in -180.0..180.0

        private val moshi: Moshi by lazy {
            Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        }

        fun buildPhotonUrl(query: String): String {
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                .replace("+", "%20")
            return String.format(
                Locale.US,
                "https://photon.komoot.io/api/?q=%s&lat=%.2f&lon=%.2f&limit=%d",
                encoded,
                PHOTON_BIAS_LAT,
                PHOTON_BIAS_LNG,
                PHOTON_LIMIT,
            )
        }

        fun buildOsrmUrl(origin: GeoPoint, destination: GeoPoint): String =
            String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                origin.longitude,
                origin.latitude,
                destination.longitude,
                destination.latitude,
            )

        /**
         * Parses Photon GeoJSON FeatureCollection into (PlacePrediction, ResolvedPlace) pairs.
         * Note: GeoJSON coordinates are [longitude, latitude].
         */
        fun parsePhotonResponse(rawJson: String): List<Pair<PlacePrediction, ResolvedPlace>> {
            val adapter = moshi.adapter(PhotonResponse::class.java)
            val features = adapter.fromJson(rawJson)?.features.orEmpty()
            return buildList {
                features.forEachIndexed { idx, feature ->
                    val coords = feature.geometry?.coordinates
                    if (coords == null || coords.size < 2) return@forEachIndexed
                    val lon = coords[0]
                    val lat = coords[1]
                    if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                        return@forEachIndexed
                    }

                    val props = feature.properties ?: return@forEachIndexed
                    val streetLine = listOfNotNull(
                        props.street?.trim()?.takeIf { it.isNotEmpty() },
                        props.housenumber?.trim()?.takeIf { it.isNotEmpty() },
                    ).joinToString(" ").takeIf { it.isNotEmpty() }

                    val primary = props.name?.trim()?.takeIf { it.isNotEmpty() }
                        ?: streetLine
                        ?: props.locality?.trim()?.takeIf { it.isNotEmpty() }
                        ?: props.city?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@forEachIndexed

                    val secondaryParts = listOfNotNull(
                        streetLine,
                        props.district?.trim()?.takeIf { it.isNotEmpty() },
                        props.locality?.trim()?.takeIf { it.isNotEmpty() },
                        props.city?.trim()?.takeIf { it.isNotEmpty() },
                        props.state?.trim()?.takeIf { it.isNotEmpty() },
                        props.country?.trim()?.takeIf { it.isNotEmpty() },
                    ).distinctBy { it.lowercase() }
                        .filterNot { it.equals(primary, ignoreCase = true) }

                    val secondary = secondaryParts.joinToString(", ")
                    val placeId = if (props.osm_id != null) {
                        "osm_${props.osm_type.orEmpty()}_${props.osm_id}"
                    } else {
                        String.format(Locale.US, "photon_%.5f_%.5f_%d", lat, lon, idx)
                    }

                    val prediction = PlacePrediction(
                        placeId = placeId,
                        primaryText = primary,
                        secondaryText = secondary,
                    )
                    val resolved = ResolvedPlace(
                        placeId = placeId,
                        name = primary,
                        address = secondary.ifBlank { null },
                        latitude = lat,
                        longitude = lon,
                    )
                    add(prediction to resolved)
                }
            }
        }

        /**
         * Parses OSRM /route/v1/driving JSON response into estimated travelMinutes and distanceKm (no traffic).
         */
        fun parseOsrmRouteResponse(rawJson: String): RouteEstimate {
            val adapter = moshi.adapter(OsrmRouteResponse::class.java)
            val parsed = adapter.fromJson(rawJson)
                ?: throw AppError.Network("Respuesta de OSRM vacía.")
            if (!parsed.code.equals("Ok", ignoreCase = true)) {
                throw AppError.Network("OSRM no encontró ruta disponible (${parsed.code ?: "error"}).")
            }
            val firstRoute = parsed.routes?.firstOrNull()
                ?: throw AppError.Network("OSRM no devolvió rutas.")
            val durationSeconds = firstRoute.duration
                ?: throw AppError.Network("Duración de ruta OSRM inválida.")
            if (durationSeconds < 0.0) {
                throw AppError.Network("Duración de ruta OSRM inválida.")
            }
            val distanceKm = firstRoute.distance?.takeIf { it > 0.0 }?.div(1000.0)
            return RouteEstimate(
                travelMinutes = ceil(durationSeconds / 60.0).toInt().coerceAtLeast(1),
                distanceKm = distanceKm,
                fromOsrm = true,
            )
        }
    }
}
