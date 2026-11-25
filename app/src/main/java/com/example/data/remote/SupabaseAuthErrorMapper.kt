package com.example.data.remote

import com.example.data.remote.dto.SupabaseErrorDto
import com.example.domain.model.AppError

/**
 * Maps Supabase GoTrue / Auth HTTP error payloads into clean, user-friendly Spanish AppError
 * instances. Never leaks raw English messages, stack traces, or tokens to the UI.
 */
object SupabaseAuthErrorMapper {

    const val SIGNUP_CONFIRMATION_MESSAGE =
        "Te enviamos un correo. Ábrelo una sola vez, pulsa el enlace y vuelve aquí para iniciar sesión."

    const val RECOVER_NEUTRAL_MESSAGE =
        "Si existe una cuenta, recibirás instrucciones."

    const val RECOVER_BROWSER_HINT =
        "El enlace se abre en el navegador; ahí eliges la nueva contraseña y regresas a la app."

    const val MSG_INVALID_CREDENTIALS = "Correo o contraseña incorrectos."
    const val MSG_EMAIL_NOT_CONFIRMED =
        "Tu correo aún no ha sido confirmado. Revisa tu bandeja de entrada o reenvía el correo de confirmación."
    const val MSG_ALREADY_REGISTERED =
        "Ya existe una cuenta registrada con ese correo. Inicia sesión o recupera tu contraseña."
    const val MSG_RATE_LIMITED = "Demasiados intentos. Espera un minuto e inténtalo de nuevo."
    const val MSG_WEAK_PASSWORD =
        "La contraseña es muy débil. Usa al menos 8 caracteres con letras y números."
    const val MSG_SAME_PASSWORD = "La nueva contraseña debe ser diferente a la actual."
    const val MSG_NO_NETWORK = "Sin conexión a internet. Verifica tu red e inténtalo nuevamente."
    const val MSG_SESSION_EXPIRED = "Tu sesión venció. Inicia sesión de nuevo."
    const val MSG_GENERIC_AUTH = "No se pudo completar la operación. Inténtalo de nuevo."

    fun mapHttpError(
        statusCode: Int,
        parsed: SupabaseErrorDto?,
        rawBody: String = "",
    ): AppError {
        val code = (parsed?.errorCode ?: parsed?.code ?: parsed?.error).orEmpty().lowercase()
        val combined = listOfNotNull(
            parsed?.errorCode,
            parsed?.error,
            parsed?.errorDescription,
            parsed?.msg,
            parsed?.message,
            rawBody.take(300),
        ).joinToString(" ").lowercase()

        // 1. Rate limit / too many requests
        if (
            statusCode == 429 ||
            code == "over_request_rate_limit" ||
            code == "over_email_send_rate_limit" ||
            combined.contains("rate limit") ||
            combined.contains("too many requests") ||
            combined.contains("only request this after") ||
            combined.contains("for security purposes")
        ) {
            return AppError.RateLimited(MSG_RATE_LIMITED)
        }

        // 2. Email not confirmed
        if (
            code == "email_not_confirmed" ||
            combined.contains("email not confirmed") ||
            combined.contains("email_not_confirmed")
        ) {
            return AppError.EmailNotConfirmed(MSG_EMAIL_NOT_CONFIRMED)
        }

        // 3. Invalid credentials
        if (
            code == "invalid_credentials" ||
            combined.contains("invalid login credentials") ||
            combined.contains("invalid_credentials") ||
            (code == "invalid_grant" && !combined.contains("refresh"))
        ) {
            return AppError.Unauthorized(MSG_INVALID_CREDENTIALS)
        }

        // 4. Already registered
        if (
            code == "user_already_exists" ||
            code == "email_exists" ||
            combined.contains("already registered") ||
            combined.contains("already been registered") ||
            combined.contains("already exists") ||
            combined.contains("user_already_exists")
        ) {
            return AppError.Validation(MSG_ALREADY_REGISTERED)
        }

        // 5. Same password (on PUT /auth/v1/user)
        if (
            code == "same_password" ||
            combined.contains("should be different from the old password") ||
            combined.contains("same_password")
        ) {
            return AppError.Validation(MSG_SAME_PASSWORD)
        }

        // 6. Weak password
        if (
            code == "weak_password" ||
            combined.contains("weak_password") ||
            combined.contains("weak password") ||
            combined.contains("password should be at least") ||
            combined.contains("password is too weak") ||
            combined.contains("password must")
        ) {
            return AppError.Validation(MSG_WEAK_PASSWORD)
        }

        // 7. Invalid email
        if (
            code == "validation_failed" && combined.contains("email") ||
            combined.contains("unable to validate email") ||
            combined.contains("invalid email")
        ) {
            return AppError.Validation("Ingresa un correo electrónico válido.")
        }

        // 8. Unauthorized / invalid refresh token / expired JWT
        if (
            statusCode == 401 ||
            combined.contains("refresh token") ||
            combined.contains("jwt expired") ||
            combined.contains("invalid jwt") ||
            combined.contains("session_not_found")
        ) {
            return AppError.Unauthorized(MSG_SESSION_EXPIRED)
        }

        // 9. Safe fallback — never expose raw English errors or tokens
        return AppError.Network(MSG_GENERIC_AUTH)
    }

    private val EXPLICIT_REFRESH_REJECTION_CODES = setOf(
        "invalid_grant",
        "refresh_token_not_found",
        "refresh_token_already_used",
        "session_not_found",
        "session_expired",
    )

    /**
     * Returns true ONLY when the GoTrue refresh endpoint explicitly rejects the refresh token
     * (HTTP 400 or 401 with `invalid_grant`, `refresh_token_not_found`,
     * `refresh_token_already_used`, `session_not_found`, or `session_expired`).
     * Timeouts, offline errors, 429, 5xx, and generic 400/401 responses return false so the
     * caller preserves the session and retries later.
     */
    fun isExplicitRefreshTokenRejection(
        statusCode: Int,
        parsed: SupabaseErrorDto?,
        rawBody: String = "",
    ): Boolean {
        if (statusCode != 400 && statusCode != 401) return false
        val codes = listOfNotNull(
            parsed?.errorCode,
            parsed?.code,
            parsed?.error,
        ).map { it.lowercase() }
        val combined = listOfNotNull(
            parsed?.errorCode,
            parsed?.code,
            parsed?.error,
            parsed?.errorDescription,
            parsed?.msg,
            parsed?.message,
            rawBody.take(300),
        ).joinToString(" ").lowercase()

        return codes.any { it in EXPLICIT_REFRESH_REJECTION_CODES } ||
            EXPLICIT_REFRESH_REJECTION_CODES.any { combined.contains(it) }
    }

    fun mapNetworkFailure(): AppError.Network = AppError.Network(MSG_NO_NETWORK)
}
