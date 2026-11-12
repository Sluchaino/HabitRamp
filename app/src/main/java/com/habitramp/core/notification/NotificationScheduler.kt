package com.habitramp.core.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.habitramp.MainActivity
import com.habitramp.R
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.PauseStatus
import com.habitramp.core.util.formatWithUnit
import com.habitramp.data.repository.HabitRepository
import com.habitramp.domain.model.Habit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationScheduler(
    private val context: Context,
    private val repository: HabitRepository
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания о привычках",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Ежедневные напоминания выполнить цель"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun scheduleAll() {
        repository.getAllHabits().forEach { habit ->
            if (habit.notificationsEnabled && habit.isLive()) {
                schedule(habit)
            } else {
                cancel(habit.id)
            }
        }
    }

    suspend fun schedule(habit: Habit) {
        cancel(habit.id)
        if (!habit.notificationsEnabled || !habit.isLive()) return
        val now = ZonedDateTime.now()
        val next = nextTrigger(habit, now) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_HABIT_ID, habit.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            habit.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = next.toInstant().toEpochMilli()
        setAlarm(triggerAt, pendingIntent)
    }

    /**
     * Exact alarms need a special user-granted access on Android 12+. Without it
     * the reminder is still delivered, only the system may batch/delay it, so we
     * fall back to an inexact alarm instead of asking for the permission.
     */
    private fun setAlarm(triggerAt: Long, pendingIntent: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm rejected, falling back to inexact", e)
            }
        }
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: Exception) {
            // A missing permission or an invalid trigger must not crash the caller.
            Log.e(TAG, "Failed to schedule a reminder", e)
        }
    }

    fun cancel(habitId: Long) {
        val pendingIntent = reminderPendingIntent(habitId) ?: return
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            // Cancelling is best effort: never let it break the caller's data write.
            Log.w(TAG, "Failed to cancel reminder for habit $habitId", e)
        } finally {
            // Drop the PendingIntent itself so no orphaned reminder stays registered.
            runCatching { pendingIntent.cancel() }
        }
    }

    /** Visible for integration tests: whether a reminder is still registered. */
    internal fun isReminderRegistered(habitId: Long): Boolean = reminderPendingIntent(habitId) != null

    /**
     * Looks up an already created reminder without allocating a new one, so a
     * missing alarm is left untouched and no side effects happen on cancellation.
     */
    private fun reminderPendingIntent(habitId: Long): PendingIntent? = try {
        PendingIntent.getBroadcast(
            context,
            habitId.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to look up reminder for habit $habitId", e)
        null
    }

    /** Visible for integration tests: the next moment a reminder may actually fire. */
    internal suspend fun nextTrigger(habit: Habit, now: ZonedDateTime): ZonedDateTime? {
        if (habit.status == HabitStatus.ARCHIVED || habit.status == HabitStatus.COMPLETED) return null
        val activePauses = repository.getPauses(habit.id)
            .filter { it.status == com.habitramp.core.model.PauseStatus.ACTIVE }
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(habit.notificationHour, habit.notificationMinute)
        var date: LocalDate = now.toLocalDate()
        repeat(400) {
            val paused = activePauses.any { it.covers(date) }
            if (!paused && habit.isScheduledOn(date)) {
                val candidate = date.atTime(time).atZone(zone)
                if (candidate.isAfter(now)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    suspend fun showNotification(habit: Habit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (!habit.notificationsEnabled || habit.status != HabitStatus.ACTIVE) return
        val today = LocalDate.now()
        if (!habit.isScheduledOn(today)) return
        val pausedToday = repository.getPauses(habit.id).any {
            it.status == PauseStatus.ACTIVE && it.covers(today)
        }
        if (pausedToday) return

        val text = "Сегодня: ${habit.currentGoal.formatWithUnit(habit.unit).trimEnd('.')}. " +
            "Пора выполнить свою цель!"
        val contentIntent = PendingIntent.getActivity(
            context,
            habit.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_HABIT_ID, habit.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(habit.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the checks.
        }
    }

    companion object {
        const val CHANNEL_ID = "habit_reminders"
        const val EXTRA_HABIT_ID = "habit_id"
        private const val TAG = "NotificationScheduler"
    }
}
