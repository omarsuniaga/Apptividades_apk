package com.example.debug

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import com.example.alarms.NotificationHelper

private const val TAG = "TestAlarm"
private const val REQUEST_CODE = 9901

/**
 * Debug builds only. Schedules a fake "leave now" alert the same way the real departure alarm
 * is scheduled (AlarmManager.setAlarmClock), without creating any activity. Trigger it with:
 * adb shell am broadcast -n <package>/com.example.debug.TestAlarmSchedulerReceiver --ei minutes 3
 */
class TestAlarmSchedulerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra("minutes", 3).coerceIn(1, 60)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms are not allowed")
            return
        }
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val fire = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TestAlarmFireReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
        Log.i(TAG, "Test alarm scheduled in $minutes min at $triggerAt")
    }
}

class TestAlarmFireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Test alarm fired at ${System.currentTimeMillis()}")
        NotificationHelper.postLeaveNotification(
            context = context,
            activityId = "debug-test-alarm",
            venueName = "PRUEBA de alarma",
            latitude = null,
            longitude = null,
            travelMinutes = 0,
            arrivalTimeLabel = "--:--",
        )
    }
}
