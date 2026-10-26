package com.hospital.spd.mobile

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class MobileSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The WebView queue performs validate -> user confirmation -> submit. This worker only wakes it.
        return Result.success()
    }
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MobileSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("spd-mobile-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
