package com.habitramp.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.habitramp.HabitRampApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(NotificationScheduler.EXTRA_HABIT_ID, -1L)
        if (habitId <= 0) return
        val app = context.applicationContext as HabitRampApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A rolled-back system clock must not crash the receiver.
                runCatching { app.container.repository.runDailyProcessing() }
                    .onFailure { Log.e(TAG, "Daily processing failed", it) }
                val habit = app.container.repository.getHabit(habitId)
                if (habit != null) {
                    app.container.notificationScheduler.showNotification(habit)
                    app.container.notificationScheduler.schedule(habit)
                } else {
                    // A stale alarm for an already deleted habit: drop any leftovers
                    // instead of rescheduling and let the receiver finish quietly.
                    app.container.notificationScheduler.cancel(habitId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle reminder for habit $habitId", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ReminderReceiver"
    }
}
