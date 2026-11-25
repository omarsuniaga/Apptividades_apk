package com.example.data.remote

import com.example.BuildConfig

object SupabaseConfig {
    const val SUPABASE_URL: String = "https://fmtjiwltxyzlfreimcue.supabase.co"
    const val LOGIN_REDIRECT: String = "https://apptividades-lab.netlify.app/login"
    const val RESET_PASSWORD_REDIRECT: String = "https://apptividades-lab.netlify.app/reset-password"
    const val PAGE_SIZE: Int = 500

    private const val PLACEHOLDER_SUPABASE_KEY = "YOUR_SUPABASE_PUBLISHABLE_KEY"

    val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    fun isKeyConfigured(): Boolean =
        publishableKey.isNotEmpty() && publishableKey != PLACEHOLDER_SUPABASE_KEY
}
