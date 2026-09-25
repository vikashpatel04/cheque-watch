package com.chequetracker.watch.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chequetracker.watch.data.IST
import com.chequetracker.watch.data.Repository
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The only background work: one fetch, then done. Runs only when a network is
 * available (WorkManager waits otherwise, no retries while offline).
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Repository.refresh(applicationContext)
        // Keep the "just after midnight" refresh queued for the next night.
        RefreshScheduler.scheduleMidnight(applicationContext)
        // Always success, even on a failed fetch: no retry/backoff churn on the
        // battery. The next hourly run (or opening the app) tries again.
        return Result.success()
    }
}

object RefreshScheduler {
    private const val HOURLY = "refresh-hourly"
    private const val MIDNIGHT = "refresh-midnight"

    /** Midnight job runs a few minutes after 00:00 IST so yesterday's data clears. */
    private val MIDNIGHT_AT: LocalTime = LocalTime.of(0, 5)

    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Safe to call often (app start); existing schedules are kept. */
    fun ensureScheduled(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<RefreshWorker>(60, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HOURLY,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
        scheduleMidnight(context)
    }

    /**
     * One-off refresh at the next 00:05 IST. KEEP means an already-queued one
     * stays as is; once it has run, the next call (from any refresh) queues the
     * following night's.
     */
    fun scheduleMidnight(context: Context) {
        val now = ZonedDateTime.now(IST)
        var next = now.toLocalDate().atTime(MIDNIGHT_AT).atZone(IST)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next)

        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(connected)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT, ExistingWorkPolicy.KEEP, request)
    }
}
