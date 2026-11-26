package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AesGcmTokenCipher
import com.example.data.local.SessionDataStore
import com.example.domain.model.AuthSession
import com.example.domain.rules.TrackedRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackedRouteStorageTest {

    private lateinit var store: SessionDataStore

    private val route = TrackedRoute(
        venueKey = "clubmed",
        walkMinutes = 15,
        setupMinutes = 10,
        marginMinutes = 15,
        driveMinutesByOrigin = mapOf("home" to 17, "venue:iberostar" to 22),
    )

    private suspend fun signIn(userId: String) {
        store.saveSession(AuthSession(userId, "user@example.test", "access", "refresh", 0L))
    }

    @Before
    fun clean() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        store = SessionDataStore(context, AesGcmTokenCipher(secretKeyProvider = { key }))
        for (user in listOf("routes-one", "routes-two")) {
            signIn(user)
            store.clearTrackedRoutes()
        }
        store.clearSession()
    }

    @Test
    fun `saved routes survive signing out and stay with their account`() = runBlocking {
        signIn("routes-one")
        store.saveTrackedRoute(route.venueKey, route)
        store.clearSession()
        assertTrue(store.loadTrackedRoutes().isEmpty())

        signIn("routes-two")
        assertTrue(store.loadTrackedRoutes().isEmpty())

        signIn("routes-one")
        assertEquals(route, store.loadTrackedRoutes()["clubmed"])
    }

    @Test
    fun `a route can be removed`() = runBlocking {
        signIn("routes-one")
        store.saveTrackedRoute(route.venueKey, route)

        store.saveTrackedRoute(route.venueKey, null)

        assertTrue(store.loadTrackedRoutes().isEmpty())
    }
}
