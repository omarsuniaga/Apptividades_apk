package com.example.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Native biometric + device credential (PIN/pattern/password) authenticator with zero third-party
 * dependencies. Supports Android 8.0+ (API 26–36) cleanly on ComponentActivity.
 */
object AppBiometricAuthenticator {

    const val BACKGROUND_GRACE_PERIOD_MS: Long = 30_000L

    fun shouldRelockAfterBackground(
        appLockEnabled: Boolean,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
        gracePeriodMillis: Long = BACKGROUND_GRACE_PERIOD_MS,
    ): Boolean {
        if (!appLockEnabled) return false
        val bgAt = backgroundedAtMillis ?: return false
        return (nowMillis - bgAt) >= gracePeriodMillis
    }

    fun canAuthenticate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bm = context.getSystemService(BiometricManager::class.java)
            if (bm != null) {
                val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                if (bm.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS) {
                    return true
                }
            }
        }
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isDeviceSecure == true
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String,
        fallbackCredentialLauncher: ((Intent) -> Unit)? = null,
        onSuccess: () -> Unit,
        onFailure: (message: String?) -> Unit,
    ) {
        if (!canAuthenticate(activity)) {
            onFailure("Configura primero huella, rostro o PIN en los ajustes de tu teléfono.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val builder = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }

            val prompt = builder.build()
            val cancellationSignal = CancellationSignal()
            prompt.authenticate(
                cancellationSignal,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        val isCancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        onFailure(if (isCancelled) null else errString?.toString())
                    }

                    override fun onAuthenticationFailed() {
                        // System prompt remains open and lets the user retry
                    }
                }
            )
            return
        }

        // Android 8.0–9.0 fallback via KeyguardManager device credential confirmation
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        @Suppress("DEPRECATION")
        val confirmIntent = km?.createConfirmDeviceCredentialIntent(title, subtitle)
        if (confirmIntent != null && fallbackCredentialLauncher != null) {
            fallbackCredentialLauncher(confirmIntent)
        } else {
            onFailure("No se pudo abrir la verificación de seguridad del dispositivo.")
        }
    }
}
