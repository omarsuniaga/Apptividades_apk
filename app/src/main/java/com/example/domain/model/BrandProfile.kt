package com.example.domain.model

/**
 * Artist's personal brand identity applied to PDF Account Statements and Quotes.
 * Images are stored in app-private storage (zero storage permissions required).
 */
data class ArtistBrandProfile(
    val artistName: String = "",
    val subtitle: String = "",
    val contactLine: String = "",
    val logoFileName: String? = null,
    val signatureFileName: String? = null,
) {
    val hasBranding: Boolean
        get() = artistName.isNotBlank() ||
            subtitle.isNotBlank() ||
            contactLine.isNotBlank() ||
            !logoFileName.isNullOrBlank() ||
            !signatureFileName.isNullOrBlank()
}
