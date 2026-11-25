package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.domain.model.Activity
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.model.ScheduledDeparture
import com.example.domain.rules.DominicanFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class NextGigWidgetSnapshot(
    val signedIn: Boolean = false,
    val hasGig: Boolean = false,
    val isToday: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val dateTimeLabel: String = "",
    val departureLabel: String = "",
)

/**
 * Lightweight SharedPreferences bridge so NextGigWidgetProvider renders synchronously
 * with zero I/O blocking or ANR risk, and updates automatically whenever activities change.
 */
object NextGigWidgetStore {

    private const val PREFS_NAME = "apptividades_widget_snapshot"
    private const val KEY_SIGNED_IN = "signed_in"
    private const val KEY_HAS_GIG = "has_gig"
    private const val KEY_IS_TODAY = "is_today"
    private const val KEY_TITLE = "title"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_DATE_TIME = "date_time"
    private const val KEY_DEPARTURE = "departure"

    fun computeSnapshot(
        activities: List<Activity>,
        scheduledDepartures: Map<String, ScheduledDeparture>,
        today: LocalDate,
        nowTime: LocalTime,
        venueNameResolver: (String) -> String = { it },
        supplierNameResolver: (String) -> String = { it },
    ): NextGigWidgetSnapshot {
        val next = activities
            .filter { act ->
                when {
                    act.eventDate.isAfter(today) -> true
                    act.eventDate == today -> {
                        val t = act.eventTime
                        t == null || !t.plusHours(2).isBefore(nowTime)
                    }
                    else -> false
                }
            }
            .sortedWith(compareBy<Activity> { it.eventDate }.thenBy { it.eventTime ?: LocalTime.MAX })
            .firstOrNull()
            ?: return NextGigWidgetSnapshot(signedIn = true, hasGig = false)

        val isToday = next.eventDate == today
        val venue = venueNameResolver(next.venueName).ifBlank { next.venueName }
        val supplier = supplierNameResolver(next.supplierName).ifBlank { next.supplierName }
        val title = venue.ifBlank { next.title.ifBlank { "Servicio musical" } }
        val subtitle = buildList {
            if (supplier.isNotBlank() && !supplier.equals(title, ignoreCase = true)) add(supplier)
            next.description?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.let { desc ->
                if (!desc.equals(title, ignoreCase = true) && !desc.equals(supplier, ignoreCase = true)) {
                    add(desc)
                }
            }
        }.joinToString(" · ")

        val datePart = if (isToday) {
            "Hoy"
        } else {
            DominicanFormatter.formatChipDate(next.eventDate)
        }
        val timePart = next.eventTime?.let { DominicanFormatter.formatTime12h(it) } ?: "Hora por definir"
        val dateTimeLabel = "$datePart · $timePart"

        val departure = scheduledDepartures[next.id]
        val departureLabel = if (departure != null) {
            val leaveTime = Instant.ofEpochMilli(departure.leaveAtEpochMillis)
                .atZone(SANTO_DOMINGO_ZONE)
                .toLocalTime()
            "Sal a las ${DominicanFormatter.formatTime12h(leaveTime)}"
        } else {
            ""
        }

        return NextGigWidgetSnapshot(
            signedIn = true,
            hasGig = true,
            isToday = isToday,
            title = title,
            subtitle = subtitle,
            dateTimeLabel = dateTimeLabel,
            departureLabel = departureLabel,
        )
    }

    fun saveAndRefresh(context: Context, snapshot: NextGigWidgetSnapshot) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SIGNED_IN, snapshot.signedIn)
            .putBoolean(KEY_HAS_GIG, snapshot.hasGig)
            .putBoolean(KEY_IS_TODAY, snapshot.isToday)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_SUBTITLE, snapshot.subtitle)
            .putString(KEY_DATE_TIME, snapshot.dateTimeLabel)
            .putString(KEY_DEPARTURE, snapshot.departureLabel)
            .apply()
        NextGigWidgetProvider.requestUpdateAll(context)
    }

    fun clearAndRefresh(context: Context) {
        saveAndRefresh(context, NextGigWidgetSnapshot(signedIn = false, hasGig = false))
    }

    fun load(context: Context): NextGigWidgetSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NextGigWidgetSnapshot(
            signedIn = prefs.getBoolean(KEY_SIGNED_IN, false),
            hasGig = prefs.getBoolean(KEY_HAS_GIG, false),
            isToday = prefs.getBoolean(KEY_IS_TODAY, false),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            subtitle = prefs.getString(KEY_SUBTITLE, "").orEmpty(),
            dateTimeLabel = prefs.getString(KEY_DATE_TIME, "").orEmpty(),
            departureLabel = prefs.getString(KEY_DEPARTURE, "").orEmpty(),
        )
    }
}

class NextGigWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = NextGigWidgetStore.load(context)
        appWidgetIds.forEach { widgetId ->
            updateAppWidget(context, appWidgetManager, widgetId, snapshot)
        }
    }

    companion object {
        fun requestUpdateAll(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, NextGigWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isNotEmpty()) {
                    val snapshot = NextGigWidgetStore.load(context)
                    ids.forEach { id -> updateAppWidget(context, manager, id, snapshot) }
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            snapshot: NextGigWidgetSnapshot,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_next_gig)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            when {
                !snapshot.signedIn -> {
                    views.setTextViewText(R.id.widget_eyebrow, context.getString(R.string.app_name).uppercase())
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_signed_out_title))
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_signed_out_body))
                    views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_datetime, View.GONE)
                    views.setViewVisibility(R.id.widget_departure_pill, View.GONE)
                }
                !snapshot.hasGig -> {
                    views.setTextViewText(R.id.widget_eyebrow, context.getString(R.string.widget_eyebrow_next))
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                    views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_body))
                    views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_datetime, View.GONE)
                    views.setViewVisibility(R.id.widget_departure_pill, View.GONE)
                }
                else -> {
                    views.setTextViewText(
                        R.id.widget_eyebrow,
                        context.getString(
                            if (snapshot.isToday) R.string.widget_eyebrow_today
                            else R.string.widget_eyebrow_next
                        ),
                    )
                    views.setTextViewText(R.id.widget_title, snapshot.title)
                    if (snapshot.subtitle.isNotBlank()) {
                        views.setTextViewText(R.id.widget_subtitle, snapshot.subtitle)
                        views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_subtitle, View.GONE)
                    }
                    views.setTextViewText(R.id.widget_datetime, snapshot.dateTimeLabel)
                    views.setViewVisibility(R.id.widget_datetime, View.VISIBLE)

                    if (snapshot.departureLabel.isNotBlank()) {
                        views.setTextViewText(R.id.widget_departure_pill, snapshot.departureLabel)
                        views.setViewVisibility(R.id.widget_departure_pill, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.widget_departure_pill, View.GONE)
                    }
                }
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
