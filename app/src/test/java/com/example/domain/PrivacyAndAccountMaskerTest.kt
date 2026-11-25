package com.example.domain

import com.example.data.port.PrivacyPreferences
import com.example.domain.rules.AccountMasker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivacyAndAccountMaskerTest {

    @Test
    fun `AccountMasker masks standard bank account keeping last 4 digits`() {
        assertEquals("•••• 6789", AccountMasker.maskAccountNumber("001-234567-89"))
        assertEquals("•••• 4821", AccountMasker.maskAccountNumber("7894561234821"))
        assertEquals("•••• 1234", AccountMasker.maskAccountNumber("  DOP 9988 1234  "))
    }

    @Test
    fun `AccountMasker never exposes short numbers in full`() {
        assertEquals("••••", AccountMasker.maskAccountNumber("1234"))
        assertEquals("••••", AccountMasker.maskAccountNumber("99"))
        assertEquals("", AccountMasker.maskAccountNumber("   "))
    }

    @Test
    fun `PrivacyPreferences defaults to private without external IP lookup`() {
        val defaults = PrivacyPreferences()
        assertFalse(defaults.screenPrivacyEnabled)
        assertFalse(defaults.resolveExternalIpInHistory)
    }
}
