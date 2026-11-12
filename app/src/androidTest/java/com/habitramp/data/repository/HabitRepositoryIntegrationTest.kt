package com.habitramp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitramp.core.database.HabitDatabase
import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.EventType
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.PauseStatus
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.Units
import com.habitramp.data.entity.UserProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Room + repository integration tests for the failure-prone daily processing paths.
 * The clock is injected so every scenario can advance several days without waiting.
 */
@RunWith(AndroidJUnit4::class)
class HabitRepositoryIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: HabitDatabase
    private lateinit var repository: HabitRepository

    /** Monday, the first day of the test week. */
    private var today: LocalDate = LocalDate.of(2026, 3, 2)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        today = LocalDate.of(2026, 3, 2)
        db = Room.inMemoryDatabaseBuilder(context, HabitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(db) { today }
        runBlocking { repository.initialize() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------ idempotency

    @Test
    fun repeatedCompletionCreatesOneRecordAndCreditsPointsOnce() = runBlocking {
        val habitId = createHabit("Read")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))
        assertFalse(repository.recordCompletion(habitId, null))
        assertFalse(repository.recordCompletion(habitId, BigDecimal.ONE))
        repository.runDailyProcessing()

        val records = repository.getRecords(habitId)
        assertEquals(1, records.size)
        assertEquals(LocalDate.of(2026, 3, 3), records.single().date)
        assertEquals(CompletionStatus.COMPLETED, records.single().status)
        assertDecimal("1", records.single().plannedValue)
        assertDecimal("1", records.single().actualValue)

        val habit = repository.getHabit(habitId)!!
        assertEquals(0, habit.cycleIndex)
        assertEquals(1, habit.dayIndex)
        assertDecimal("2", habit.currentGoal)
        assertEquals(1, repository.observeUserProgress().first().currentPoints)
    }

    @Test
    fun repeatedCompletionDoesNotDuplicatePointsAcrossFollowingDays() = runBlocking {
        val habitId = createHabit("Read")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))
        assertFalse(repository.recordCompletion(habitId, null))

        advanceTo(LocalDate.of(2026, 3, 4))
        assertTrue(repository.recordCompletion(habitId, null))

        assertEquals(2, repository.getRecords(habitId).size)
        assertEquals(2, repository.observeUserProgress().first().currentPoints)
    }

    // ---------------------------------------------------------- deferred reset

    @Test
    fun twoDeferredResetsApplyOnlyTheCurrentPlan() = runBlocking {
        val habitId = createHabit("Deep work", initial = "10")

        assertDecimal("5", repository.manualReset(habitId)!!)
        assertDecimal("5", repository.manualReset(habitId)!!)

        var habit = repository.getHabit(habitId)!!
        assertDecimal("10", habit.currentGoal)
        assertEquals(LocalDate.of(2026, 3, 3), habit.pendingResetDate)
        assertTrue(repository.getManualResets(habitId).isEmpty())

        advanceTo(LocalDate.of(2026, 3, 3))

        habit = repository.getHabit(habitId)!!
        assertDecimal("5", habit.currentGoal)
        assertNull(habit.pendingResetDate)
        assertNull(habit.pendingResetValue)
        val resets = repository.getManualResets(habitId)
        assertEquals(1, resets.size)
        assertEquals(LocalDate.of(2026, 3, 3), resets.single().effectiveDate)
    }

    @Test
    fun pendingResetIsAppliedBeforeTheMissedDayItBelongsTo() = runBlocking {
        val habitId = createHabit("Study")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))
        assertDecimal("1", repository.manualReset(habitId)!!)

        // The app was not opened on March 4 and 5.
        advanceTo(LocalDate.of(2026, 3, 6))

        val records = repository.getRecords(habitId)
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 5)
            ),
            records.map { it.date }
        )
        assertDecimal("1", records[0].plannedValue)
        assertEquals(CompletionStatus.COMPLETED, records[0].status)
        assertDecimal("1", records[1].plannedValue)
        assertEquals(CompletionStatus.MISSED, records[1].status)
        assertDecimal("2", records[2].plannedValue)
        assertEquals(CompletionStatus.MISSED, records[2].status)

        val resets = repository.getManualResets(habitId)
        assertEquals(1, resets.size)
        assertEquals(LocalDate.of(2026, 3, 4), resets.single().effectiveDate)
        assertDecimal("3", repository.getHabit(habitId)!!.currentGoal)
        assertEquals(LocalDate.of(2026, 3, 5), repository.getHabit(habitId)!!.lastProcessedDate)
    }

    // --------------------------------------------------------- missed days

    @Test
    fun severalMissedDaysAreRecordedChronologicallyWithPenalties() = runBlocking {
        val habitId = createHabit("Walk")

        // Days March 3, 4 and 5 pass while the app stays closed.
        advanceTo(LocalDate.of(2026, 3, 6))

        val records = repository.getRecords(habitId)
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 5)
            ),
            records.map { it.date }
        )
        assertTrue(records.all { it.status == CompletionStatus.MISSED })
        assertDecimal("1", records[0].plannedValue)
        assertDecimal("2", records[1].plannedValue)
        assertDecimal("3", records[2].plannedValue)
        assertEquals(0, records[0].cycleIndex)
        assertEquals(0, records[0].dayIndex)
        assertEquals(0, records[1].cycleIndex)
        assertEquals(1, records[1].dayIndex)
        assertEquals(0, records[2].cycleIndex)
        assertEquals(2, records[2].dayIndex)

        val misses = repository.observeEvents(habitId).first().filter { it.type == EventType.MISS }
        assertEquals(3, misses.size)
        assertEquals(records.map { it.date }, misses.map { it.effectiveDate })

        val habit = repository.getHabit(habitId)!!
        assertDecimal("4", habit.currentGoal)
        assertEquals(LocalDate.of(2026, 3, 5), habit.lastProcessedDate)

        val progress = repository.observeUserProgress().first()
        assertEquals(0, progress.currentPoints)
        assertEquals(LocalDate.of(2026, 3, 5), progress.lastProcessedDate)

        assertTrue(repository.recordCompletion(habitId, null))
        assertEquals(1, repository.observeUserProgress().first().currentPoints)
    }

    @Test
    fun pauseDaysDoNotBecomeMissesAndProcessingResumesAfterPause() = runBlocking {
        val habitId = createHabit("Stretch")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.startPause(habitId, PauseType.WEEKLY))
        assertEquals(HabitStatus.PAUSED, repository.getHabit(habitId)!!.status)

        // The weekly pause covers March 3..9. The app opens again on March 12.
        advanceTo(LocalDate.of(2026, 3, 12))

        val records = repository.getRecords(habitId)
        assertEquals(
            listOf(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11)),
            records.map { it.date }
        )
        assertTrue(records.all { it.status == CompletionStatus.MISSED })

        val habit = repository.getHabit(habitId)!!
        assertEquals(HabitStatus.ACTIVE, habit.status)
        assertEquals(LocalDate.of(2026, 3, 11), habit.lastProcessedDate)
        assertEquals(PauseStatus.ENDED, repository.getPauses(habitId).single().status)
        assertTrue(repository.observeEvents(habitId).first().any { it.type == EventType.PAUSE_ENDED })
    }

    // ---------------------------------------------------------- clock changes

    @Test
    fun clockRollbackDoesNotCorruptStateAndRecoversAfterClockReturns() = runBlocking {
        val habitId = createHabit("Journal")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))

        val recordsBefore = repository.getRecords(habitId)
        val habitBefore = repository.getHabit(habitId)!!
        val progressBefore = repository.observeUserProgress().first()

        // Simulate a westbound time-zone jump / manual clock rollback.
        today = LocalDate.of(2026, 3, 2)
        repository.initialize()
        repository.runDailyProcessing()

        assertEquals(recordsBefore, repository.getRecords(habitId))
        assertEquals(habitBefore, repository.getHabit(habitId))
        assertEquals(progressBefore, repository.observeUserProgress().first())

        val rollbackError = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.recordCompletion(habitId, null) }
        }
        assertTrue(rollbackError.message.orEmpty().contains("Системная дата"))
        assertEquals(recordsBefore, repository.getRecords(habitId))

        // Only the guarded mutation is rejected; the app remains readable.
        assertEquals(HabitStatus.ACTIVE, repository.getHabit(habitId)!!.status)
        assertEquals(1, repository.observeLiveHabits().first().size)

        // Once the local date catches up, the guarded operation works again.
        advanceTo(LocalDate.of(2026, 3, 3))
        assertFalse(repository.recordCompletion(habitId, null))
        assertEquals(progressBefore.currentPoints, repository.observeUserProgress().first().currentPoints)
    }

    @Test
    fun forwardTimezoneShiftIsProcessedAsANormalDayChange() = runBlocking {
        val habitId = createHabit("Journal")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))

        // Eastbound travel can move the local date one day forward.
        advanceTo(LocalDate.of(2026, 3, 4))
        assertTrue(repository.recordCompletion(habitId, null))

        assertEquals(2, repository.getRecords(habitId).size)
        assertEquals(2, repository.observeUserProgress().first().currentPoints)
    }

    // --------------------------------------------------- lifecycle and slots

    @Test
    fun archivingFreesTheSlotAndPreservesHistory() = runBlocking {
        val habitId = createHabit("Old habit")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))
        advanceTo(LocalDate.of(2026, 3, 4))

        assertTrue(repository.archiveHabit(habitId))
        assertFalse(repository.archiveHabit(habitId))

        val archived = repository.getHabit(habitId)!!
        assertEquals(HabitStatus.ARCHIVED, archived.status)
        assertEquals(LocalDate.of(2026, 3, 4), archived.archivedAt)
        assertEquals(0, repository.observeActiveCount().first())
        assertEquals(1, repository.getRecords(habitId).size)
        assertTrue(repository.observeEvents(habitId).first().any { it.type == EventType.HABIT_ARCHIVED })

        // The single starting slot is available again.
        val replacementId = createHabit("New habit")
        assertTrue(replacementId > 0)
        assertEquals(1, repository.observeActiveCount().first())
    }

    @Test
    fun completingHabitFreesTheSlotAndPreservesHistory() = runBlocking {
        val habitId = createHabit("Course")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))
        advanceTo(LocalDate.of(2026, 3, 4))

        assertTrue(repository.completeHabit(habitId))
        assertFalse(repository.completeHabit(habitId))

        val completed = repository.getHabit(habitId)!!
        assertEquals(HabitStatus.COMPLETED, completed.status)
        assertEquals(LocalDate.of(2026, 3, 4), completed.completedAt)
        assertEquals(0, repository.observeActiveCount().first())
        assertEquals(1, repository.getRecords(habitId).size)
        assertTrue(repository.observeEvents(habitId).first().any { it.type == EventType.HABIT_COMPLETED })

        assertTrue(createHabit("Next course") > 0)
    }

    @Test
    fun twoHabitsAccumulateAndResetDailyPointsCorrectly() = runBlocking {
        db.userProgressDao().upsert(
            UserProgressEntity(
                id = 1,
                currentPoints = 0,
                unlockedSlots = 2,
                lastProcessedDate = today.toEpochDay()
            )
        )
        val first = createHabit("First")
        val second = createHabit("Second")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(first, null))
        assertTrue(repository.recordCompletion(second, null))
        assertEquals(2, repository.observeUserProgress().first().currentPoints)
        assertEquals(1, repository.getRecords(first).size)
        assertEquals(1, repository.getRecords(second).size)

        // Only one of two habits is completed on March 4.
        advanceTo(LocalDate.of(2026, 3, 4))
        assertTrue(repository.recordCompletion(first, null))
        assertEquals(2, repository.observeUserProgress().first().currentPoints)

        // March 4 becomes a past day with one miss: the daily counter resets.
        advanceTo(LocalDate.of(2026, 3, 5))
        val secondRecords = repository.getRecords(second)
        assertEquals(CompletionStatus.MISSED, secondRecords.last().status)
        assertEquals(LocalDate.of(2026, 3, 4), secondRecords.last().date)

        val progress = repository.observeUserProgress().first()
        assertEquals(0, progress.currentPoints)
        assertEquals(LocalDate.of(2026, 3, 4), progress.lastProcessedDate)
        assertEquals(2, progress.unlockedSlots)

        // Completing both habits on March 5 earns the points again.
        assertTrue(repository.recordCompletion(first, null))
        assertTrue(repository.recordCompletion(second, null))
        assertEquals(2, repository.observeUserProgress().first().currentPoints)
    }

    @Test
    fun changingScheduleDoesNotRewritePastRecords() = runBlocking {
        val habitId = createHabit("Language")

        advanceTo(LocalDate.of(2026, 3, 3))
        assertTrue(repository.recordCompletion(habitId, null))

        // March 4 is missed, then the schedule is changed on March 5.
        advanceTo(LocalDate.of(2026, 3, 5))
        repository.updateWeekdays(habitId, setOf(DayOfWeek.THURSDAY))

        val firstDay = repository.getRecords(habitId).first { it.date == LocalDate.of(2026, 3, 3) }
        assertEquals(CompletionStatus.COMPLETED, firstDay.status)
        assertDecimal("1", firstDay.plannedValue)
        assertDecimal("1", firstDay.actualValue)

        val missedDay = repository.getRecords(habitId).first { it.date == LocalDate.of(2026, 3, 4) }
        assertEquals(CompletionStatus.MISSED, missedDay.status)
        assertDecimal("2", missedDay.plannedValue)

        val habit = repository.getHabit(habitId)!!
        assertEquals(setOf(DayOfWeek.THURSDAY), habit.weekdays)
        assertEquals(LocalDate.of(2026, 3, 5), repository.getSettings(habitId).last().effectiveDate)
        assertEquals(LocalDate.of(2026, 3, 4), habit.lastProcessedDate)

        repository.runDailyProcessing()
        assertEquals(2, repository.getRecords(habitId).size)
        assertEquals(CompletionStatus.MISSED, repository.getRecords(habitId)[1].status)
    }

    @Test
    fun progressSurvivesDatabaseReopen() = runBlocking {
        val databaseName = "habitramp-restart-${System.currentTimeMillis()}.db"
        context.deleteDatabase(databaseName)

        var fileDb: HabitDatabase? = null
        try {
            fileDb = Room.databaseBuilder(context, HabitDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            var fileRepository = HabitRepository(fileDb) { today }
            fileRepository.initialize()
            val habitId = fileRepository
                .createHabit("Persisted", BigDecimal.ONE, Units.DEFAULT.code)
                .getOrThrow()

            today = LocalDate.of(2026, 3, 3)
            fileRepository.runDailyProcessing()
            assertTrue(fileRepository.recordCompletion(habitId, null))
            fileDb.close()

            // Reopen the same database and create a fresh repository instance.
            fileDb = Room.databaseBuilder(context, HabitDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            fileRepository = HabitRepository(fileDb) { today }
            fileRepository.initialize()

            val habit = fileRepository.getHabit(habitId)
            assertNotNull(habit)
            assertEquals(1, fileRepository.getRecords(habitId).size)
            assertEquals(1, fileRepository.observeUserProgress().first().currentPoints)
            assertEquals(LocalDate.of(2026, 3, 3), habit!!.lastProcessedDate)
        } finally {
            fileDb?.close()
            context.deleteDatabase(databaseName)
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun createHabit(name: String, initial: String = "1"): Long =
        repository.createHabit(name, BigDecimal(initial), Units.DEFAULT.code).getOrThrow()

    private suspend fun advanceTo(date: LocalDate) {
        today = date
        repository.runDailyProcessing()
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(
            "expected $expected but was ${actual.toPlainString()}",
            0,
            actual.compareTo(BigDecimal(expected))
        )
    }
}
