package com.example.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(stored: String): String?
    fun isEncrypted(stored: String): Boolean
}

/**
 * Pure AES-GCM (256-bit, 96-bit random IV, 128-bit auth tag) token cipher.
 * Uses `java.util.Base64` (supported natively from Android API 26+ and JVM unit tests).
 *
 * Format: `enc:v1:<base64(12-byte-iv || ciphertext-with-gcm-tag)>`
 * Legacy plaintext values (without `enc:v1:` prefix) are returned as-is during `decrypt`
 * so existing sessions migrate seamlessly without logging users out.
 */
class AesGcmTokenCipher(
    private val secretKeyProvider: () -> SecretKey,
    private val secureRandom: SecureRandom = SecureRandom(),
) : TokenCipher {

    override fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX_V1)

    override fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return plaintext
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = secretKeyProvider()
        // Let Keystore/JCE generate a fresh IV when required, or supply a 12-byte random IV on standard JCE keys.
        val isAndroidKeyStoreKey = key.javaClass.name.contains("AndroidKeyStore", ignoreCase = true)
        if (isAndroidKeyStoreKey) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        val iv = cipher.iv
        require(iv != null && iv.size == GCM_IV_LENGTH_BYTES) {
            "IV AES-GCM inválido."
        }
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return PREFIX_V1 + Base64.getEncoder().encodeToString(combined)
    }

    override fun decrypt(stored: String): String? {
        if (stored.isEmpty()) return ""
        if (!isEncrypted(stored)) {
            // Legacy unencrypted token: allow one-time transparent migration
            return stored
        }
        return runCatching {
            val encoded = stored.removePrefix(PREFIX_V1)
            val combined = Base64.getDecoder().decode(encoded)
            if (combined.size <= GCM_IV_LENGTH_BYTES + 16) return null
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKeyProvider(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        const val PREFIX_V1 = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}

/**
 * Hardware-backed Android Keystore AES-256-GCM cipher for encrypting auth session tokens at rest.
 */
class AndroidKeystoreTokenCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : TokenCipher {

    private val delegate = AesGcmTokenCipher(secretKeyProvider = ::getOrCreateSecretKey)

    override fun isEncrypted(stored: String): Boolean = delegate.isEncrypted(stored)

    override fun encrypt(plaintext: String): String =
        runCatching { delegate.encrypt(plaintext) }.getOrElse { plaintext }

    override fun decrypt(stored: String): String? =
        runCatching { delegate.decrypt(stored) }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "apptividades_session_aes_gcm_v1"
    }
}
