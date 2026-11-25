package com.example.domain.rules

import java.time.LocalTime

enum class SupplierMessagePreset {
    ARRIVED,
    DELAY,
    FINISHED,
    CUSTOM,
}

/**
 * Builds quick status messages for communicating with event suppliers
 * (arrival at venue, delay/contingency notice, performance finished, or open custom situation notes).
 */
object SupplierQuickMessageBuilder {

    val quickSituationSnippets: List<String> = listOf(
        "Estoy esperando acceso en seguridad/entrada.",
        "Hay tráfico en la vía, llego en aprox. 15 minutos.",
        "Ya estoy montando el equipo.",
        "Hubo un ajuste en el horario del evento.",
        "Todo salió excelente, gracias por el apoyo.",
    )

    fun buildPresetMessage(
        preset: SupplierMessagePreset,
        supplierDisplayName: String?,
        venueName: String?,
        eventTime: LocalTime?,
        extraSituation: String? = null,
    ): String {
        val cleanSupplier = supplierDisplayName?.trim().orEmpty()
        val greeting = if (cleanSupplier.isNotEmpty()) "Hola $cleanSupplier," else "Hola,"
        val cleanVenue = venueName?.trim().orEmpty()
        val venuePhrase = if (cleanVenue.isNotEmpty()) "en $cleanVenue" else "en el lugar del evento"
        val venueDestination = if (cleanVenue.isNotEmpty()) "a $cleanVenue" else "al lugar del evento"
        val timeSuffix = eventTime?.let { " de las ${DominicanFormatter.formatTime12h(it)}" }.orEmpty()

        val base = when (preset) {
            SupplierMessagePreset.ARRIVED ->
                "$greeting te aviso que ya llegué $venueDestination para la actividad$timeSuffix. Quedo atento por aquí."

            SupplierMessagePreset.DELAY ->
                "$greeting te aviso que voy en camino $venueDestination para la actividad$timeSuffix, pero tengo un pequeño contratiempo en el trayecto. Te mantengo al tanto de mi llegada."

            SupplierMessagePreset.FINISHED ->
                "$greeting te aviso que ya terminé la actuación $venuePhrase. ¡Muchas gracias por todo!"

            SupplierMessagePreset.CUSTOM ->
                "$greeting te escribo respecto a la actividad $venuePhrase$timeSuffix:"
        }

        val cleanExtra = extraSituation?.trim().orEmpty()
        return if (cleanExtra.isNotEmpty()) {
            "$base\n\n$cleanExtra"
        } else {
            base
        }
    }

    fun appendSnippet(currentText: String, snippet: String): String {
        val trimmedCurrent = currentText.trimEnd()
        val trimmedSnippet = snippet.trim()
        if (trimmedSnippet.isEmpty()) return currentText
        if (trimmedCurrent.isEmpty()) return trimmedSnippet
        if (trimmedCurrent.contains(trimmedSnippet, ignoreCase = true)) return trimmedCurrent
        return "$trimmedCurrent\n$trimmedSnippet"
    }

    fun buildWhatsAppUrl(rawPhone: String?, message: String): String? =
        WhatsAppReminderBuilder.buildWaMeUrl(rawPhone, message)
}
