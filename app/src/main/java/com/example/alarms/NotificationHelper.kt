package com.example.alarms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_DEPARTURE = "apptividades_departure"
    const val CHANNEL_CONTINGENCY = "apptividades_contingency"
    const val CHANNEL_RECEIVABLES = "apptividades_receivables"
    const val CHANNEL_SCORES = "apptividades_scores"
    const val CHANNEL_SUMMARY = "apptividades_summary"
    const val CHANNEL_QUOTES = "apptividades_quotes"

    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val EXTRA_QUICK_MESSAGE_ACTIVITY_ID = "extra_quick_message_activity_id"
    const val EXTRA_QUICK_MESSAGE_PRESET = "extra_quick_message_preset"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val departureChannel = NotificationChannel(
            CHANNEL_DEPARTURE,
            context.getString(R.string.channel_departure_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_departure_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 200, 350)
            setSound(alarmSound, alarmAttrs)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        val contingencyChannel = NotificationChannel(
            CHANNEL_CONTINGENCY,
            context.getString(R.string.channel_contingency_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_contingency_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 180, 400, 180, 400)
            setSound(alarmSound, alarmAttrs)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        val receivablesChannel = NotificationChannel(
            CHANNEL_RECEIVABLES,
            context.getString(R.string.channel_receivables_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_receivables_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val scoresChannel = NotificationChannel(
            CHANNEL_SCORES,
            context.getString(R.string.channel_scores_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_scores_desc)
        }

        val summaryChannel = NotificationChannel(
            CHANNEL_SUMMARY,
            context.getString(R.string.channel_summary_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_summary_desc)
        }

        val quotesChannel = NotificationChannel(
            CHANNEL_QUOTES,
            context.getString(R.string.channel_quotes_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_quotes_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannels(
            listOf(
                quotesChannel,
                departureChannel,
                contingencyChannel,
                receivablesChannel,
                scoresChannel,
                summaryChannel,
            )
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun postPrepareNotification(
        context: Context,
        activityId: String,
        venueName: String,
        eventTimeLabel: String,
        leaveTimeLabel: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAppIntent = openAppPendingIntent(context, activityId.hashCode())
        val body = context.getString(R.string.notif_prepare_body, venueName, eventTimeLabel, leaveTimeLabel)
        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTURE)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_prepare_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(activityId, 1), notification)
        }
    }

    fun postLeaveNotification(
        context: Context,
        activityId: String,
        venueName: String,
        latitude: Double?,
        longitude: Double?,
        travelMinutes: Int,
        arrivalTimeLabel: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAppIntent = openAppPendingIntent(context, activityId.hashCode() + 10)
        val mapsPendingIntent = googleMapsPendingIntent(context, activityId, venueName, latitude, longitude)
        val wazePendingIntent = wazePendingIntent(context, activityId, venueName, latitude, longitude)
        val body = if (travelMinutes > 0) {
            context.getString(R.string.notif_leave_body, travelMinutes, arrivalTimeLabel)
        } else {
            context.getString(R.string.notif_leave_body_no_travel, arrivalTimeLabel)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_DEPARTURE)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_leave_title, venueName))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        mapsPendingIntent?.let {
            builder.addAction(
                android.R.drawable.ic_menu_directions,
                context.getString(R.string.notif_action_google_maps),
                it,
            )
        }
        wazePendingIntent?.let {
            builder.addAction(
                android.R.drawable.ic_menu_compass,
                context.getString(R.string.notif_action_waze),
                it,
            )
        }

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(activityId, 2), builder.build())
        }
    }

    fun postLateNotification(
        context: Context,
        activityId: String,
        venueName: String,
        latitude: Double?,
        longitude: Double?,
        arrivalTimeLabel: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAppIntent = openAppPendingIntent(context, activityId.hashCode() + 20)
        val mapsPendingIntent = googleMapsPendingIntent(context, activityId, venueName, latitude, longitude)
        val wazePendingIntent = wazePendingIntent(context, activityId, venueName, latitude, longitude)
        val body = context.getString(R.string.notif_late_body, arrivalTimeLabel)

        val builder = NotificationCompat.Builder(context, CHANNEL_DEPARTURE)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_late_title, venueName))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        mapsPendingIntent?.let {
            builder.addAction(
                android.R.drawable.ic_menu_directions,
                context.getString(R.string.notif_action_google_maps),
                it,
            )
        }
        wazePendingIntent?.let {
            builder.addAction(
                android.R.drawable.ic_menu_compass,
                context.getString(R.string.notif_action_waze),
                it,
            )
        }

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(activityId, 3), builder.build())
        }
    }

    fun postContingencyNotification(
        context: Context,
        activityId: String,
        venueName: String,
        minutesUntilStart: Int,
        eventTimeLabel: String,
        distanceLabel: String?,
        isFinalEscalation: Boolean,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notifySupplierIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_QUICK_MESSAGE_ACTIVITY_ID, activityId)
            putExtra(EXTRA_QUICK_MESSAGE_PRESET, "DELAY")
        }
        val notifySupplierPi = PendingIntent.getActivity(
            context,
            notificationId(activityId, 41),
            notifySupplierIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val underControlIntent = Intent(context, ContingencyActionReceiver::class.java).apply {
            putExtra(DepartureAlarmScheduler.EXTRA_ACTIVITY_ID, activityId)
        }
        val underControlPi = PendingIntent.getBroadcast(
            context,
            notificationId(activityId, 42),
            underControlIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (isFinalEscalation) {
            context.getString(R.string.notif_contingency_title_final, venueName)
        } else {
            context.getString(R.string.notif_contingency_title, venueName)
        }
        val body = if (!distanceLabel.isNullOrBlank()) {
            context.getString(
                R.string.notif_contingency_body_distance,
                minutesUntilStart.coerceAtLeast(1),
                eventTimeLabel,
                distanceLabel,
            )
        } else {
            context.getString(
                R.string.notif_contingency_body_no_distance,
                minutesUntilStart.coerceAtLeast(1),
                eventTimeLabel,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CONTINGENCY)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(notifySupplierPi)
            .addAction(
                android.R.drawable.ic_dialog_email,
                context.getString(R.string.notif_contingency_action_notify_supplier),
                notifySupplierPi,
            )
            .addAction(
                android.R.drawable.ic_menu_save,
                context.getString(R.string.notif_contingency_action_under_control),
                underControlPi,
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId(activityId, 4), notification)
        }
    }

    fun cancelContingencyNotification(context: Context, activityId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId(activityId, 4))
        }
    }

    fun postDailySummaryNotification(
        context: Context,
        summaryLines: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAppIntent = openAppPendingIntent(context, 8000)
        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_daily_title))
            .setContentText(summaryLines.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryLines))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(8001, notification)
        }
    }

    /** "Time to follow up on this quote" reminder. Client name is hidden on the lock screen. */
    fun postQuoteFollowUpNotification(
        context: Context,
        quoteId: String,
        clientName: String,
        amountText: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openFunnelIntent = openAppPendingIntent(
            context = context,
            requestCode = 8003,
            openTabRoute = com.example.ROUTE_QUOTE_FUNNEL,
        )
        val body = context.getString(R.string.notif_quote_followup_body, clientName, amountText)
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_QUOTES)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_quote_followup_title))
            .setContentText(context.getString(R.string.notif_quote_followup_public_body))
            .setAutoCancel(true)
            .setContentIntent(openFunnelIntent)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_QUOTES)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_quote_followup_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setAutoCancel(true)
            .setContentIntent(openFunnelIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(quoteId, 8003), notification)
        }
    }

    fun postReceivablesReminderNotification(
        context: Context,
        overdueAmountText: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openCobrosIntent = openAppPendingIntent(
            context = context,
            requestCode = 8002,
            openTabRoute = com.example.ROUTE_COBROS,
        )
        val body = context.getString(R.string.notif_receivables_body, overdueAmountText)
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_RECEIVABLES)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_receivables_title))
            .setContentText(context.getString(R.string.notif_receivables_public_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openCobrosIntent)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_RECEIVABLES)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_receivables_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setAutoCancel(true)
            .setContentIntent(openCobrosIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                context.getString(R.string.notif_receivables_action_open),
                openCobrosIntent,
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(8002, notification)
        }
    }

    fun postScoreReminderNotification(
        context: Context,
        daysAhead: Int,
        targetDateLabel: String,
        detailsLines: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAgendaIntent = openAppPendingIntent(
            context = context,
            requestCode = 8003,
            openTabRoute = com.example.ROUTE_AGENDA,
        )
        val header = if (daysAhead == 1) {
            context.getString(R.string.notif_scores_body_header_tomorrow, targetDateLabel)
        } else {
            context.getString(R.string.notif_scores_body_header, daysAhead, targetDateLabel)
        }
        val fullText = "$header\n$detailsLines"
        val notification = NotificationCompat.Builder(context, CHANNEL_SCORES)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_scores_title))
            .setContentText(header)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAgendaIntent)
            .addAction(
                android.R.drawable.ic_menu_agenda,
                context.getString(R.string.notif_scores_action_agenda),
                openAgendaIntent,
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(8003, notification)
        }
    }

    fun postTestAlarmNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannels(context)

        val openAppIntent = openAppPendingIntent(context, 8099)
        val body = context.getString(R.string.notif_test_body)
        val notification = NotificationCompat.Builder(context, CHANNEL_DEPARTURE)
            .setSmallIcon(R.drawable.ic_notif_alarm)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(8099, notification)
            true
        }.getOrDefault(false)
    }

    fun buildGoogleMapsIntent(
        venueName: String?,
        latitude: Double?,
        longitude: Double?,
    ): Intent? {
        val url = com.example.domain.rules.NavigationUrlBuilder.googleMapsDirectionsUrl(
            venueName = venueName,
            latitude = latitude,
            longitude = longitude,
        ) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildWazeIntent(
        venueName: String?,
        latitude: Double?,
        longitude: Double?,
    ): Intent? {
        val url = com.example.domain.rules.NavigationUrlBuilder.wazeNavigationUrl(
            venueName = venueName,
            latitude = latitude,
            longitude = longitude,
        ) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openGoogleMaps(
        context: Context,
        venueName: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        val intent = buildGoogleMapsIntent(venueName, latitude, longitude) ?: return
        runCatching { context.startActivity(intent) }
    }

    fun openWaze(
        context: Context,
        venueName: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        val intent = buildWazeIntent(venueName, latitude, longitude) ?: return
        runCatching { context.startActivity(intent) }
    }

    private fun googleMapsPendingIntent(
        context: Context,
        activityId: String,
        venueName: String,
        latitude: Double?,
        longitude: Double?,
    ): PendingIntent? {
        val intent = buildGoogleMapsIntent(venueName, latitude, longitude) ?: return null
        return PendingIntent.getActivity(
            context,
            notificationId(activityId, 9),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun wazePendingIntent(
        context: Context,
        activityId: String,
        venueName: String,
        latitude: Double?,
        longitude: Double?,
    ): PendingIntent? {
        val intent = buildWazeIntent(venueName, latitude, longitude) ?: return null
        return PendingIntent.getActivity(
            context,
            notificationId(activityId, 11),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(
        context: Context,
        requestCode: Int,
        openTabRoute: String? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!openTabRoute.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_TAB, openTabRoute)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(activityId: String, salt: Int): Int =
        (activityId.hashCode() * 31 + salt) and 0x7FFFFFFF
}
