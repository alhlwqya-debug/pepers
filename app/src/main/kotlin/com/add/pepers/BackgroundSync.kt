package com.add.pepers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal object BackgroundSyncScheduler {
    private const val WORK_NAME = "pepers_background_sync"
    private const val IMMEDIATE_WORK_NAME = "pepers_sync_now"

    fun requestNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun ensure(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

internal class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val stored = SupabaseSessionStore.load(context) ?: return Result.success()
        return try {
            val active = if (stored.expiresAt > 0L &&
                stored.expiresAt < System.currentTimeMillis() + 60_000L
            ) {
                SupabaseAuth.refresh(context, stored)
            } else {
                stored
            }

            // The background worker must operate on the same local database that
            // belongs to the authenticated Supabase user. This is essential after
            // reboot, process death, logout/login, or account switching.
            val activeUserId = LocalDatabaseAccountManager.activeUserId(context)
            if (activeUserId != active.userId) {
                LocalDatabaseAccountManager.activateUser(context, active.userId)
            }

            // Re-check the binding immediately before sync so a stale worker cannot
            // write another account's records if the active account changed.
            if (LocalDatabaseAccountManager.activeUserId(context) != active.userId) {
                return Result.retry()
            }

            SupabaseSyncManager.sync(context, active)
            Result.success()
        } catch (error: Exception) {
            android.util.Log.e("PepersSync", "Background cloud sync failed", error)
            val message = error.message.orEmpty()
            val retryable =
                error is java.io.IOException ||
                error is java.net.SocketTimeoutException ||
                message.contains("Supabase 408") ||
                message.contains("Supabase 429") ||
                message.contains("Supabase 5")
            if (retryable) Result.retry() else Result.failure()
        }
    }
}

internal class SyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        BackgroundSyncScheduler.ensure(appContext)
        WorkReminderScheduler.schedule(appContext)
    }
}
