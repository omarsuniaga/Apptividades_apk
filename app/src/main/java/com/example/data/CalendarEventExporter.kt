package com.example.data

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.domain.model.Activity
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.rules.DominicanFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Zero-permission native Android Calendar export via CalendarContract.Events ACTION_INSERT.
 * Opens the user's preferred calendar app (Google Calendar, Samsung Calendar, etc.) pre-filled
 * with exact start/end times, location, and gig details.
 */
object CalendarEventExporter {

    fun buildCalendarInsertIntent(
        eventDate: LocalDate,
        eventTime: LocalTime?,
        durationMinutes: Int,
        title: String,
        location: String?,
        description: String?,
    ): Intent {
        val safeDuration = durationMinutes.coerceIn(15, 720).toLong()
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.trim().ifBlank { "Servicio musical" })
            if (!location.isNullOrBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location.trim())
            }
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description.trim())
            }
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)

            if (eventTime != null) {
                val startZdt = eventDate.atTime(eventTime).atZone(SANTO_DOMINGO_ZONE)
                val endZdt = startZdt.plusMinutes(safeDuration)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startZdt.toInstant().toEpochMilli())
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endZdt.toInstant().toEpochMilli())
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
            } else {
                val startUtc = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endUtc = eventDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startUtc)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endUtc)
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun exportActivityToCalendar(
        context: Context,
        activity: Activity,
        supplierDisplayName: String,
        venueDisplayName: String,
        durationMinutes: Int,
    ): Result<Unit> = runCatching {
        val venueLabel = venueDisplayName.ifBlank { activity.venueName }
        val supplierLabel = supplierDisplayName.ifBlank { activity.supplierName }
        val title = buildList {
            if (venueLabel.isNotBlank()) add(venueLabel)
            if (supplierLabel.isNotBlank()) add(supplierLabel)
        }.joinToString(" · ").ifBlank { activity.title }

        val location = listOfNotNull(
            venueLabel.takeIf { it.isNotBlank() },
            activity.venueAddress?.takeIf { it.isNotBlank() },
        ).joinToString(", ").ifBlank { null }

        val description = buildList {
            if (supplierLabel.isNotBlank()) add("Proveedor: $supplierLabel")
            add("Monto: ${DominicanFormatter.formatMoney(activity.amount, activity.currency)}")
            add("Duración estimada: $durationMinutes min")
            activity.description?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            add("Registrado con Apptividades")
        }.joinToString("\n")

        val intent = buildCalendarInsertIntent(
            eventDate = activity.eventDate,
            eventTime = activity.eventTime,
            durationMinutes = durationMinutes,
            title = title,
            location = location,
            description = description,
        )
        context.startActivity(intent)
    }
}
