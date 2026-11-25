package com.example.domain.rules

data class PasswordStrengthCheck(
    val hasMinLength: Boolean,
    val hasLetter: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
) {
    /** Satisfies policy: minimum 8 characters with letters and numbers. */
    val isStrong: Boolean
        get() = hasMinLength && hasLetter && hasDigit
}

object AuthInputValidator {
    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    fun isValidEmail(email: String): Boolean =
        EMAIL_REGEX.matches(email.trim())

    fun checkPasswordStrength(password: String): PasswordStrengthCheck =
        PasswordStrengthCheck(
            hasMinLength = password.length >= 8,
            hasLetter = password.any { it.isLetter() },
            hasUppercase = password.any { it.isUpperCase() },
            hasLowercase = password.any { it.isLowerCase() },
            hasDigit = password.any { it.isDigit() },
        )

    fun validatePassword(
        password: String,
        confirmPassword: String,
    ): String? {
        val check = checkPasswordStrength(password)
        if (!check.hasMinLength) {
            return "La contraseña debe tener al menos 8 caracteres."
        }
        if (!check.hasLetter || !check.hasDigit) {
            return "La contraseña debe incluir letras y números."
        }
        if (password != confirmPassword) {
            return "Las contraseñas no coinciden."
        }
        return null
    }

    fun validateSignUp(
        email: String,
        password: String,
        confirmPassword: String,
    ): String? {
        if (!isValidEmail(email)) {
            return "Ingresa un correo electrónico válido."
        }
        return validatePassword(password, confirmPassword)
    }

    fun validatePasswordChange(
        newPassword: String,
        confirmPassword: String,
    ): String? = validatePassword(newPassword, confirmPassword)
}
