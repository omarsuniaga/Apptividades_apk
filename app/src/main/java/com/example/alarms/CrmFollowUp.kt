package com.example.alarms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.crm.CrmDatabase
import com.example.data.local.crm.RoomCrmRepository
import com.example.data.port.CrmFollowUpPort
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.rules.CrmProfile
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules a WorkManager reminder at 9:00 (Santo Domingo) on a CRM profile's next-contact date.
 * Dates already in the past are not scheduled: the CRM screen flags them as due instead.
 */
class CrmFollowUpScheduler(context: Context) : CrmFollowUpPort {

    private val appContext = context.applicationContext

    override fun schedule(profile: CrmProfile) {
        val workManager = WorkManager.getInstance(appContext)
        val date = profile.nextContactDate
        if (date == null) {
            workManager.cancelUniqueWork(workName(profile.accountKey))
            return
        }
        val triggerAt = ZonedDateTime.of(date, LocalTime.of(REMINDER_HOUR, 0), SANTO_DOMINGO_ZONE)
        val delayMs = triggerAt.toInstant().toEpochMilli() - System.currentTimeMillis()
        if (delayMs <= 0L) {
            workManager.cancelUniqueWork(workName(profile.accountKey))
            return
        }
        val request = OneTimeWorkRequestBuilder<CrmFollowUpWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(CrmFollowUpWorker.KEY_ACCOUNT_KEY to profile.accountKey))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(profile.accountKey), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(accountKey: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(accountKey))
    }

    private fun workName(accountKey: String) = "crm_followup_$accountKey"

    companion object {
        const val TAG = "crm_followup"
        private const val REMINDER_HOUR = 9
    }
}

class CrmFollowUpWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_ACCOUNT_KEY) ?: return Result.success()
        val repository = RoomCrmRepository(CrmDatabase.getInstance(applicationContext).crmDao())
        val profile = repository.getProfile(key) ?: return Result.success()
        // The reminder is cancelled/replaced when a contact is logged; stay silent if the date was cleared.
        if (profile.nextContactDate == null) return Result.success()

        NotificationHelper.postCrmFollowUpNotification(
            context = applicationContext,
            accountKey = key,
            displayName = profile.displayName,
        )
        return Result.success()
    }

    companion object {
        const val KEY_ACCOUNT_KEY = "account_key"
    }
}
