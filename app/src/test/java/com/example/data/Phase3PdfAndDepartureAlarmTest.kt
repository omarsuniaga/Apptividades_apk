package com.example.data

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.alarms.DepartureAlarmScheduler
import com.example.alarms.DepartureNotificationDispatcher
import com.example.alarms.NotificationHelper
import com.example.data.local.LoginHistoryStore
import com.example.data.local.SessionDataStore
import com.example.data.port.GeoPoint
import com.example.data.port.MapsAndLocationPort
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.data.port.RouteEstimate
import com.example.domain.model.Activity
import com.example.domain.model.BankAccount
import com.example.domain.model.BankAccountType
import com.example.domain.model.Currency
import com.example.domain.model.LabSettings
import com.example.domain.model.PaymentStatus
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.rules.AccountStatementBuilder
import com.example.domain.rules.AccountStatementPdfGenerator
import com.example.domain.rules.DepartureTimeCalculator
import kotlinx.coroutines.flow.first
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.util.ReflectionHelpers
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Robolectric 4.16 JVM shadow for [PdfDocument] backed by real Android [Bitmap] + [Canvas]
 * so [AccountStatementPdfGenerator] executes its full native Paint text-measurement, wrapping,
 * pagination, and Canvas draw pipeline on JVM without needing native libpdfium.
 */
@Implements(PdfDocument::class)
class ShadowRealCanvasPdfDocument {
    private val finishedPages = mutableListOf<PdfDocument.PageInfo>()
    private var closed = false

    @Implementation
    fun __constructor__() {
        closed = false
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        val bitmap = Bitmap.createBitmap(
            pageInfo.pageWidth,
            pageInfo.pageHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        return ReflectionHelpers.callConstructor(
            PdfDocument.Page::class.java,
            ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
            ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo),
        )
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        check(!closed) { "document is closed!" }
        finishedPages.add(page.info)
    }

    @Implementation
    fun getPages(): List<PdfDocument.PageInfo> = finishedPages.toList()

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!closed) { "document is closed!" }
        val stubPdf = buildString {
            append("%PDF-1.4\n")
            append("% Apptividades A4 statement (${finishedPages.size} page(s))\n")
            finishedPages.forEach { info ->
                append("% Page ${info.pageNumber}: ${info.pageWidth}x${info.pageHeight}\n")
            }
            append("%%EOF\n")
        }
        out.write(stubPdf.toByteArray(Charsets.US_ASCII))
    }

    @Implementation
    fun close() {
        closed = true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ShadowRealCanvasPdfDocument::class])
