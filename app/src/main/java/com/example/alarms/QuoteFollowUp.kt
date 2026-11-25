package com.example.alarms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.quotes.QuotesDatabase
import com.example.data.local.quotes.RoomQuoteFunnelRepository
import com.example.data.port.QuoteFollowUpPort
import com.example.domain.model.SANTO_DOMINGO_ZONE
import com.example.domain.rules.DominicanFormatter
import com.example.domain.rules.SavedQuote
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules a WorkManager reminder at 9:00 (Santo Domingo) on a quote's follow-up date.
 * Dates already in the past are not scheduled: the funnel screen flags them as overdue instead.
 */
class QuoteFollowUpScheduler(context: Context) : QuoteFollowUpPort {

    private val appContext = context.applicationContext

    override fun schedule(quote: SavedQuote) {
        val workManager = WorkManager.getInstance(appContext)
        val followUp = quote.followUpDate
        if (!quote.status.isAwaitingAnswer || followUp == null) {
            workManager.cancelUniqueWork(workName(quote.id))
            return
        }
        val triggerAt = ZonedDateTime.of(followUp, LocalTime.of(REMINDER_HOUR, 0), SANTO_DOMINGO_ZONE)
        val delayMs = triggerAt.toInstant().toEpochMilli() - System.currentTimeMillis()
        if (delayMs <= 0L) {
            workManager.cancelUniqueWork(workName(quote.id))
            return
        }
        val request = OneTimeWorkRequestBuilder<QuoteFollowUpWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(QuoteFollowUpWorker.KEY_QUOTE_ID to quote.id))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(quote.id), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(quoteId: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(quoteId))
    }

    private fun workName(quoteId: String) = "quote_followup_$quoteId"

    companion object {
        const val TAG = "quote_followup"
        private const val REMINDER_HOUR = 9
    }
}

class QuoteFollowUpWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val quoteId = inputData.getString(KEY_QUOTE_ID) ?: return Result.success()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val repository = RoomQuoteFunnelRepository(
            QuotesDatabase.getInstance(applicationContext).savedQuoteDao(),
            moshi,
        )
        val saved = repository.getQuote(quoteId) ?: return Result.success()
        // Only remind while the quote is still waiting for an answer.
        if (!saved.status.isAwaitingAnswer) return Result.success()

        NotificationHelper.postQuoteFollowUpNotification(
            context = applicationContext,
            quoteId = quoteId,
            clientName = saved.quote.clientName,
            amountText = DominicanFormatter.formatMoney(saved.amount, saved.currency),
        )
        return Result.success()
    }

    companion object {
        const val KEY_QUOTE_ID = "quote_id"
    }
}
