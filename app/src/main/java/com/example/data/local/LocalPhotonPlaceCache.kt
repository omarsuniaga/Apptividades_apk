package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.domain.model.Venue
import com.example.domain.rules.NameNormalizer
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.photonPlacesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "photon_places_cache"
)

/**
 * Persistent local record of a hotel/place discovered via Photon (OpenStreetMap)
 * or pinned on the map, ranked by selection frequency so repeat hotel picks require
 * zero Photon API calls.
 */
data class CachedPhotonPlace(
    val placeId: String,
    val name: String,
    val normalizedKey: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val useCount: Int = 0,
    val lastUsedEpochMillis: Long = 0L,
) {
    fun toPlacePrediction(): PlacePrediction = PlacePrediction(
        placeId = placeId,
        primaryText = name,
        secondaryText = address.orEmpty(),
    )

    fun toResolvedPlace(): ResolvedPlace = ResolvedPlace(
        placeId = placeId,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )

    fun toVenue(): Venue = Venue(
        id = "",
        name = name,
        placeId = placeId,
        address = address,
        latitude = latitude,
        longitude = longitude,
    )
}

internal data class CachedPhotonQueryEntry(
    val normalizedQuery: String,
    val placeIds: List<String>,
    val cachedAtEpochMillis: Long,
)

