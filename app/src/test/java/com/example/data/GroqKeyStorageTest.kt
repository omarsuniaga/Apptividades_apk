package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AesGcmTokenCipher
import com.example.data.local.SessionDataStore
import com.example.data.local.TokenCipher
import com.example.domain.model.AuthSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroqKeyStorageTest {

    private lateinit var context: Context
    private val cipher = newCipher()

    private fun newCipher(): TokenCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AesGcmTokenCipher(secretKeyProvider = { key })
    }

    /** Behaves like the Android Keystore cipher when the Keystore fails: returns the input. */
    private class FailingCipher : TokenCipher {
        override fun encrypt(plaintext: String): String = plaintext
        override fun decrypt(stored: String): String = stored
        override fun isEncrypted(stored: String): Boolean = false
    }

    private fun store(tokenCipher: TokenCipher = cipher) = SessionDataStore(context, tokenCipher)

    private suspend fun signIn(userId: String) {
        store().saveSession(AuthSession(userId, "user@example.test", "access", "refresh", 0L))
    }

    @Before
    fun clean() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        for (user in listOf("one", "two")) {
            signIn(user)
            store().clearGroqApiKey()
        }
        store().clearSession()
    }

    @Test
    fun `a saved key survives signing out and back in to the same account`() = runBlocking {
        signIn("one")
        store().saveEncryptedGroqApiKey(KEY_ONE, "gsk_one")
        store().clearSession()

        assertNull(store().loadDecryptedGroqApiKey())
        assertFalse(store().observeGroqPrefs().first().hasEncryptedUserKey)

        signIn("one")

        assertEquals(KEY_ONE, store().loadDecryptedGroqApiKey())
        assertTrue(store().observeGroqPrefs().first().hasEncryptedUserKey)
    }

    @Test
    fun `another account on the same phone does not see the key`() = runBlocking {
        signIn("one")
        store().saveEncryptedGroqApiKey(KEY_ONE, "gsk_one")
        signIn("two")

        assertNull(store().loadDecryptedGroqApiKey())
        assertNull(store().observeGroqPrefs().first().maskedKeyPreview)
    }

    @Test
    fun `a key that can no longer be read is reported instead of looking configured`() = runBlocking {
        signIn("one")
        store().saveEncryptedGroqApiKey(KEY_ONE, "gsk_one")

        val prefs = store(newCipher()).observeGroqPrefs().first()

        assertFalse(prefs.hasEncryptedUserKey)
        assertTrue(prefs.keyNeedsReentry)
    }

    @Test
    fun `the key is not stored when it cannot be encrypted`() = runBlocking {
        signIn("one")

        val result = runCatching { store(FailingCipher()).saveEncryptedGroqApiKey(KEY_ONE, "gsk_one") }

        assertTrue(result.isFailure)
        assertNull(store(FailingCipher()).loadDecryptedGroqApiKey())
        assertFalse(store().observeGroqPrefs().first().keyNeedsReentry)
    }

    @Test
    fun `a key saved before accounts were separated is adopted once`() = runBlocking {
        store().writeLegacyGroqKeyForTest(cipher.encrypt(KEY_ONE), "gsk_one")
        signIn("one")

        assertEquals(KEY_ONE, store().loadDecryptedGroqApiKey())

        signIn("two")
        assertNull(store().loadDecryptedGroqApiKey())
    }

    @Test
    fun `clearing removes the key of the signed in account only`() = runBlocking {
        signIn("one")
        store().saveEncryptedGroqApiKey(KEY_ONE, "gsk_one")
        signIn("two")
        store().saveEncryptedGroqApiKey(KEY_TWO, "gsk_two")

        store().clearGroqApiKey()

        assertNull(store().loadDecryptedGroqApiKey())
        signIn("one")
        assertEquals(KEY_ONE, store().loadDecryptedGroqApiKey())
    }

    private companion object {
        const val KEY_ONE = "gsk_test_only_value_number_one"
        const val KEY_TWO = "gsk_test_only_value_number_two"
    }
}
