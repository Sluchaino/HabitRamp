package com.habitramp.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.habitramp.HabitRampApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HabitRampApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A rolled-back system clock must not crash the receiver.
                runCatching { app.container.repository.runDailyProcessing() }
                    .onFailure { Log.e(TAG, "Daily processing failed", it) }
                app.container.notificationScheduler.scheduleAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule notifications after boot/time change", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
