package com.example.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.port.LoginHistoryRepository
import com.example.data.port.PrivacyPreferencesPort
import com.example.domain.model.LoginSessionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.loginHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "login_history_store"
)

/**
 * Tracks authentication events (date, time, IP address, device name, Android version, outcome)
 * in app-private DataStore (excluded from cloud backup, never stores credentials or tokens).
 */
class LoginHistoryStore(
    private val context: Context,
    okHttpClient: OkHttpClient,
    private val privacyPreferencesPort: PrivacyPreferencesPort? = null,
) : LoginHistoryRepository {

    private object Keys {
        val EVENTS_JSON = stringPreferencesKey("login_session_events_json")
    }

    // Strict short timeouts so IP resolution never delays sign-in or leaks connections
    private val ipClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    override fun observeLoginHistory(): Flow<List<LoginSessionEvent>> =
        context.loginHistoryDataStore.data.map { prefs ->
            parseEvents(prefs[Keys.EVENTS_JSON])
                .sortedByDescending { it.timestampEpochMillis }
        }

    override suspend fun recordEvent(
        userId: String,
        email: String,
        eventType: String,
        success: Boolean,
    ) = withContext(Dispatchers.IO) {
        val allowExternalIp = runCatching {
            privacyPreferencesPort?.observePrivacyPreferences()?.first()?.resolveExternalIpInHistory
        }.getOrNull() ?: false
        val ip = resolveClientIp(allowExternalLookup = allowExternalIp)
        val device = buildDeviceName()
        val osVersion = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}"
        val event = LoginSessionEvent(
            id = UUID.randomUUID().toString(),
            userId = userId.trim(),
            email = email.trim(),
            timestampEpochMillis = System.currentTimeMillis(),
            ipAddress = ip,
            deviceName = device,
            androidVersion = osVersion,
            eventType = eventType,
            success = success,
        )
        context.loginHistoryDataStore.edit { prefs ->
            val current = parseEvents(prefs[Keys.EVENTS_JSON])
            val updated = (listOf(event) + current).take(MAX_EVENTS)
            prefs[Keys.EVENTS_JSON] = serializeEvents(updated)
        }
        Unit
    }

    override suspend fun clearHistory() {
        context.loginHistoryDataStore.edit { prefs ->
            prefs.remove(Keys.EVENTS_JSON)
        }
    }

    private fun resolveClientIp(allowExternalLookup: Boolean): String {
        if (allowExternalLookup) {
            val publicIp = runCatching {
                val req = Request.Builder()
                    .url("https://api.ipify.org?format=json")
                    .header("Accept", "application/json")
                    .get()
                    .build()
                ipClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    JSONObject(body).optString("ip").trim().takeIf { isValidIpString(it) }
                }
            }.getOrNull()
            if (!publicIp.isNullOrBlank()) return publicIp
        }

        val localIp = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList().asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.mapNotNull { it.hostAddress?.trim() }
                ?.firstOrNull { isValidIpString(it) }
        }.getOrNull()

        return when {
            !localIp.isNullOrBlank() -> localIp
            !allowExternalLookup -> "Omitida (privacidad)"
            else -> "No disponible"
        }
    }

    private fun isValidIpString(value: String): Boolean =
        value.isNotEmpty() && value.length <= 45 && value.all {
            it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F'
        }

    private fun buildDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            manufacturer.isEmpty() && model.isEmpty() -> "Dispositivo Android"
            model.startsWith(manufacturer, ignoreCase = true) -> model.replaceFirstChar { it.uppercase() }
            else -> "${manufacturer.replaceFirstChar { it.uppercase() }} $model".trim()
        }
    }

    private fun parseEvents(raw: String?): List<LoginSessionEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        LoginSessionEvent(
                            id = obj.optString("id"),
                            userId = obj.optString("userId"),
                            email = obj.optString("email"),
                            timestampEpochMillis = obj.optLong("timestampEpochMillis"),
                            ipAddress = obj.optString("ipAddress", "No disponible"),
                            deviceName = obj.optString("deviceName", "Dispositivo Android"),
                            androidVersion = obj.optString("androidVersion", "Android"),
                            eventType = obj.optString("eventType", EVENT_LOGIN),
                            success = obj.optBoolean("success", true),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeEvents(events: List<LoginSessionEvent>): String {
        val arr = JSONArray()
        events.forEach { ev ->
            arr.put(
                JSONObject().apply {
                    put("id", ev.id)
                    put("userId", ev.userId)
                    put("email", ev.email)
                    put("timestampEpochMillis", ev.timestampEpochMillis)
                    put("ipAddress", ev.ipAddress)
                    put("deviceName", ev.deviceName)
                    put("androidVersion", ev.androidVersion)
                    put("eventType", ev.eventType)
                    put("success", ev.success)
                }
            )
        }
        return arr.toString()
    }

    companion object {
        const val EVENT_LOGIN = "LOGIN"
        const val EVENT_SIGNUP = "SIGNUP"
        const val EVENT_SESSION_RESTORED = "SESSION_RESTORED"
        private const val MAX_EVENTS = 50
    }
}
