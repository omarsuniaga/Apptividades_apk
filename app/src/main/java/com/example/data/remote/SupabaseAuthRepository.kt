package com.example.data.remote

import com.example.data.local.SessionStorage
import com.example.data.port.AuthRepository
import com.example.data.remote.dto.AuthResponseDto
import com.example.data.remote.dto.RecoverPasswordRequestDto
import com.example.data.remote.dto.RefreshTokenRequestDto
import com.example.data.remote.dto.ResendConfirmationRequestDto
import com.example.data.remote.dto.SignInRequestDto
import com.example.data.remote.dto.SignUpRequestDto
import com.example.data.remote.dto.SignUpResponseDto
import com.example.data.remote.dto.UpdatePasswordRequestDto
import com.example.domain.model.AppError
import com.example.domain.model.AuthSession
import com.example.domain.model.SignUpOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

class SupabaseAuthRepository(
    private val httpClient: SupabaseHttpClient,
    private val sessionDataStore: SessionStorage,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val isConfiguredProvider: () -> Boolean = { SupabaseConfig.isKeyConfigured() },
    private val refreshMutex: Mutex = SHARED_REFRESH_MUTEX,
) : AuthRepository {

    private val _sessionFlow = MutableStateFlow<AuthSession?>(null)
    override val sessionFlow: StateFlow<AuthSession?> = _sessionFlow.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _sessionExpiredMessageFlow = MutableStateFlow<String?>(null)
    override val sessionExpiredMessageFlow: StateFlow<String?> = _sessionExpiredMessageFlow.asStateFlow()

    @Volatile
    private var lastResendEpochMillis: Long = 0L

    private val signInAdapter = httpClient.moshi.adapter(SignInRequestDto::class.java)
    private val signUpAdapter = httpClient.moshi.adapter(SignUpRequestDto::class.java)
    private val signUpResponseAdapter = httpClient.moshi.adapter(SignUpResponseDto::class.java)
    private val resendAdapter = httpClient.moshi.adapter(ResendConfirmationRequestDto::class.java)
    private val refreshAdapter = httpClient.moshi.adapter(RefreshTokenRequestDto::class.java)
    private val recoverAdapter = httpClient.moshi.adapter(RecoverPasswordRequestDto::class.java)
    private val updatePasswordAdapter = httpClient.moshi.adapter(UpdatePasswordRequestDto::class.java)
    private val authResponseAdapter = httpClient.moshi.adapter(AuthResponseDto::class.java)

    override fun isConfigured(): Boolean = isConfiguredProvider()

    override fun clearSessionExpiredMessage() {
        _sessionExpiredMessageFlow.value = null
    }

    override suspend fun restoreSession() {
        val saved = runCatching { sessionDataStore.loadSession() }.getOrNull()
        if (saved != null) {
            _sessionFlow.value = saved
            val now = clockMillis() / 1000L
            if (saved.isExpiredOrExpiringSoon(now) && isConfigured()) {
                // forceRefreshSession() clears the session ONLY if the refresh endpoint
                // explicitly rejects the refresh token (400/401 invalid_grant / refresh_token_not_found).
                // On timeouts, offline, 429, or 5xx it preserves the session for later retry.
                forceRefreshSession(failedAccessToken = saved.accessToken)
            }
        }
        _isInitialized.value = true
    }

    override suspend fun signIn(email: String, password: String): Result<AuthSession> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("auth/v1/token")
            .addQueryParameter("grant_type", "password")
            .build()

        val payload = signInAdapter.toJson(SignInRequestDto(email.trim(), password))
        val response = httpClient.executePublic("POST", url, payload)
            .getOrElse { return Result.failure(it) }

        val dto = runCatching { authResponseAdapter.fromJson(response.body) }.getOrNull()
            ?: return Result.failure(AppError.Network(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH))

        val session = dto.toDomain()
        _sessionExpiredMessageFlow.value = null
        sessionDataStore.saveSession(session)
        _sessionFlow.value = session
        return Result.success(session)
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String?,
    ): Result<SignUpOutcome> {
        val cleanEmail = email.trim()
        val redirectUrl = verifiedRedirectUrl(SupabaseConfig.LOGIN_REDIRECT)
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("auth/v1/signup")
            .addQueryParameter("redirect_to", redirectUrl)
            .build()

        val metadata = fullName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            mapOf("full_name" to it)
        }
        val payload = signUpAdapter.toJson(
            SignUpRequestDto(
                email = cleanEmail,
                password = password,
                data = metadata,
            )
        )
        val response = httpClient.executePublic("POST", url, payload)
            .getOrElse { return Result.failure(it) }

        val dto = runCatching { signUpResponseAdapter.fromJson(response.body) }.getOrNull()
            ?: return Result.failure(AppError.Network(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH))
        val access = dto.accessToken
        val refresh = dto.refreshToken
        val user = dto.user

        if (!access.isNullOrBlank() && !refresh.isNullOrBlank() && user != null) {
            val now = clockMillis() / 1000L
            val exp = dto.expiresAt ?: (now + (dto.expiresIn ?: 3600L))
            val session = AuthSession(
                userId = user.id,
                email = user.email ?: cleanEmail,
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochSeconds = exp,
            )
            _sessionExpiredMessageFlow.value = null
            sessionDataStore.saveSession(session)
            _sessionFlow.value = session
            return Result.success(SignUpOutcome.Authenticated(session))
        }

        // GoTrue returns HTTP 200 with an empty identities array (and sends NO email)
        // when the email is already registered and confirmed (anti-enumeration behavior).
        val identities = dto.identities ?: dto.user?.identities
        if (identities != null && identities.isEmpty()) {
            return Result.failure(AppError.Validation(SupabaseAuthErrorMapper.MSG_ALREADY_REGISTERED))
        }

        return Result.success(
            SignUpOutcome.EmailConfirmationRequired(dto.email ?: dto.user?.email ?: cleanEmail)
        )
    }

    override fun remainingResendCooldownSeconds(): Int {
        if (lastResendEpochMillis == 0L) return 0
        val elapsed = clockMillis() - lastResendEpochMillis
        val remainingMs = RESEND_COOLDOWN_MS - elapsed
        if (remainingMs <= 0L) return 0
        return ceil(remainingMs / 1000.0).toInt().coerceIn(1, RESEND_COOLDOWN_SECONDS)
    }

    override suspend fun resendConfirmation(email: String): Result<Unit> {
        val remaining = remainingResendCooldownSeconds()
        if (remaining > 0) {
            return Result.failure(
                AppError.RateLimited("Espera $remaining s antes de reenviar el correo de confirmación.")
            )
        }

        val cleanEmail = email.trim()
        val redirectUrl = verifiedRedirectUrl(SupabaseConfig.LOGIN_REDIRECT)
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("auth/v1/resend")
            .addQueryParameter("redirect_to", redirectUrl)
            .build()

        val payload = resendAdapter.toJson(
            ResendConfirmationRequestDto(
                type = "signup",
                email = cleanEmail,
            )
        )
        val result = httpClient.executePublic("POST", url, payload).map { Unit }
        if (result.isSuccess) {
            lastResendEpochMillis = clockMillis()
        }
        return result
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        val redirectUrl = verifiedRedirectUrl(SupabaseConfig.RESET_PASSWORD_REDIRECT)
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("auth/v1/recover")
            .addQueryParameter("redirect_to", redirectUrl)
            .build()

        val payload = recoverAdapter.toJson(
            RecoverPasswordRequestDto(
                email = email.trim(),
                redirectTo = redirectUrl,
            )
        )
        val rawResult = httpClient.executePublic("POST", url, payload)
        if (rawResult.isSuccess) return Result.success(Unit)

        // Preserve neutral response so account existence is never leaked,
        // while still surfacing genuine network outages, missing config, or rate limits.
        val err = rawResult.exceptionOrNull()
        return when {
            err is AppError.MissingConfig -> Result.failure(err)
            err is AppError.RateLimited -> Result.failure(err)
            err is AppError.Network && err.message == SupabaseAuthErrorMapper.MSG_NO_NETWORK -> Result.failure(err)
            else -> Result.success(Unit)
        }
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> {
        val url = httpClient.baseUrl.newBuilder()
            .addPathSegments("auth/v1/user")
            .build()

        val payload = updatePasswordAdapter.toJson(
            UpdatePasswordRequestDto(password = newPassword)
        )
        return httpClient.executeAuthenticated(
            authRepository = this,
            method = "PUT",
            url = url,
            jsonBody = payload,
        ).map { Unit }
    }

    override suspend fun signOut(): Result<Unit> {
        val currentToken = _sessionFlow.value?.accessToken
        if (!currentToken.isNullOrBlank() && isConfigured()) {
            val url = httpClient.baseUrl.newBuilder()
                .addPathSegments("auth/v1/logout")
                .build()
            runCatching {
                httpClient.executePublic("POST", url, null, bearerToken = currentToken)
            }
        }
        _sessionExpiredMessageFlow.value = null
        sessionDataStore.clearSession()
        _sessionFlow.value = null
        return Result.success(Unit)
    }

    override suspend fun getValidAccessToken(): Result<String> {
        val current = _sessionFlow.value
            ?: runCatching { sessionDataStore.loadSession() }.getOrNull()?.also { _sessionFlow.value = it }
            ?: return Result.failure(AppError.Unauthorized(refreshTokenRejected = true))
        val now = clockMillis() / 1000L
        return if (current.isExpiredOrExpiringSoon(now)) {
            forceRefreshSession(failedAccessToken = current.accessToken).map { it.accessToken }
        } else {
            Result.success(current.accessToken)
        }
    }

    override suspend fun forceRefreshSession(failedAccessToken: String?): Result<AuthSession> {
        val expectedAccessToken = failedAccessToken ?: _sessionFlow.value?.accessToken
        return refreshMutex.withLock {
            val now = clockMillis() / 1000L
            // Read persisted storage too so UI and WorkManager share fresh tokens even across
            // distinct repository instances sharing the same Mutex and SessionStorage.
            val persisted = runCatching { sessionDataStore.loadSession() }.getOrNull()
            val inMemory = _sessionFlow.value
            val latest = when {
                persisted == null -> null
                inMemory == null -> persisted
                persisted.accessToken != inMemory.accessToken -> persisted
                else -> inMemory
            }

            if (latest == null) {
                if (inMemory != null) {
                    _sessionExpiredMessageFlow.value = SupabaseAuthErrorMapper.MSG_SESSION_EXPIRED
                }
                _sessionFlow.value = null
                return@withLock Result.failure(
                    AppError.Unauthorized(
                        message = SupabaseAuthErrorMapper.MSG_SESSION_EXPIRED,
                        refreshTokenRejected = true,
                    )
                )
            }

            // Another caller refreshed the token while we waited on refreshMutex -> reuse it!
            if (
                expectedAccessToken != null &&
                latest.accessToken != expectedAccessToken &&
                !latest.isExpiredOrExpiringSoon(now)
            ) {
                _sessionFlow.value = latest
                return@withLock Result.success(latest)
            }

            if (
                failedAccessToken == null &&
                inMemory != null &&
                latest.accessToken != inMemory.accessToken &&
                !latest.isExpiredOrExpiringSoon(now)
            ) {
                _sessionFlow.value = latest
                return@withLock Result.success(latest)
            }

            val url = httpClient.baseUrl.newBuilder()
                .addPathSegments("auth/v1/token")
                .addQueryParameter("grant_type", "refresh_token")
                .build()

            val payload = refreshAdapter.toJson(RefreshTokenRequestDto(latest.refreshToken))
            val rawResponse = httpClient.executeRawPublic("POST", url, payload)
                .getOrElse { err ->
                    // Timeout, no network, or missing config -> keep session and retry later.
                    return@withLock Result.failure(err)
                }

            if (rawResponse.code !in 200..299) {
                val parsed = httpClient.parseErrorDto(rawResponse.body)
                if (
                    SupabaseAuthErrorMapper.isExplicitRefreshTokenRejection(
                        statusCode = rawResponse.code,
                        parsed = parsed,
                        rawBody = rawResponse.body,
                    )
                ) {
                    sessionDataStore.clearSession()
                    _sessionExpiredMessageFlow.value = SupabaseAuthErrorMapper.MSG_SESSION_EXPIRED
                    _sessionFlow.value = null
                    return@withLock Result.failure(
                        AppError.Unauthorized(
                            message = SupabaseAuthErrorMapper.MSG_SESSION_EXPIRED,
                            refreshTokenRejected = true,
                        )
                    )
                }
                // 429, 5xx, or non-rejection 400/401 -> keep session intact for later retry.
                val retryableError = if (rawResponse.code == 429) {
                    AppError.RateLimited(SupabaseAuthErrorMapper.MSG_RATE_LIMITED)
                } else {
                    AppError.Network(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH)
                }
                return@withLock Result.failure(retryableError)
            }

            val dto = runCatching { authResponseAdapter.fromJson(rawResponse.body) }.getOrNull()
                ?: return@withLock Result.failure(AppError.Network(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH))

            val refreshed = dto.toDomain()
            sessionDataStore.saveSession(refreshed)
            _sessionFlow.value = refreshed
            Result.success(refreshed)
        }
    }

    private fun verifiedRedirectUrl(url: String): String {
        require(!url.contains("localhost", ignoreCase = true) && !url.contains("127.0.0.1")) {
            "redirect_to must never point to localhost"
        }
        return url
    }

    private fun AuthResponseDto.toDomain(): AuthSession {
        val now = clockMillis() / 1000L
        val exp = expiresAt ?: (now + expiresIn)
        return AuthSession(
            userId = user.id,
            email = user.email.orEmpty(),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = exp,
        )
    }

    companion object {
        const val RESEND_COOLDOWN_SECONDS = 60
        const val RESEND_COOLDOWN_MS = RESEND_COOLDOWN_SECONDS * 1_000L

        /** Process-wide Mutex shared by UI and WorkManager to serialize token refreshes. */
        val SHARED_REFRESH_MUTEX: Mutex = Mutex()
    }
}
