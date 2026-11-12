package com.habitramp.core.notification

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitramp.core.database.HabitDatabase
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.Units
import com.habitramp.data.repository.HabitRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReminderIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: HabitDatabase
    private lateinit var repository: HabitRepository
    private lateinit var scheduler: NotificationScheduler

    private var today: LocalDate = LocalDate.of(2026, 3, 2)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        today = LocalDate.of(2026, 3, 2)
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(db) { today }
        scheduler = NotificationScheduler(context, repository)
        runBlocking { repository.initialize() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun extendingPauseMovesTheNextReminderPastTheNewPauseEnd() = runBlocking {
        val habitId = repository
            .createHabit("Walk", BigDecimal.ONE, Units.DEFAULT.code)
            .getOrThrow()
        repository.updateNotifications(habitId, enabled = true, hour = 9, minute = 0)

        today = LocalDate.of(2026, 3, 3)
        repository.runDailyProcessing()
        assertTrue(repository.startPause(habitId, PauseType.WEEKLY))

        val now = LocalDate.of(2026, 3, 3).atTime(8, 0).atZone(ZoneId.systemDefault())
        var habit = repository.getHabit(habitId)!!
        var trigger = scheduler.nextTrigger(habit, now)
        assertNotNull(trigger)
        // The weekly pause covers March 3..9; the reminder waits for March 10.
        assertEquals(LocalDate.of(2026, 3, 10), trigger!!.toLocalDate())

        assertTrue(repository.extendPause(habitId))
        habit = repository.getHabit(habitId)!!
        trigger = scheduler.nextTrigger(habit, now)
        assertNotNull(trigger)
        // Extension moves the end to March 17, so no trigger is left inside the pause.
        assertEquals(LocalDate.of(2026, 3, 17), trigger!!.toLocalDate())
        assertFalse(trigger.toLocalDate().isBefore(LocalDate.of(2026, 3, 17)))
    }

    @Test
    fun reminderReceiverFinishesItsAsyncWork() {
        val latch = CountDownLatch(1)
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                latch.countDown()
            }
        }
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(NotificationScheduler.EXTRA_HABIT_ID, Long.MAX_VALUE)

        context.sendOrderedBroadcast(
            intent,
            null,
            resultReceiver,
            Handler(Looper.getMainLooper()),
            Activity.RESULT_OK,
            null,
            null
        )

        assertTrue(
            "ReminderReceiver did not call PendingResult.finish()",
            latch.await(20, TimeUnit.SECONDS)
        )
    }
}