class Phase3PdfAndDepartureAlarmTest {

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        if (!WorkManager.isInitialized()) {
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
            WorkManager.initialize(context, config)
        }
    }

    @Test
    fun pdfGeneratorRenders4GigsAnd2AccountsIntoSingleA4Page() {
        val today = LocalDate.of(2026, 11, 20)
        val activities = listOf(
            Activity(
                id = "gig_1",
                ownerId = "user_1",
                title = "Cóctel de bienvenida",
                description = "Trío de jazz acústico",
                eventDate = LocalDate.of(2026, 8, 4),
                eventTime = LocalTime.of(18, 30),
                supplierName = "José David",
                venueName = "Club Med Punta Cana",
                amount = BigDecimal("5500"),
                currency = Currency.DOP,
                paymentStatus = PaymentStatus.PENDIENTE,
            ),
            Activity(
                id = "gig_2",
                ownerId = "user_1",
                title = "Cena gala",
                description = "Cuarteto de cuerdas",
                eventDate = LocalDate.of(2026, 9, 18),
                eventTime = LocalTime.of(20, 0),
                supplierName = "Jose David",
                venueName = "Secrets Cap Cana",
                amount = BigDecimal("6000"),
                currency = Currency.DOP,
                paymentStatus = PaymentStatus.PENDIENTE,
            ),
            Activity(
                id = "gig_3",
                ownerId = "user_1",
                title = "Ceremonia playa",
                description = null,
                eventDate = LocalDate.of(2026, 10, 14),
                eventTime = LocalTime.of(17, 0),
                supplierName = "José David",
                venueName = "Hard Rock Hotel Punta Cana",
                amount = BigDecimal("5000"),
                currency = Currency.DOP,
                paymentStatus = PaymentStatus.PENDIENTE,
            ),
            Activity(
                id = "gig_4",
                ownerId = "user_1",
                title = "Recepción",
                description = "Dúo violín y guitarra",
                eventDate = LocalDate.of(2026, 11, 8),
                eventTime = LocalTime.of(19, 30),
                supplierName = "José David",
                venueName = "Barceló Bávaro Palace",
                amount = BigDecimal("5500"),
                currency = Currency.DOP,
                paymentStatus = PaymentStatus.PENDIENTE,
            ),
        )

        val accounts = listOf(
            BankAccount(
                id = "acc_1",
                ownerId = "user_1",
                label = "Popular DOP",
                bankName = "Banco Popular Dominicano",
                holderName = "Omar Pineda",
                accountNumber = "800-123456-7",
                accountType = BankAccountType.AHORROS,
                currency = Currency.DOP,
                isDefault = true,
            ),
            BankAccount(
                id = "acc_2",
                ownerId = "user_1",
                label = "BHD DOP",
                bankName = "Banco BHD",
                holderName = "Omar Pineda",
                accountNumber = "112-987654-0",
                accountType = BankAccountType.CORRIENTE,
                currency = Currency.DOP,
                isDefault = false,
            ),
        )

        val statement = AccountStatementBuilder.build(
            supplierName = "José David",
            currency = Currency.DOP,
            activities = activities,
            bankAccounts = accounts,
            today = today,
        )

        val pdf = AccountStatementPdfGenerator.generate(statement)

        assertEquals(
            "4 gigs + 2 bank accounts must fit on a single A4 page",
            1,
            pdf.pageCount,
        )
        assertTrue("Generated PDF bytes should not be empty", pdf.bytes.isNotEmpty())
        val header = String(pdf.bytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", header)
        assertEquals("resumen-de-actividades-jose-david-2026-11-20.pdf", pdf.fileName)
    }

    @Test
    fun pdfGeneratorPaginatesCleanlyForLargeNumberOfGigs() {
        val today = LocalDate.of(2026, 11, 20)
        val manyActivities = (1..24).map { idx ->
            Activity(
                id = "gig_many_$idx",
                ownerId = "user_1",
                title = "Evento #$idx",
                description = "Cuarteto acústico de recepción #$idx",
                eventDate = LocalDate.of(2026, 8, 1).plusDays(idx.toLong() * 3),
                eventTime = LocalTime.of(19, 0),
                supplierName = "José David",
                venueName = "Club Med Punta Cana",
                amount = BigDecimal("5000"),
                currency = Currency.DOP,
                paymentStatus = PaymentStatus.PENDIENTE,
            )
        }
        val statement = AccountStatementBuilder.build(
            supplierName = "José David",
            currency = Currency.DOP,
            activities = manyActivities,
            bankAccounts = emptyList(),
            today = today,
        )
        val pdf = AccountStatementPdfGenerator.generate(statement)
        assertTrue("24 gigs should spill cleanly onto 2+ pages, got ${pdf.pageCount}", pdf.pageCount >= 2)
    }

    @Test
    fun primaryExactAlarmAndWorkManagerFallbackSubtractOsrmAndSetupAndFireLeaveNotification() = runBlocking {
        // Grant Android 13+ POST_NOTIFICATIONS permission in Robolectric
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(NotificationHelper.hasNotificationPermission(context))

        // Verify Android 13+ NotificationChannels are created with IMPORTANCE_HIGH & VISIBILITY_PUBLIC
        NotificationHelper.ensureChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val departureChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_DEPARTURE)
        assertNotNull(departureChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, departureChannel!!.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, departureChannel.lockscreenVisibility)

        // Configure AlarmManager exact alarm permission granted
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        val fakeOsrmTravelMinutes = 40
        val setupMinutes = 45
        val bufferMinutes = 15
        val prepMinutes = 60

        val fakeMapsPort = object : MapsAndLocationPort {
            override suspend fun searchPlaces(query: String): Result<List<PlacePrediction>> =
                Result.success(emptyList())

            override suspend fun fetchPlaceDetails(placeId: String): Result<ResolvedPlace> =
                Result.failure(UnsupportedOperationException())

            override suspend fun computeRouteEstimate(
                origin: GeoPoint,
                destination: GeoPoint,
            ): Result<RouteEstimate> = Result.success(
                RouteEstimate(travelMinutes = fakeOsrmTravelMinutes, distanceKm = 31.4, fromOsrm = true)
            )

            override suspend fun getLastKnownOrCurrentLocation(): GeoPoint =
                GeoPoint(18.6813, -68.4543)
        }

        val sessionDataStore = SessionDataStore(context)
        val scheduler = DepartureAlarmScheduler(context, sessionDataStore, fakeMapsPort)

        val now = ZonedDateTime.now(SANTO_DOMINGO_ZONE)
        val eventStart = now.plusHours(5).truncatedTo(ChronoUnit.MINUTES)
        val destLat = 18.4601
        val destLng = -68.4012
        val activity = Activity(
            id = "act_exact_alarm_verify",
            ownerId = "user_1",
            title = "Cena en Sanctuary Cap Cana",
            eventDate = eventStart.toLocalDate(),
            eventTime = eventStart.toLocalTime(),
            venueName = "Sanctuary Cap Cana",
            latitude = destLat,
            longitude = destLng,
            amount = BigDecimal("14000"),
            currency = Currency.DOP,
            paymentStatus = PaymentStatus.PENDIENTE,
        )
        val settings = LabSettings(
            setupMinutes = setupMinutes,
            prepMinutes = prepMinutes,
            travelBufferMinutes = bufferMinutes,
            homeLatitude = 18.6813,
            homeLongitude = -68.4543,
        )

        scheduler.syncUpcomingActivities(
            activities = listOf(activity),
            settings = settings,
            overdueSummaryText = null,
        )

        // Verify exact departure math aligned with EventTimelineCalculator
        val expectedSchedule = DepartureTimeCalculator.calculateFromTimeline(
            eventDate = activity.eventDate,
            eventTime = activity.eventTime!!,
            travelMinutes = fakeOsrmTravelMinutes,
            settings = settings,
        )
        val expectedLeaveMillis = expectedSchedule.leaveAt.atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()
        val expectedPrepareMillis = expectedSchedule.prepareAt.atZone(SANTO_DOMINGO_ZONE).toInstant().toEpochMilli()

        val stored = sessionDataStore.loadScheduledDepartures()[activity.id]
        assertNotNull(stored)
        assertEquals(expectedLeaveMillis, stored!!.leaveAtEpochMillis)
        assertEquals(expectedPrepareMillis, stored.prepareAtEpochMillis)

        // Verify PRIMARY AlarmManager setAlarmClock is armed for the exact LEAVE timestamp
        val nextAlarmClock = alarmManager.nextAlarmClock
        assertNotNull("Primary AlarmManager setAlarmClock must be scheduled for Sal ahora", nextAlarmClock)
        assertEquals(expectedLeaveMillis, nextAlarmClock!!.triggerTime)

        // Verify FALLBACK WorkManager job is also enqueued with proper departure tag
        val wm = WorkManager.getInstance(context)
        val leaveWork = wm.getWorkInfosForUniqueWork(
            DepartureAlarmScheduler.departureWorkName(activity.id, DepartureAlarmScheduler.ALARM_TYPE_LEAVE)
        ).get()
        assertTrue(leaveWork.any { it.state == WorkInfo.State.ENQUEUED })
        assertTrue(leaveWork.any { DepartureAlarmScheduler.TAG_DEPARTURE_LEAVE in it.tags })

        // Verify dispatching the scheduled alert posts the Android 13+ "Sal ahora" notification
        // with Google Maps + Waze actions and deduplicates a second trigger from the fallback
        val dispatched = DepartureNotificationDispatcher.dispatchIfNew(
            context = context,
            activityId = activity.id,
            alarmType = DepartureAlarmScheduler.ALARM_TYPE_LEAVE,
            venueName = activity.venueName,
            latitude = destLat,
            longitude = destLng,
            travelMinutes = fakeOsrmTravelMinutes,
            eventTimeLabel = "8:00 p. m.",
            leaveTimeLabel = "6:20 p. m.",
            arrivalTimeLabel = "19:15",
            triggerEpochMillis = expectedLeaveMillis,
        )
        assertTrue("Primary trigger should dispatch notification", dispatched)

        val fallbackDuplicate = DepartureNotificationDispatcher.dispatchIfNew(
            context = context,
            activityId = activity.id,
            alarmType = DepartureAlarmScheduler.ALARM_TYPE_LEAVE,
            venueName = activity.venueName,
            latitude = destLat,
            longitude = destLng,
            travelMinutes = fakeOsrmTravelMinutes,
            eventTimeLabel = "8:00 p. m.",
            leaveTimeLabel = "6:20 p. m.",
            arrivalTimeLabel = "19:15",
            triggerEpochMillis = expectedLeaveMillis,
        )
        assertFalse("WorkManager fallback trigger must be deduplicated", fallbackDuplicate)

        val shadowNotifManager = shadowOf(notificationManager)
        val posted = shadowNotifManager.allNotifications
        assertTrue("Expected Sal ahora notification to be posted", posted.isNotEmpty())
        val leaveNotif = posted.last()
        val title = leaveNotif.extras.getString(Notification.EXTRA_TITLE).orEmpty()
        assertTrue("Title should mention Sanctuary Cap Cana: $title", title.contains("Sanctuary Cap Cana"))
        assertEquals(2, leaveNotif.actions?.size ?: 0)
    }

    @Test
    fun android13SuppressesNotificationWhenPostNotificationsPermissionDenied() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(NotificationHelper.hasNotificationPermission(context))

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val beforeCount = shadowOf(notificationManager).allNotifications.size

        NotificationHelper.postLeaveNotification(
            context = context,
            activityId = "act_denied_perm",
            venueName = "Hotel Test",
            latitude = 18.55,
            longitude = -68.37,
            travelMinutes = 25,
            arrivalTimeLabel = "18:45",
        )

        val afterCount = shadowOf(notificationManager).allNotifications.size
        assertEquals(beforeCount, afterCount)
    }

    @Test
    fun loginHistoryStoreRecordsDateTimeIpDeviceAndClearsCleanly() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"ip":"186.149.20.42"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val store = LoginHistoryStore(context, mockClient)
        store.clearHistory()

        store.recordEvent(
            userId = "user-abc",
            email = "omar@apptividades.do",
            eventType = LoginHistoryStore.EVENT_LOGIN,
            success = true,
        )

        val events = store.observeLoginHistory().first()
        assertEquals(1, events.size)
        val ev = events.first()
        assertEquals("omar@apptividades.do", ev.email)
        assertEquals("186.149.20.42", ev.ipAddress)
        assertEquals(LoginHistoryStore.EVENT_LOGIN, ev.eventType)
        assertTrue(ev.success)
        assertTrue(ev.timestampEpochMillis > 0L)
        assertTrue(ev.deviceName.isNotBlank())

        store.clearHistory()
        assertTrue(store.observeLoginHistory().first().isEmpty())
    }
}