interface PhotonPlaceCachePort {
    fun observeSavedPlaces(): Flow<List<CachedPhotonPlace>>
    suspend fun loadSavedPlaces(): List<CachedPhotonPlace>
    suspend fun findByPlaceId(placeId: String): CachedPhotonPlace?
    suspend fun findCachedQueryPredictions(
        query: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<PlacePrediction>?
    suspend fun findFrequentMatches(query: String): List<CachedPhotonPlace>
    suspend fun saveSearchResults(
        query: String,
        items: List<Pair<PlacePrediction, ResolvedPlace>>,
        nowEpochMillis: Long = System.currentTimeMillis(),
    )
    suspend fun recordPlaceSelection(
        place: ResolvedPlace,
        nowEpochMillis: Long = System.currentTimeMillis(),
    )
}

/**
 * DataStore-backed local cache for Photon hotel/place coordinates and recent queries.
 * Lives in app-private `files/datastore/photon_places_cache.preferences_pb`
 * (excluded from cloud backup by backup_rules.xml / data_extraction_rules.xml).
 */
class LocalPhotonPlaceCache(
    private val context: Context,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) : PhotonPlaceCachePort {

    private object Keys {
        val PLACES_JSON = stringPreferencesKey("cached_photon_places_json")
        val QUERIES_JSON = stringPreferencesKey("cached_photon_queries_json")
    }

    private val placesListType = Types.newParameterizedType(List::class.java, CachedPhotonPlace::class.java)
    private val placesAdapter = moshi.adapter<List<CachedPhotonPlace>>(placesListType)

    private val queriesListType = Types.newParameterizedType(List::class.java, CachedPhotonQueryEntry::class.java)
    private val queriesAdapter = moshi.adapter<List<CachedPhotonQueryEntry>>(queriesListType)

    override fun observeSavedPlaces(): Flow<List<CachedPhotonPlace>> =
        context.photonPlacesDataStore.data.map { prefs ->
            parsePlaces(prefs[Keys.PLACES_JSON])
                .sortedWith(
                    compareByDescending<CachedPhotonPlace> { it.useCount }
                        .thenByDescending { it.lastUsedEpochMillis }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
        }

    override suspend fun loadSavedPlaces(): List<CachedPhotonPlace> =
        observeSavedPlaces().first()

    override suspend fun findByPlaceId(placeId: String): CachedPhotonPlace? {
        val cleanId = placeId.trim()
        if (cleanId.isEmpty()) return null
        return loadSavedPlaces().firstOrNull { it.placeId == cleanId }
    }

    override suspend fun findCachedQueryPredictions(
        query: String,
        nowEpochMillis: Long,
    ): List<PlacePrediction>? {
        val normQuery = normalizeSearchQuery(query)
        if (normQuery.length < 3) return null

        val prefs = context.photonPlacesDataStore.data.first()
        val queries = parseQueries(prefs[Keys.QUERIES_JSON])
        val entry = queries.firstOrNull { it.normalizedQuery == normQuery } ?: return null
        if (nowEpochMillis - entry.cachedAtEpochMillis > QUERY_TTL_MILLIS) return null

        val placesById = parsePlaces(prefs[Keys.PLACES_JSON]).associateBy { it.placeId }
        val resolved = entry.placeIds.mapNotNull { placesById[it]?.toPlacePrediction() }
        return resolved.takeIf { it.isNotEmpty() }
    }

    override suspend fun findFrequentMatches(query: String): List<CachedPhotonPlace> {
        val normQuery = normalizeSearchQuery(query)
        if (normQuery.length < 3) return emptyList()
        val normKey = NameNormalizer.normalizeKey(query)
        val tokens = normQuery.split(" ").filter { it.length >= 2 }

        return loadSavedPlaces()
            .filter { place ->
                val nameNorm = normalizeSearchQuery(place.name)
                val addrNorm = normalizeSearchQuery(place.address.orEmpty())
                place.useCount > 0 && (
                    place.normalizedKey == normKey ||
                        place.normalizedKey.startsWith(normKey) ||
                        nameNorm.contains(normQuery) ||
                        (tokens.isNotEmpty() && tokens.all { t -> nameNorm.contains(t) || addrNorm.contains(t) })
                    )
            }
            .sortedWith(
                compareByDescending<CachedPhotonPlace> { it.normalizedKey == normKey }
                    .thenByDescending { it.normalizedKey.startsWith(normKey) }
                    .thenByDescending { it.useCount }
                    .thenByDescending { it.lastUsedEpochMillis }
            )
            .take(8)
    }

    override suspend fun saveSearchResults(
        query: String,
        items: List<Pair<PlacePrediction, ResolvedPlace>>,
        nowEpochMillis: Long,
    ) {
        val normQuery = normalizeSearchQuery(query)
        if (normQuery.length < 3 || items.isEmpty()) return

        context.photonPlacesDataStore.edit { prefs ->
            val placesMap = parsePlaces(prefs[Keys.PLACES_JSON])
                .associateBy { it.placeId }
                .toMutableMap()

            val orderedIds = mutableListOf<String>()
            items.forEach { (_, resolved) ->
                if (!isValidCoordinates(resolved.latitude, resolved.longitude)) return@forEach
                val cleanName = resolved.name.trim()
                if (cleanName.isEmpty()) return@forEach

                val existing = placesMap[resolved.placeId]
                val updated = CachedPhotonPlace(
                    placeId = resolved.placeId,
                    name = cleanName,
                    normalizedKey = NameNormalizer.normalizeKey(cleanName),
                    address = resolved.address?.trim()?.ifBlank { null } ?: existing?.address,
                    latitude = resolved.latitude,
                    longitude = resolved.longitude,
                    useCount = existing?.useCount ?: 0,
                    lastUsedEpochMillis = maxOf(existing?.lastUsedEpochMillis ?: 0L, nowEpochMillis),
                )
                placesMap[resolved.placeId] = updated
                orderedIds.add(resolved.placeId)
            }

            val trimmedPlaces = placesMap.values
                .sortedWith(
                    compareByDescending<CachedPhotonPlace> { it.useCount }
                        .thenByDescending { it.lastUsedEpochMillis }
                )
                .take(MAX_CACHED_PLACES)

            val queriesList = parseQueries(prefs[Keys.QUERIES_JSON])
                .filterNot { it.normalizedQuery == normQuery }
                .filter { nowEpochMillis - it.cachedAtEpochMillis <= QUERY_TTL_MILLIS }
                .toMutableList()
            if (orderedIds.isNotEmpty()) {
                queriesList.add(
                    0,
                    CachedPhotonQueryEntry(
                        normalizedQuery = normQuery,
                        placeIds = orderedIds,
                        cachedAtEpochMillis = nowEpochMillis,
                    )
                )
            }
            val trimmedQueries = queriesList.take(MAX_CACHED_QUERIES)

            prefs[Keys.PLACES_JSON] = placesAdapter.toJson(trimmedPlaces)
            prefs[Keys.QUERIES_JSON] = queriesAdapter.toJson(trimmedQueries)
        }
    }

    override suspend fun recordPlaceSelection(
        place: ResolvedPlace,
        nowEpochMillis: Long,
    ) {
        if (!isValidCoordinates(place.latitude, place.longitude)) return
        val cleanName = place.name.trim()
        if (cleanName.isEmpty()) return
        val normKey = NameNormalizer.normalizeKey(cleanName)
        val effectiveId = place.placeId.trim().ifBlank {
            String.format(Locale.US, "local_%s_%.4f_%.4f", normKey.take(24), place.latitude, place.longitude)
        }

        context.photonPlacesDataStore.edit { prefs ->
            val existingList = parsePlaces(prefs[Keys.PLACES_JSON]).toMutableList()
            val matchIndex = existingList.indexOfFirst {
                it.placeId == effectiveId ||
                    (it.normalizedKey == normKey &&
                        kotlin.math.abs(it.latitude - place.latitude) < 0.002 &&
                        kotlin.math.abs(it.longitude - place.longitude) < 0.002)
            }

            val existing = if (matchIndex >= 0) existingList.removeAt(matchIndex) else null
            val updated = CachedPhotonPlace(
                placeId = existing?.placeId ?: effectiveId,
                name = cleanName,
                normalizedKey = normKey,
                address = place.address?.trim()?.ifBlank { null } ?: existing?.address,
                latitude = place.latitude,
                longitude = place.longitude,
                useCount = (existing?.useCount ?: 0) + 1,
                lastUsedEpochMillis = nowEpochMillis,
            )
            existingList.add(0, updated)

            val trimmedPlaces = existingList
                .sortedWith(
                    compareByDescending<CachedPhotonPlace> { it.useCount }
                        .thenByDescending { it.lastUsedEpochMillis }
                )
                .take(MAX_CACHED_PLACES)

            prefs[Keys.PLACES_JSON] = placesAdapter.toJson(trimmedPlaces)
        }
    }

    private fun parsePlaces(raw: String?): List<CachedPhotonPlace> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { placesAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    private fun parseQueries(raw: String?): List<CachedPhotonQueryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { queriesAdapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    companion object {
        const val MAX_CACHED_PLACES = 200
        const val MAX_CACHED_QUERIES = 80
        const val QUERY_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L // 7 days

        fun normalizeSearchQuery(raw: String): String =
            java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
                .trim()

        fun isStrongFrequentMatch(query: String, matches: List<CachedPhotonPlace>): Boolean {
            if (matches.isEmpty()) return false
            val normKey = NameNormalizer.normalizeKey(query)
            if (normKey.length < 4) return false
            return matches.any { place ->
                place.useCount > 0 && (
                    place.normalizedKey == normKey ||
                        (normKey.length >= 5 && place.normalizedKey.startsWith(normKey))
                    )
            }
        }

        private fun isValidCoordinates(lat: Double, lng: Double): Boolean =
            lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    }
}
