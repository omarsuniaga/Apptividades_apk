package com.example.data

import com.example.data.local.SessionStorage
import com.example.data.remote.SupabaseAuthErrorMapper
import com.example.data.remote.SupabaseAuthRepository
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseHttpClient
import com.example.domain.model.AppError
import com.example.domain.model.AuthSession
import com.example.domain.model.SignUpOutcome
import com.example.domain.rules.AuthInputValidator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SupabaseAuthFlowTest {

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val fullUrl: String,
        val redirectTo: String?,
        val authorization: String?,
        val apiKey: String?,
        val body: String,
    )

    private class FakeSessionStorage(
        @Volatile var stored: AuthSession? = null,
    ) : SessionStorage {
        private val clearCounter = AtomicInteger(0)
        private val saveCounter = AtomicInteger(0)

        val clearCount: Int get() = clearCounter.get()
        val saveCount: Int get() = saveCounter.get()

        override suspend fun loadSession(): AuthSession? = stored

        override suspend fun saveSession(session: AuthSession) {
            saveCounter.incrementAndGet()
            stored = session
        }

        override suspend fun clearSession() {
            clearCounter.incrementAndGet()
            stored = null
        }
    }

    private class FakeHttpHandler(
        private val responder: (RecordedRequest) -> FakeHttpReply,
    ) : Interceptor {
        val requests = CopyOnWriteArrayList<RecordedRequest>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val bodyString = readRequestBody(req)
            val recorded = RecordedRequest(
                method = req.method,
                path = req.url.encodedPath,
                fullUrl = req.url.toString(),
                redirectTo = req.url.queryParameter("redirect_to"),
                authorization = req.header("Authorization"),
                apiKey = req.header("apikey"),
                body = bodyString,
            )
            requests.add(recorded)

            val reply = responder(recorded)
            if (reply.throwTimeout) {
                throw SocketTimeoutException("Simulated socket timeout")
            }
            if (reply.throwIoException) {
                throw IOException("Simulated offline network")
            }

            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(reply.code)
                .message(if (reply.code in 200..299) "OK" else "Error")
                .body(reply.body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        private fun readRequestBody(request: Request): String {
            val body = request.body ?: return ""
            val buffer = Buffer()
            body.writeTo(buffer)
            return buffer.readUtf8()
        }
    }

    private data class FakeHttpReply(
        val code: Int = 200,
        val body: String = "{}",
        val throwIoException: Boolean = false,
        val throwTimeout: Boolean = false,
    )

    private fun buildTestHarness(
        initialClockMillis: Long = 1_760_000_000_000L,
        initialSession: AuthSession? = null,
        sharedStorage: FakeSessionStorage? = null,
        refreshMutex: Mutex = Mutex(),
        responder: (RecordedRequest) -> FakeHttpReply,
    ): Harness {
        var nowMs = initialClockMillis
        val handler = FakeHttpHandler(responder)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(handler)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val httpClient = SupabaseHttpClient(
            okHttpClient = okHttp,
            moshi = moshi,
            apiKeyProvider = { "test-publishable-key" },
            isConfiguredProvider = { true },
        )
        val storage = sharedStorage ?: FakeSessionStorage(stored = initialSession)
        val repo = SupabaseAuthRepository(
            httpClient = httpClient,
            sessionDataStore = storage,
            clockMillis = { nowMs },
            isConfiguredProvider = { true },
            refreshMutex = refreshMutex,
        )
        return Harness(
            repo = repo,
            httpClient = httpClient,
            storage = storage,
            handler = handler,
            advanceClockMs = { delta -> nowMs += delta },
        )
    }

    private data class Harness(
        val repo: SupabaseAuthRepository,
        val httpClient: SupabaseHttpClient,
        val storage: FakeSessionStorage,
        val handler: FakeHttpHandler,
        val advanceClockMs: (Long) -> Unit,
    )

    @Test
    fun signUpWithSessionAuthenticatesImmediatelyAndUsesNetlifyLoginRedirect() = runBlocking {
        val harness = buildTestHarness { req ->
            assertEquals("POST", req.method)
            assertEquals("/auth/v1/signup", req.path)
            FakeHttpReply(
                code = 200,
                body = """
                    {
                      "access_token": "jwt_access_123",
                      "refresh_token": "jwt_refresh_456",
                      "expires_in": 3600,
                      "user": { "id": "user-uuid-1", "email": "musico@example.com" }
                    }
                """.trimIndent(),
            )
        }

        val result = harness.repo.signUp("musico@example.com", "Musica2026")
        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(outcome is SignUpOutcome.Authenticated)
        val session = (outcome as SignUpOutcome.Authenticated).session
        assertEquals("user-uuid-1", session.userId)
        assertEquals("musico@example.com", session.email)
        assertEquals("jwt_access_123", session.accessToken)
        assertEquals(session, harness.repo.sessionFlow.value)
        assertEquals(session, harness.storage.stored)

        val recorded = harness.handler.requests.single()
        assertEquals(SupabaseConfig.LOGIN_REDIRECT, recorded.redirectTo)
        assertFalse("redirect_to must never contain localhost", recorded.fullUrl.contains("localhost", ignoreCase = true))
        assertTrue(recorded.body.contains("musico@example.com"))
    }

    @Test
    fun signUpWithoutSessionRequiresEmailConfirmationAndNeverUsesLocalhost() = runBlocking {
        val harness = buildTestHarness { _ ->
            FakeHttpReply(
                code = 200,
                body = """
                    {
                      "id": "user-uuid-2",
                      "email": "nuevo@example.com",
                      "confirmation_sent_at": "2026-11-20T21:50:00Z",
                      "identities": [
                        { "id": "id-1", "provider": "email" }
                      ]
                    }
                """.trimIndent(),
            )
        }

        val result = harness.repo.signUp("nuevo@example.com", "Musica2026")
        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(outcome is SignUpOutcome.EmailConfirmationRequired)
        assertEquals("nuevo@example.com", (outcome as SignUpOutcome.EmailConfirmationRequired).email)
        assertNull(harness.repo.sessionFlow.value)
        assertNull(harness.storage.stored)

        val recorded = harness.handler.requests.single()
        assertEquals("/auth/v1/signup", recorded.path)
        assertEquals("https://apptividades-lab.netlify.app/login", recorded.redirectTo)
        assertFalse(recorded.fullUrl.contains("localhost", ignoreCase = true))

        assertEquals(
            "Te enviamos un correo. Ábrelo una sola vez, pulsa el enlace y vuelve aquí para iniciar sesión.",
            SupabaseAuthErrorMapper.SIGNUP_CONFIRMATION_MESSAGE,
        )
    }

    @Test
    fun signUpWithAlreadyConfirmedEmailEmptyIdentitiesReturnsAlreadyRegistered() = runBlocking {
        // GoTrue returns HTTP 200 with identities: [] and sends NO email for existing confirmed users
        val harness = buildTestHarness { _ ->
            FakeHttpReply(
                code = 200,
                body = """
                    {
                      "id": "fake-uuid",
                      "email": "omareduardo74@gmail.com",
                      "confirmation_sent_at": "2026-11-20T21:50:00Z",
                      "identities": []
                    }
                """.trimIndent(),
            )
        }

        val result = harness.repo.signUp("omareduardo74@gmail.com", "Musica2026")
        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is AppError.Validation)
        assertEquals(SupabaseAuthErrorMapper.MSG_ALREADY_REGISTERED, err?.message)
        assertNull(harness.repo.sessionFlow.value)
    }

    @Test
    fun resendConfirmationUsesSignupTypeLoginRedirectAndEnforces60SecondCooldown() = runBlocking {
        val harness = buildTestHarness { req ->
            when (req.path) {
                "/auth/v1/token" -> FakeHttpReply(
                    code = 400,
                    body = """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}""",
                )
                "/auth/v1/resend" -> FakeHttpReply(code = 200, body = "{}")
                else -> error("Unexpected path: ${req.path}")
            }
        }

        // Login returns EmailNotConfirmed so the UI enables the resend button
        val loginResult = harness.repo.signIn("sinconfirmar@example.com", "Musica2026")
        assertTrue(loginResult.isFailure)
        assertTrue(loginResult.exceptionOrNull() is AppError.EmailNotConfirmed)

        // First resend succeeds
        val firstResend = harness.repo.resendConfirmation("sinconfirmar@example.com")
        assertTrue(firstResend.isSuccess)
        assertEquals(60, harness.repo.remainingResendCooldownSeconds())

        val resendReq = harness.handler.requests.last()
        assertEquals("POST", resendReq.method)
        assertEquals("/auth/v1/resend", resendReq.path)
        assertEquals("https://apptividades-lab.netlify.app/login", resendReq.redirectTo)
        assertFalse(resendReq.fullUrl.contains("localhost", ignoreCase = true))
        assertTrue(resendReq.body.contains("\"type\":\"signup\""))
        assertTrue(resendReq.body.contains("\"email\":\"sinconfirmar@example.com\""))

        // Second resend within cooldown is blocked locally with 0 extra HTTP requests
        harness.advanceClockMs(20_000L)
        assertEquals(40, harness.repo.remainingResendCooldownSeconds())
        val countBeforeBlocked = harness.handler.requests.size
        val blockedResend = harness.repo.resendConfirmation("sinconfirmar@example.com")
        assertTrue(blockedResend.isFailure)
        assertTrue(blockedResend.exceptionOrNull() is AppError.RateLimited)
        assertEquals(countBeforeBlocked, harness.handler.requests.size)

        // After full 60s elapse, resend is allowed again
        harness.advanceClockMs(40_000L)
        assertEquals(0, harness.repo.remainingResendCooldownSeconds())
        val secondResend = harness.repo.resendConfirmation("sinconfirmar@example.com")
        assertTrue(secondResend.isSuccess)
        assertEquals(countBeforeBlocked + 1, harness.handler.requests.size)
    }

    @Test
    fun recoverPasswordAlwaysSucceedsNeutrallyAndUsesNetlifyResetRedirect() = runBlocking {
        // Case 1: existing email (200)
        val existingHarness = buildTestHarness { _ -> FakeHttpReply(code = 200, body = "{}") }
        val existingResult = existingHarness.repo.resetPassword("existe@example.com")
        assertTrue(existingResult.isSuccess)

        val existingReq = existingHarness.handler.requests.single()
        assertEquals("POST", existingReq.method)
        assertEquals("/auth/v1/recover", existingReq.path)
        assertEquals("https://apptividades-lab.netlify.app/reset-password", existingReq.redirectTo)
        assertFalse(existingReq.fullUrl.contains("localhost", ignoreCase = true))
        assertFalse(existingReq.body.contains("localhost", ignoreCase = true))

        // Case 2: non-existing email (server returns 404/400) -> still neutral success
        val missingHarness = buildTestHarness { _ ->
            FakeHttpReply(
                code = 404,
                body = """{"error_code":"user_not_found","msg":"User not found"}""",
            )
        }
        val missingResult = missingHarness.repo.resetPassword("noexiste@example.com")
        assertTrue(
            "Recover must return neutral success so account existence is not revealed",
            missingResult.isSuccess,
        )

        assertTrue(
            SupabaseAuthErrorMapper.RECOVER_NEUTRAL_MESSAGE.contains(
                "Si existe una cuenta, recibirás instrucciones"
            )
        )
        assertEquals(
            "El enlace se abre en el navegador; ahí eliges la nueva contraseña y regresas a la app.",
            SupabaseAuthErrorMapper.RECOVER_BROWSER_HINT,
        )
    }

    @Test
    fun updatePasswordSendsPutWithAccessTokenAndRefreshesOrSignsOutOn401() = runBlocking {
        val initialSession = AuthSession(
            userId = "u-1",
            email = "musico@example.com",
            accessToken = "old_access_token",
            refreshToken = "valid_refresh_token",
            expiresAtEpochSeconds = 1_760_003_600L, // not expired locally
        )

        // Sub-case A: PUT returns 401 -> refreshes token -> retries PUT -> succeeds
        var putAttempt = 0
        val refreshHarness = buildTestHarness(initialSession = initialSession) { req ->
            when {
                req.path == "/auth/v1/user" && req.method == "PUT" -> {
                    putAttempt++
                    if (putAttempt == 1) {
                        FakeHttpReply(code = 401, body = """{"msg":"JWT expired"}""")
                    } else {
                        FakeHttpReply(code = 200, body = """{"id":"u-1","email":"musico@example.com"}""")
                    }
                }
                req.path == "/auth/v1/token" -> FakeHttpReply(
                    code = 200,
                    body = """
                        {
                          "access_token": "new_access_token",
                          "refresh_token": "new_refresh_token",
                          "expires_in": 3600,
                          "user": { "id": "u-1", "email": "musico@example.com" }
                        }
                    """.trimIndent(),
                )
                else -> error("Unexpected request: ${req.method} ${req.path}")
            }
        }
        refreshHarness.repo.restoreSession()

        val updateResult = refreshHarness.repo.updatePassword("NuevaClave2026")
        assertTrue(updateResult.isSuccess)
        assertEquals(2, putAttempt)
        assertEquals("new_access_token", refreshHarness.repo.sessionFlow.value?.accessToken)
        val retryPut = refreshHarness.handler.requests.last()
        assertEquals("Bearer new_access_token", retryPut.authorization)
        assertTrue(retryPut.body.contains("\"password\":\"NuevaClave2026\""))

        // Sub-case B: PUT returns 401 and refresh also fails with 400/401 -> signs out cleanly
        val unrefreshableHarness = buildTestHarness(initialSession = initialSession) { req ->
            when {
                req.path == "/auth/v1/user" -> FakeHttpReply(code = 401, body = """{"msg":"Invalid JWT"}""")
                req.path == "/auth/v1/token" -> FakeHttpReply(
                    code = 400,
                    body = """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}""",
                )
                req.path == "/auth/v1/logout" -> FakeHttpReply(code = 204, body = "")
                else -> FakeHttpReply(code = 200, body = "{}")
            }
        }
        unrefreshableHarness.repo.restoreSession()
        assertNotNull(unrefreshableHarness.repo.sessionFlow.value)

        val failedUpdate = unrefreshableHarness.repo.updatePassword("NuevaClave2026")
        assertTrue(failedUpdate.isFailure)
        assertTrue(failedUpdate.exceptionOrNull() is AppError.Unauthorized)
        assertNull("Session must be cleared on unrefreshable 401", unrefreshableHarness.repo.sessionFlow.value)
        assertNull("Persisted session must be cleared on unrefreshable 401", unrefreshableHarness.storage.stored)
        assertTrue(unrefreshableHarness.storage.clearCount >= 1)
    }

    @Test
    fun spanishErrorMappingCoversAllCasesAndNeverLeaksEnglishOrTokens() = runBlocking {
        // 1. Invalid credentials
        val invalidCreds = buildTestHarness {
            FakeHttpReply(
                code = 400,
                body = """{"error":"invalid_grant","error_description":"Invalid login credentials"}""",
            )
        }.repo.signIn("a@example.com", "WrongPass1")
        val invalidErr = invalidCreds.exceptionOrNull()
        assertTrue(invalidErr is AppError.Unauthorized)
        assertEquals(SupabaseAuthErrorMapper.MSG_INVALID_CREDENTIALS, invalidErr?.message)

        // 2. Email not confirmed
        val unconfirmed = buildTestHarness {
            FakeHttpReply(
                code = 400,
                body = """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}""",
            )
        }.repo.signIn("a@example.com", "Musica2026")
        val unconfirmedErr = unconfirmed.exceptionOrNull()
        assertTrue(unconfirmedErr is AppError.EmailNotConfirmed)
        assertFalse(unconfirmedErr?.message.orEmpty().contains("Email not confirmed"))

        // 3. Already registered
        val duplicate = buildTestHarness {
            FakeHttpReply(
                code = 422,
                body = """{"error_code":"user_already_exists","msg":"User already registered"}""",
            )
        }.repo.signUp("a@example.com", "Musica2026")
        val duplicateErr = duplicate.exceptionOrNull()
        assertTrue(duplicateErr is AppError.Validation)
        assertEquals(SupabaseAuthErrorMapper.MSG_ALREADY_REGISTERED, duplicateErr?.message)

        // 4. Rate limit / too many requests -> includes "espera un minuto"
        val rateLimited = buildTestHarness {
            FakeHttpReply(
                code = 429,
                body = """{"error_code":"over_email_send_rate_limit","msg":"Too many requests"}""",
            )
        }.repo.signUp("a@example.com", "Musica2026")
        val rateErr = rateLimited.exceptionOrNull()
        assertTrue(rateErr is AppError.RateLimited)
        assertTrue(
            "Rate limit message should advise waiting one minute",
            rateErr?.message.orEmpty().lowercase().contains("espera un minuto"),
        )

        // 5. Weak password
        val weak = buildTestHarness {
            FakeHttpReply(
                code = 422,
                body = """{"error_code":"weak_password","msg":"Password should be at least 8 characters"}""",
            )
        }.repo.signUp("a@example.com", "weakpass1")
        val weakErr = weak.exceptionOrNull()
        assertTrue(weakErr is AppError.Validation)
        assertEquals(SupabaseAuthErrorMapper.MSG_WEAK_PASSWORD, weakErr?.message)

        // 6. No network
        val offline = buildTestHarness {
            FakeHttpReply(throwIoException = true)
        }.repo.signIn("a@example.com", "Musica2026")
        val offlineErr = offline.exceptionOrNull()
        assertTrue(offlineErr is AppError.Network)
        assertEquals(SupabaseAuthErrorMapper.MSG_NO_NETWORK, offlineErr?.message)

        // 7. Unknown raw English error with token-like payload -> NEVER leaked
        val secretJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secret_payload.signature"
        val unknown = buildTestHarness {
            FakeHttpReply(
                code = 500,
                body = """{"msg":"Internal GoTrue crash for token $secretJwt"}""",
            )
        }.repo.signIn("a@example.com", "Musica2026")
        val unknownMsg = unknown.exceptionOrNull()?.message.orEmpty()
        assertEquals(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH, unknownMsg)
        assertFalse(unknownMsg.contains(secretJwt))
        assertFalse(unknownMsg.contains("GoTrue"))
    }

    @Test
    fun passwordValidationAndConfigRedirectConstantsMatchSpec() {
        // Redirect constants never localhost
        assertEquals("https://apptividades-lab.netlify.app/login", SupabaseConfig.LOGIN_REDIRECT)
        assertEquals("https://apptividades-lab.netlify.app/reset-password", SupabaseConfig.RESET_PASSWORD_REDIRECT)
        assertFalse(SupabaseConfig.LOGIN_REDIRECT.contains("localhost", ignoreCase = true))
        assertFalse(SupabaseConfig.RESET_PASSWORD_REDIRECT.contains("localhost", ignoreCase = true))

        // Password rules: min 8 chars, letters + numbers, confirm match
        assertNotNull(AuthInputValidator.validatePassword("abc1234", "abc1234")) // 7 chars
        assertNotNull(AuthInputValidator.validatePassword("abcdefgh", "abcdefgh")) // no digits
        assertNotNull(AuthInputValidator.validatePassword("12345678", "12345678")) // no letters
        assertNotNull(AuthInputValidator.validatePassword("musica2026", "musica2027")) // mismatch
        assertNull(AuthInputValidator.validatePassword("musica2026", "musica2026")) // valid lowercase + digits
        assertNull(AuthInputValidator.validatePasswordChange("Clave2026", "Clave2026"))
    }

    @Test
    fun offlineTimeout429And5xxRefreshKeepSessionAndAllowLaterRetry() = runBlocking {
        val expiredSession = AuthSession(
            userId = "u-offline",
            email = "musico@example.com",
            accessToken = "expired_access_token",
            refreshToken = "good_refresh_token",
            expiresAtEpochSeconds = 1_759_999_000L, // expired relative to 1_760_000_000
        )

        var mode = "offline"
        val harness = buildTestHarness(initialSession = expiredSession) { _ ->
            when (mode) {
                "offline" -> FakeHttpReply(throwIoException = true)
                "timeout" -> FakeHttpReply(throwTimeout = true)
                "429" -> FakeHttpReply(code = 429, body = """{"msg":"Too many requests"}""")
                "503" -> FakeHttpReply(code = 503, body = """{"msg":"Service unavailable"}""")
                "generic_401" -> FakeHttpReply(code = 401, body = """{"msg":"Upstream gateway error"}""")
                else -> FakeHttpReply(
                    code = 200,
                    body = """
                        {
                          "access_token": "recovered_access_token",
                          "refresh_token": "recovered_refresh_token",
                          "expires_in": 3600,
                          "user": { "id": "u-offline", "email": "musico@example.com" }
                        }
                    """.trimIndent(),
                )
            }
        }

        // 1. Offline during restoreSession() -> keeps session intact
        harness.repo.restoreSession()
        assertEquals(expiredSession, harness.repo.sessionFlow.value)
        assertEquals(expiredSession, harness.storage.stored)
        assertEquals(0, harness.storage.clearCount)

        // 2. SocketTimeoutException -> keeps session intact
        mode = "timeout"
        val timeoutRes = harness.repo.forceRefreshSession()
        assertTrue(timeoutRes.isFailure)
        assertTrue(timeoutRes.exceptionOrNull() is AppError.Network)
        assertEquals(expiredSession, harness.repo.sessionFlow.value)
        assertEquals(expiredSession, harness.storage.stored)
        assertEquals(0, harness.storage.clearCount)

        // 3. HTTP 429 -> keeps session intact
        mode = "429"
        val rateLimitRes = harness.repo.forceRefreshSession()
        assertTrue(rateLimitRes.isFailure)
        assertTrue(rateLimitRes.exceptionOrNull() is AppError.RateLimited)
        assertEquals(expiredSession, harness.repo.sessionFlow.value)
        assertEquals(expiredSession, harness.storage.stored)
        assertEquals(0, harness.storage.clearCount)

        // 4. HTTP 503 (5xx) -> keeps session intact
        mode = "503"
        val serverErrRes = harness.repo.forceRefreshSession()
        assertTrue(serverErrRes.isFailure)
        assertTrue(serverErrRes.exceptionOrNull() is AppError.Network)
        assertEquals(expiredSession, harness.repo.sessionFlow.value)
        assertEquals(expiredSession, harness.storage.stored)
        assertEquals(0, harness.storage.clearCount)

        // 5. Generic 401 WITHOUT invalid_grant / refresh_token_not_found -> keeps session intact
        mode = "generic_401"
        val generic401Res = harness.repo.forceRefreshSession()
        assertTrue(generic401Res.isFailure)
        assertEquals(expiredSession, harness.repo.sessionFlow.value)
        assertEquals(expiredSession, harness.storage.stored)
        assertEquals(0, harness.storage.clearCount)

        // 6. Once network recovers, retry succeeds with preserved refresh token
        mode = "online"
        val recoveredRes = harness.repo.forceRefreshSession()
        assertTrue(recoveredRes.isSuccess)
        assertEquals("recovered_access_token", recoveredRes.getOrThrow().accessToken)
        assertEquals("recovered_access_token", harness.repo.sessionFlow.value?.accessToken)
        assertEquals("recovered_refresh_token", harness.storage.stored?.refreshToken)
    }

    @Test
    fun explicitRefreshTokenRejectionClearsSessionOnlyForInvalidGrantOrRefreshTokenNotFound() = runBlocking {
        val session = AuthSession(
            userId = "u-revoked",
            email = "musico@example.com",
            accessToken = "old_access",
            refreshToken = "revoked_refresh",
            expiresAtEpochSeconds = 1_760_003_600L,
        )

        // Case A: HTTP 400 with invalid_grant clears session
        val invalidGrantHarness = buildTestHarness(initialSession = session) {
            FakeHttpReply(
                code = 400,
                body = """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}""",
            )
        }
        invalidGrantHarness.repo.restoreSession()
        val resA = invalidGrantHarness.repo.forceRefreshSession()
        assertTrue(resA.isFailure)
        val errA = resA.exceptionOrNull() as? AppError.Unauthorized
        assertNotNull(errA)
        assertTrue(errA!!.refreshTokenRejected)
        assertNull(invalidGrantHarness.repo.sessionFlow.value)
        assertNull(invalidGrantHarness.storage.stored)
        assertEquals(1, invalidGrantHarness.storage.clearCount)

        // Case B: HTTP 401 with refresh_token_not_found clears session
        val notFoundHarness = buildTestHarness(initialSession = session) {
            FakeHttpReply(
                code = 401,
                body = """{"error_code":"refresh_token_not_found","msg":"Invalid Refresh Token: Refresh Token Not Found"}""",
            )
        }
        notFoundHarness.repo.restoreSession()
        val resB = notFoundHarness.repo.forceRefreshSession()
        assertTrue(resB.isFailure)
        val errB = resB.exceptionOrNull() as? AppError.Unauthorized
        assertNotNull(errB)
        assertTrue(errB!!.refreshTokenRejected)
        assertNull(notFoundHarness.repo.sessionFlow.value)
        assertNull(notFoundHarness.storage.stored)
        assertEquals(1, notFoundHarness.storage.clearCount)
    }

    @Test
    fun twoConcurrentRefreshesHitNetworkOnceAndReuseNewTokenAcrossUiAndWorkManager() = runBlocking {
        val expiredSession = AuthSession(
            userId = "u-concurrent",
            email = "musico@example.com",
            accessToken = "stale_access_token",
            refreshToken = "rotating_refresh_token",
            expiresAtEpochSeconds = 1_759_999_000L, // expired
        )

        // Sub-test 1: Two concurrent refreshes on the same repository (UI + background coroutine)
        val refreshNetworkHits = AtomicInteger(0)
        val firstRequestStarted = CountDownLatch(1)
        val releaseFirstRequest = CountDownLatch(1)

        val sharedStorage = FakeSessionStorage(stored = expiredSession)
        val sharedMutex = Mutex()

        val responder: (RecordedRequest) -> FakeHttpReply = { req ->
            if (req.path == "/auth/v1/token") {
                val hit = refreshNetworkHits.incrementAndGet()
                firstRequestStarted.countDown()
                // Hold the first refresh in flight briefly so the second caller is guaranteed to queue on the Mutex
                releaseFirstRequest.await(2, TimeUnit.SECONDS)
                FakeHttpReply(
                    code = 200,
                    body = """
                        {
                          "access_token": "fresh_access_token_$hit",
                          "refresh_token": "fresh_refresh_token_$hit",
                          "expires_in": 3600,
                          "user": { "id": "u-concurrent", "email": "musico@example.com" }
                        }
                    """.trimIndent(),
                )
            } else {
                FakeHttpReply(code = 200, body = "{}")
            }
        }

        // Simulate UI repository and WorkManager repository sharing the single Mutex and SessionStorage
        val uiHarness = buildTestHarness(
            sharedStorage = sharedStorage,
            refreshMutex = sharedMutex,
            responder = responder,
        )
        val workerHarness = buildTestHarness(
            sharedStorage = sharedStorage,
            refreshMutex = sharedMutex,
            responder = responder,
        )

        val uiCall = async(Dispatchers.Default) { uiHarness.repo.getValidAccessToken() }
        assertTrue("First refresh should reach fake HTTP layer", firstRequestStarted.await(2, TimeUnit.SECONDS))

        // While UI refresh is still in flight, WorkManager triggers refresh on the shared Mutex
        val workerCall = async(Dispatchers.Default) { workerHarness.repo.getValidAccessToken() }
        // Give workerCall a moment to suspend on the shared Mutex, then release HTTP response
        Thread.sleep(50)
        releaseFirstRequest.countDown()

        val (uiTokenResult, workerTokenResult) = awaitAll(uiCall, workerCall)
        assertTrue(uiTokenResult.isSuccess)
        assertTrue(workerTokenResult.isSuccess)
        assertEquals("fresh_access_token_1", uiTokenResult.getOrThrow())
        assertEquals("fresh_access_token_1", workerTokenResult.getOrThrow())
        assertEquals("Concurrent UI + WorkManager refreshes must hit /auth/v1/token only once", 1, refreshNetworkHits.get())

        // Sub-test 2: Two concurrent authenticated API calls that both get 401 refresh only once
        val apiSession = AuthSession(
            userId = "u-concurrent",
            email = "musico@example.com",
            accessToken = "revoked_short_lived_jwt",
            refreshToken = "refresh_for_401",
            expiresAtEpochSeconds = 1_760_003_600L, // not expired locally, server rejects with 401
        )
        val apiRefreshHits = AtomicInteger(0)
        val bothApi401Seen = CountDownLatch(2)

        val apiHarness = buildTestHarness(initialSession = apiSession) { req ->
            when (req.path) {
                "/rest/v1/lab_activities" -> {
                    if (req.authorization == "Bearer revoked_short_lived_jwt") {
                        bothApi401Seen.countDown()
                        bothApi401Seen.await(2, TimeUnit.SECONDS)
                        FakeHttpReply(code = 401, body = """{"message":"JWT expired"}""")
                    } else {
                        assertEquals("Bearer rotated_jwt_1", req.authorization)
                        FakeHttpReply(code = 200, body = "[]")
                    }
                }
                "/auth/v1/token" -> {
                    val hit = apiRefreshHits.incrementAndGet()
                    Thread.sleep(60)
                    FakeHttpReply(
                        code = 200,
                        body = """
                            {
                              "access_token": "rotated_jwt_$hit",
                              "refresh_token": "rotated_refresh_$hit",
                              "expires_in": 3600,
                              "user": { "id": "u-concurrent", "email": "musico@example.com" }
                            }
                        """.trimIndent(),
                    )
                }
                else -> FakeHttpReply(code = 200, body = "{}")
            }
        }
        apiHarness.repo.restoreSession()

        val url = apiHarness.httpClient.baseUrl.newBuilder()
            .addPathSegments("rest/v1/lab_activities")
            .build()
        val req1 = async(Dispatchers.Default) {
            apiHarness.httpClient.executeAuthenticated(apiHarness.repo, "GET", url)
        }
        val req2 = async(Dispatchers.Default) {
            apiHarness.httpClient.executeAuthenticated(apiHarness.repo, "GET", url)
        }
        val (r1, r2) = awaitAll(req1, req2)
        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)
        assertEquals("Two concurrent 401 API calls must trigger exactly one refresh", 1, apiRefreshHits.get())
    }

    @Test
    fun refreshTokenAlreadyUsedClearsSessionWithExpiredMessage() = runBlocking {
        assertExplicitRejectionCodeClearsSession(
            statusCode = 400,
            errorCode = "refresh_token_already_used",
            msg = "Invalid Refresh Token: Already Used",
        )
    }

    @Test
    fun sessionNotFoundClearsSessionWithExpiredMessage() = runBlocking {
        assertExplicitRejectionCodeClearsSession(
            statusCode = 401,
            errorCode = "session_not_found",
            msg = "Session from session_id claim in JWT does not exist",
        )
    }

    @Test
    fun sessionExpiredClearsSessionWithExpiredMessage() = runBlocking {
        assertExplicitRejectionCodeClearsSession(
            statusCode = 401,
            errorCode = "session_expired",
            msg = "Session has expired",
        )
    }

    private suspend fun assertExplicitRejectionCodeClearsSession(
        statusCode: Int,
        errorCode: String,
        msg: String,
    ) {
        val session = AuthSession(
            userId = "u-exp",
            email = "musico@example.com",
            accessToken = "old_access",
            refreshToken = "old_refresh",
            expiresAtEpochSeconds = 1_760_003_600L,
        )
        val harness = buildTestHarness(initialSession = session) {
            FakeHttpReply(
                code = statusCode,
                body = """{"error_code":"$errorCode","msg":"$msg"}""",
            )
        }
        harness.repo.restoreSession()
        assertNotNull(harness.repo.sessionFlow.value)

        val res = harness.repo.forceRefreshSession()
        assertTrue(res.isFailure)
        val err = res.exceptionOrNull() as? AppError.Unauthorized
        assertNotNull("Expected AppError.Unauthorized for $errorCode", err)
        assertTrue("Expected refreshTokenRejected=true for $errorCode", err!!.refreshTokenRejected)
        assertEquals("Tu sesión venció. Inicia sesión de nuevo.", err.message)
        assertEquals(
            "Tu sesión venció. Inicia sesión de nuevo.",
            harness.repo.sessionExpiredMessageFlow.value,
        )
        assertNull("In-memory session must be cleared for $errorCode", harness.repo.sessionFlow.value)
        assertNull("Persisted session must be cleared for $errorCode", harness.storage.stored)
        assertEquals(1, harness.storage.clearCount)
    }
}
