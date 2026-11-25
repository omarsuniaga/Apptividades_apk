package com.example.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.alarms.DepartureAlarmScheduler
import com.example.alarms.DepartureNotificationDispatcher
import com.example.data.local.LocalPhotonPlaceCache
import com.example.data.local.SessionDataStore
import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.data.port.RouteEstimate
import com.example.data.remote.OpenMapsApiClient
import com.example.domain.model.Activity
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SANTO_DOMINGO_ZONE
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalPhotonCacheAndWorkManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (!WorkManager.isInitialized()) {
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
            WorkManager.initialize(context, config)
        }
    }

    @Test
    fun photonCachePersistsCoordinatesAndAvoidsRepeatHttpCalls() = runBlocking {
        val httpCalls = AtomicInteger(0)
        val photonJson = """
            {
              "features": [
                {
                  "geometry": { "coordinates": [-68.3725, 18.5601] },
                  "properties": {
                    "osm_id": 99001,
                    "osm_type": "W",
                    "name": "Hard Rock Hotel Punta Cana",
                    "city": "Punta Cana",
                    "country": "República Dominicana"
                  }
                }
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                httpCalls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(photonJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val cache = LocalPhotonPlaceCache(context)
        val firstApi = OpenMapsApiClient(context, client, cache)

        // 1st search hits Photon once and persists results in DataStore
        val firstRes = firstApi.searchPlaces("Hard Rock Hotel").getOrThrow()
        assertEquals(1, httpCalls.get())
        assertEquals(1, firstRes.size)
        assertEquals("Hard Rock Hotel Punta Cana", firstRes.first().primaryText)

        // 2nd search with a fresh OpenMapsApiClient (simulating app restart) hits DataStore with 0 HTTP calls
        val restartedApi = OpenMapsApiClient(context, client, cache)
        val cachedRes = restartedApi.searchPlaces("  hard rock hotel  ").getOrThrow()
        assertEquals(1, httpCalls.get())
        assertEquals("osm_W_99001", cachedRes.first().placeId)

        // Selecting the place resolves coordinates from DataStore (even with empty RAM cache) and bumps useCount
        val resolved = restartedApi.fetchPlaceDetails("osm_W_99001").getOrThrow()
        assertEquals(18.5601, resolved.latitude, 0.0001)
        assertEquals(-68.3725, resolved.longitude, 0.0001)

        val saved = cache.loadSavedPlaces().first { it.placeId == "osm_W_99001" }
        assertTrue(saved.useCount >= 1)
    }

    @Test
    fun frequentSelectedHotelSkipsPhotonAndWorksOffline() = runBlocking {
        val httpCalls = AtomicInteger(0)
        val offlineClient = OkHttpClient.Builder()
            .addInterceptor {
                httpCalls.incrementAndGet()
                throw IOException("Sin conexión")
            }
            .build()

        val cache = LocalPhotonPlaceCache(context)
        val api = OpenMapsApiClient(context, offlineClient, cache)

        // User selects Club Med Punta Cana once -> saved as frequent hotel in DataStore
        api.rememberSelectedPlace(
            name = "Club Med Punta Cana",
            address = "Playa Blanca, Punta Cana",
            latitude = 18.5542,
            longitude = -68.3688,
            placeId = "osm_W_55501",
        )

        // Prefix search for a frequent hotel returns immediately from DataStore with 0 Photon calls
        val prefixHits = api.searchPlaces("Club Med").getOrThrow()
        assertEquals(0, httpCalls.get())
        assertTrue(prefixHits.any { it.primaryText == "Club Med Punta Cana" })

        // Partial token match while offline falls back cleanly to DataStore cached hotel
        val offlineFallback = api.searchPlaces("Playa Blanca").getOrThrow()
        assertTrue(offlineFallback.any { it.primaryText == "Club Med Punta Cana" })
    }

    @Test
    fun workManagerSchedulesLeaveAndPrepareWorkersFromOsrmTravelTime() = runBlocking {
        val osrmCalls = AtomicInteger(0)
        val fakeMapsPort = object : MapsAndLocationPort {
            override suspend fun searchPlaces(query: String): Result<List<PlacePrediction>> =
                Result.success(emptyList())

            override suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace> =
                Result.failure(UnsupportedOperationException())

            override suspend fun computeRouteEstimate(
                origin: GeoPoint,
                destination: GeoPoint,
            ): Result<RouteEstimate> {
                osrmCalls.incrementAndGet()
                return Result.success(
                    RouteEstimate(travelMinutes = 35, distanceKm = 24.6, fromOsrm = true)
                )
            }

            override suspend fun getLastKnownOrCurrentLocation(): GeoPoint =
                GeoPoint(18.6813, -68.4543)
        }

        val sessionDataStore = SessionDataStore(context)
        val scheduler = DepartureAlarmScheduler(context, sessionDataStore, fakeMapsPort)

        val now = ZonedDateTime.now(SANTO_DOMINGO_ZONE)
        val eventStart = now.plusHours(6)
        val activity = Activity(
            id = "act_workmanager_osrm_1",
            ownerId = "user_1",
            title = "Boda en Secrets Cap Cana",
            eventDate = eventStart.toLocalDate(),
            eventTime = eventStart.toLocalTime().withSecond(0).withNano(0),
            venueName = "Secrets Cap Cana",
            latitude = 18.4552,
            longitude = -68.4041,
            amount = BigDecimal("12000"),
            currency = Currency.DOP,
            paymentStatus = PaymentStatus.PENDIENTE,
        )
        val settings = LabSettings(
            setupMinutes = 45,
            prepMinutes = 60,
            travelBufferMinutes = 15,
            homeLatitude = 18.6813,
            homeLongitude = -68.4543,
        )

        scheduler.syncUpcomingActivities(
            activities = listOf(activity),
            settings = settings,
            overdueSummaryText = null,
        )

        assertEquals(1, osrmCalls.get())

        val stored = sessionDataStore.loadScheduledDepartures()[activity.id]
        assertNotNull(stored)
        assertEquals(35, stored!!.travelMinutes)
        assertTrue(stored.isOsrmEstimated)
        assertTrue(stored.alarmsScheduled)

        val wm = WorkManager.getInstance(context)
        val leaveInfos = wm.getWorkInfosForUniqueWork(
            DepartureAlarmScheduler.departureWorkName(activity.id, DepartureAlarmScheduler.ALARM_TYPE_LEAVE)
        ).get()
        assertTrue(
            "Expected WorkManager LEAVE job to be ENQUEUED",
            leaveInfos.any { it.state == WorkInfo.State.ENQUEUED }
        )

        val prepareInfos = wm.getWorkInfosForUniqueWork(
            DepartureAlarmScheduler.departureWorkName(activity.id, DepartureAlarmScheduler.ALARM_TYPE_PREPARE)
        ).get()
        assertTrue(
            "Expected WorkManager PREPARE job to be ENQUEUED",
            prepareInfos.any { it.state == WorkInfo.State.ENQUEUED }
        )

        val refreshInfos = wm.getWorkInfosForUniqueWork(
            DepartureAlarmScheduler.routeWorkName(activity.id)
        ).get()
        assertTrue(
            "OSRM has no live traffic so redundant T-3h recomputation should not be enqueued",
            refreshInfos.none { it.state == WorkInfo.State.ENQUEUED }
        )

        val periodicInfos = wm.getWorkInfosForUniqueWork(
            DepartureAlarmScheduler.PERIODIC_SYNC_WORK_NAME
        ).get()
        assertTrue(
            "Expected periodic sync WorkManager job to be ENQUEUED",
            periodicInfos.any { it.state == WorkInfo.State.ENQUEUED }
        )

        // Canceling the activity cancels its WorkManager departure & refresh jobs
        scheduler.cancelForActivity(activity.id)
        val afterCancelLeave = wm.getWorkInfosForUniqueWork(
            DepartureAlarmScheduler.departureWorkName(activity.id, DepartureAlarmScheduler.ALARM_TYPE_LEAVE)
        ).get()
        assertTrue(afterCancelLeave.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun departureDispatcherDeduplicatesExactAlarmAndWorkManagerTriggers() = runBlocking {
        val triggerMillis = System.currentTimeMillis() + 60_000L
        val firstDispatch = DepartureNotificationDispatcher.dispatchIfNew(
            context = context,
            activityId = "act_dedup_test",
            alarmType = DepartureAlarmScheduler.ALARM_TYPE_LEAVE,
            venueName = "Barceló Bávaro Palace",
            latitude = 18.6621,
            longitude = -68.4094,
            travelMinutes = 28,
            eventTimeLabel = "8:00 p. m.",
            leaveTimeLabel = "6:32 p. m.",
            arrivalTimeLabel = "19:00",
            triggerEpochMillis = triggerMillis,
        )
        val duplicateDispatch = DepartureNotificationDispatcher.dispatchIfNew(
            context = context,
            activityId = "act_dedup_test",
            alarmType = DepartureAlarmScheduler.ALARM_TYPE_LEAVE,
            venueName = "Barceló Bávaro Palace",
            latitude = 18.6621,
            longitude = -68.4094,
            travelMinutes = 28,
            eventTimeLabel = "8:00 p. m.",
            leaveTimeLabel = "6:32 p. m.",
            arrivalTimeLabel = "19:00",
            triggerEpochMillis = triggerMillis,
        )

        assertTrue("First trigger should dispatch notification", firstDispatch)
        assertFalse("Second trigger with same key must be deduplicated", duplicateDispatch)
    }
}
