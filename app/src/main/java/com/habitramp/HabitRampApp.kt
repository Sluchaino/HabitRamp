package com.habitramp

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.habitramp.core.database.HabitDatabase
import com.habitramp.core.notification.DailyMaintenanceWorker
import com.habitramp.core.notification.NotificationScheduler
import com.habitramp.data.prefs.AppPreferences
import com.habitramp.data.repository.HabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext
    val database: HabitDatabase = HabitDatabase.build(appContext)
    val repository: HabitRepository = HabitRepository(database)
    val notificationScheduler: NotificationScheduler = NotificationScheduler(appContext, repository)
    val preferences: AppPreferences = AppPreferences(appContext)
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        notificationScheduler.createChannel()
        applicationScope.launch {
            repository.initialize()
            notificationScheduler.scheduleAll()
            scheduleDailyMaintenance()
        }
    }

    private fun scheduleDailyMaintenance() {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(0, 5)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            "daily_habit_maintenance",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}

class HabitRampApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
