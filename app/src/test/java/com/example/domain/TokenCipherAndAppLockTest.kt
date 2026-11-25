package com.example.domain

import com.example.data.local.AesGcmTokenCipher
import com.example.data.port.PrivacyPreferences
import com.example.ui.security.AppBiometricAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class TokenCipherAndAppLockTest {

    private fun testAes256Cipher(): AesGcmTokenCipher {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val key = keyGen.generateKey()
        return AesGcmTokenCipher(secretKeyProvider = { key })
    }

    @Test
    fun `AesGcmTokenCipher encrypts and decrypts JWT tokens round-trip`() {
        val cipher = testAes256Cipher()
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"

        val encrypted = cipher.encrypt(token)

        assertTrue(cipher.isEncrypted(encrypted))
        assertTrue(encrypted.startsWith(AesGcmTokenCipher.PREFIX_V1))
        assertFalse(encrypted.contains(token))
        assertEquals(token, cipher.decrypt(encrypted))
    }

    @Test
    fun `AesGcmTokenCipher produces unique ciphertexts for identical tokens via random IV`() {
        val cipher = testAes256Cipher()
        val token = "refresh-token-same-value"

        val first = cipher.encrypt(token)
        val second = cipher.encrypt(token)

        assertNotEquals(first, second)
        assertEquals(token, cipher.decrypt(first))
        assertEquals(token, cipher.decrypt(second))
    }

    @Test
    fun `AesGcmTokenCipher rejects tampered ciphertext and wrong keys`() {
        val cipher1 = testAes256Cipher()
        val cipher2 = testAes256Cipher()
        val encrypted = cipher1.encrypt("secret-access-token")

        // Wrong key must fail authentication tag verification
        assertNull(cipher2.decrypt(encrypted))

        // Corrupted payload must fail safely without throwing
        assertNull(cipher1.decrypt(AesGcmTokenCipher.PREFIX_V1 + "not-valid-base64!!!"))
        assertNull(cipher1.decrypt(AesGcmTokenCipher.PREFIX_V1 + "AAAA"))
    }

    @Test
    fun `AesGcmTokenCipher reads legacy unencrypted tokens for seamless migration`() {
        val cipher = testAes256Cipher()
        val legacyToken = "legacy-plaintext-jwt"

        assertFalse(cipher.isEncrypted(legacyToken))
        assertEquals(legacyToken, cipher.decrypt(legacyToken))
    }

    @Test
    fun `AppBiometricAuthenticator relock grace period works as expected`() {
        val start = 1_000_000L

        assertFalse(
            AppBiometricAuthenticator.shouldRelockAfterBackground(
                appLockEnabled = false,
                backgroundedAtMillis = start,
                nowMillis = start + 120_000L,
            )
        )
        assertFalse(
            AppBiometricAuthenticator.shouldRelockAfterBackground(
                appLockEnabled = true,
                backgroundedAtMillis = start,
                nowMillis = start + 15_000L, // within 30s grace period
            )
        )
        assertTrue(
            AppBiometricAuthenticator.shouldRelockAfterBackground(
                appLockEnabled = true,
                backgroundedAtMillis = start,
                nowMillis = start + 30_000L, // reached 30s grace period
            )
        )
        assertFalse(PrivacyPreferences().appLockEnabled)
    }
}
