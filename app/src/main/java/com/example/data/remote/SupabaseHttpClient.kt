package com.example.data.remote

import com.example.data.port.AuthRepository
import com.example.data.remote.dto.SupabaseErrorDto
import com.example.domain.model.AppError
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class HttpResponseData(
    val code: Int,
    val body: String,
)

/**
 * Thin, secure HTTP helper for Supabase GoTrue Auth & PostgREST endpoints.
 * Automatically injects apikey and Bearer JWT, refreshes expired tokens on 401
 * (signing out cleanly if refresh fails or retry still returns 401), and maps
 * Auth/Postgres error payloads to typed Spanish AppError instances.
 */
class SupabaseHttpClient(
    val okHttpClient: OkHttpClient,
    val moshi: Moshi,
    private val apiKeyProvider: () -> String = { SupabaseConfig.publishableKey },
    private val isConfiguredProvider: () -> Boolean = { SupabaseConfig.isKeyConfigured() },
    val baseUrl: HttpUrl = SupabaseConfig.SUPABASE_URL.toHttpUrl(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val errorAdapter = moshi.adapter(SupabaseErrorDto::class.java)

    suspend fun executeAuthenticated(
        authRepository: AuthRepository,
        method: String,
        url: HttpUrl,
        jsonBody: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Result<HttpResponseData> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(AppError.MissingConfig)
        }

        val tokenResult = authRepository.getValidAccessToken()
        val token = tokenResult.getOrElse { return@withContext Result.failure(it) }

        val firstAttempt = performRequest(method, url, jsonBody, token, extraHeaders)
        val response = firstAttempt.getOrElse { return@withContext Result.failure(it) }

        if (response.code == 401) {
            val refreshed = authRepository.forceRefreshSession(failedAccessToken = token)
                .getOrElse { err ->
                    // forceRefreshSession() is the single authority that clears the session
                    // (only on explicit 400/401 invalid_grant / refresh_token_not_found).
                    // On timeouts, offline, 429, or 5xx, keep the session intact.
                    return@withContext Result.failure(err)
                }
            val retryResponse = performRequest(method, url, jsonBody, refreshed.accessToken, extraHeaders)
                .getOrElse { return@withContext Result.failure(it) }
            return@withContext handleStatus(url, retryResponse)
        }

        handleStatus(url, response)
    }

    suspend fun executePublic(
        method: String,
        url: HttpUrl,
        jsonBody: String? = null,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Result<HttpResponseData> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(AppError.MissingConfig)
        }
        val response = performRequest(method, url, jsonBody, bearerToken, extraHeaders)
            .getOrElse { return@withContext Result.failure(it) }
        handleStatus(url, response)
    }

    /**
     * Executes a public request returning the raw HTTP status code and body without mapping
     * non-2xx statuses, so token refresh can distinguish explicit invalid_grant /
     * refresh_token_not_found rejections from retryable 429 / 5xx / generic responses.
     */
    suspend fun executeRawPublic(
        method: String,
        url: HttpUrl,
        jsonBody: String? = null,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Result<HttpResponseData> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(AppError.MissingConfig)
        }
        performRequest(method, url, jsonBody, bearerToken, extraHeaders)
    }

    fun parseErrorDto(rawBody: String): SupabaseErrorDto? =
        runCatching { errorAdapter.fromJson(rawBody) }.getOrNull()

    private fun performRequest(
        method: String,
        url: HttpUrl,
        jsonBody: String?,
        bearerToken: String?,
        extraHeaders: Map<String, String>,
    ): Result<HttpResponseData> {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("apikey", apiKeyProvider())
                .header("Accept", "application/json")

            if (!bearerToken.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $bearerToken")
            }
            for ((k, v) in extraHeaders) {
                builder.header(k, v)
            }

            val body = jsonBody?.toRequestBody(jsonMediaType)
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
                "POST" -> builder.post(body ?: "{}".toRequestBody(jsonMediaType))
                "PATCH" -> builder.patch(body ?: "{}".toRequestBody(jsonMediaType))
                "PUT" -> builder.put(body ?: "{}".toRequestBody(jsonMediaType))
                else -> builder.method(method, body)
            }

            okHttpClient.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                Result.success(HttpResponseData(resp.code, text))
            }
        } catch (_: IOException) {
            Result.failure(SupabaseAuthErrorMapper.mapNetworkFailure())
        } catch (_: Exception) {
            Result.failure(AppError.Network(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH))
        }
    }

    private fun handleStatus(url: HttpUrl, response: HttpResponseData): Result<HttpResponseData> {
        if (response.code in 200..299) {
            return Result.success(response)
        }
        val parsed = runCatching { errorAdapter.fromJson(response.body) }.getOrNull()
        val pgCode = parsed?.code.orEmpty()

        // Always route GoTrue /auth/v1/* errors through strict Spanish error mapper
        if (url.encodedPath.startsWith("/auth/v1/")) {
            return Result.failure(
                SupabaseAuthErrorMapper.mapHttpError(
                    statusCode = response.code,
                    parsed = parsed,
                    rawBody = response.body,
                )
            )
        }

        return Result.failure(
            when {
                pgCode == "23P01" -> AppError.ScheduleConflict
                pgCode == "42703" || pgCode == "42P01" || pgCode == "PGRST204" || pgCode == "PGRST205" ->
                    AppError.ServerFeaturePending
                response.code == 401 -> AppError.Unauthorized(SupabaseAuthErrorMapper.MSG_SESSION_EXPIRED)
                response.code == 429 -> AppError.RateLimited(SupabaseAuthErrorMapper.MSG_RATE_LIMITED)
                else -> AppError.Network(SupabaseAuthErrorMapper.MSG_GENERIC_AUTH)
            }
        )
    }
}
