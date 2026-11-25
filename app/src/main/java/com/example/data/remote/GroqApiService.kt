package com.example.data.remote

import com.example.domain.model.AppError
import com.example.domain.model.GroqCompletionResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@JsonClass(generateAdapter = true)
data class GroqMessageDto(
    val role: String,
    val content: String,
)

@JsonClass(generateAdapter = true)
data class GroqChatRequestDto(
    val model: String,
    val messages: List<GroqMessageDto>,
    val temperature: Double = 0.25,
    @Json(name = "max_completion_tokens") val maxCompletionTokens: Int = 1100,
)

@JsonClass(generateAdapter = true)
internal data class GroqChoiceMessageDto(
    val role: String? = null,
    val content: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqChoiceDto(
    val index: Int = 0,
    val message: GroqChoiceMessageDto? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqUsageDto(
    @Json(name = "total_tokens") val totalTokens: Int? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqChatResponseDto(
    val model: String? = null,
    val choices: List<GroqChoiceDto> = emptyList(),
    val usage: GroqUsageDto? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqErrorDetailDto(
    val message: String? = null,
    val code: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqErrorEnvelopeDto(
    val error: GroqErrorDetailDto? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqModelEntryDto(
    val id: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class GroqModelsListDto(
    val data: List<GroqModelEntryDto> = emptyList(),
)

data class GroqModelsCheckResult(
    val availableModelIds: List<String>,
    val latencyMs: Long,
)

/**
 * Low-level HTTPS data service for Groq's OpenAI-compatible REST endpoints.
 * Stateless with respect to API keys (keys are resolved securely by [com.example.data.repository.GroqRepository]).
 */
interface GroqApiService {
    suspend fun listModels(apiKey: String): Result<GroqModelsCheckResult>
    suspend fun createChatCompletion(
        apiKey: String,
        request: GroqChatRequestDto,
    ): Result<GroqCompletionResult>
}

class OkHttpGroqApiService(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) : GroqApiService {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val requestAdapter = moshi.adapter(GroqChatRequestDto::class.java)
    private val responseAdapter = moshi.adapter(GroqChatResponseDto::class.java)
    private val errorAdapter = moshi.adapter(GroqErrorEnvelopeDto::class.java)
    private val modelsAdapter = moshi.adapter(GroqModelsListDto::class.java)

    override suspend fun listModels(apiKey: String): Result<GroqModelsCheckResult> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(MODELS_ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()

            val startedAt = System.currentTimeMillis()
            try {
                okHttpClient.newCall(req).execute().use { resp ->
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
                    }
                    val parsed = runCatching { modelsAdapter.fromJson(bodyStr) }.getOrNull()
                    val availableIds = parsed?.data?.mapNotNull { it.id }.orEmpty()
                    Result.success(
                        GroqModelsCheckResult(
                            availableModelIds = availableIds,
                            latencyMs = elapsed,
                        )
                    )
                }
            } catch (_: IOException) {
                Result.failure(AppError.Network("Sin conexión a internet para contactar a Groq."))
            } catch (_: Exception) {
                Result.failure(AppError.Network("No se pudo verificar la conexión con Groq."))
            }
        }

    override suspend fun createChatCompletion(
        apiKey: String,
        request: GroqChatRequestDto,
    ): Result<GroqCompletionResult> = withContext(Dispatchers.IO) {
        val payloadJson = requestAdapter.toJson(request)
        val req = Request.Builder()
            .url(CHAT_COMPLETIONS_ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payloadJson.toRequestBody(jsonMediaType))
            .build()

        val startedAt = System.currentTimeMillis()
        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(mapHttpError(resp.code, bodyStr))
                }
                val parsed = runCatching { responseAdapter.fromJson(bodyStr) }.getOrNull()
                val text = parsed?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                if (text.isEmpty()) {
                    return@withContext Result.failure(
                        AppError.Network("Groq devolvió una respuesta vacía. Intenta nuevamente.")
                    )
                }
                Result.success(
                    GroqCompletionResult(
                        content = text,
                        model = parsed?.model ?: request.model,
                        latencyMs = elapsed,
                        totalTokens = parsed?.usage?.totalTokens,
                    )
                )
            }
        } catch (_: IOException) {
            Result.failure(AppError.Network("No hay conexión a internet para consultar a Groq."))
        } catch (_: Exception) {
            Result.failure(AppError.Network("Ocurrió un error al generar el análisis con Groq."))
        }
    }

    private fun mapHttpError(statusCode: Int, rawBody: String): AppError {
        val parsedMsg = runCatching { errorAdapter.fromJson(rawBody)?.error?.message }.getOrNull()
        return when (statusCode) {
            401, 403 -> AppError.Unauthorized(
                "La API Key de Groq es inválida o fue revocada. Verifica tu clave en Ajustes."
            )
            429 -> AppError.RateLimited(
                "Alcanzaste el límite temporal de solicitudes en Groq. Espera unos segundos e intenta otra vez."
            )
            404 -> AppError.Validation(
                "El modelo seleccionado no está disponible en tu cuenta de Groq. Cambia el modelo en Ajustes."
            )
            400 -> AppError.Validation(
                parsedMsg?.take(180)?.takeIf { it.isNotBlank() }
                    ?: "Solicitud rechazada por Groq. Intenta con una consulta más breve."
            )
            else -> AppError.Network(
                "Groq no respondió correctamente (código $statusCode). Intenta nuevamente."
            )
        }
    }

    companion object {
        const val CHAT_COMPLETIONS_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"
    }
}
