package com.habitramp.core.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.habitramp.HabitRampApp

class DailyMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HabitRampApp
        return try {
            app.container.repository.runDailyProcessing()
            app.container.notificationScheduler.scheduleAll()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily maintenance failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "DailyMaintenanceWorker"
    }
}
