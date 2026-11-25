package com.example.domain.rules

/**
 * Masks sensitive bank account numbers on screen (e.g., "001-234567-89" -> "•••• 6789")
 * so numbers are not exposed over the shoulder while still letting the user recognize each account.
 */
object AccountMasker {

    fun maskAccountNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val alnum = trimmed.filter { it.isLetterOrDigit() }
        if (alnum.length <= 4) return "••••"
        return "•••• ${alnum.takeLast(4)}"
    }
}
