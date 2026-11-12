package com.habitramp.data.repository

import androidx.room.withTransaction
import com.habitramp.core.database.HabitDatabase
import com.habitramp.core.model.CompletionStatus
import com.habitramp.core.model.EventType
import com.habitramp.core.model.HabitStatus
import com.habitramp.core.model.MeasurementUnit
import com.habitramp.core.model.MissPolicy
import com.habitramp.core.model.PauseStatus
import com.habitramp.core.model.PauseType
import com.habitramp.core.model.TargetBehavior
import com.habitramp.core.model.Units
import com.habitramp.core.util.DatePeriod
import com.habitramp.core.util.currentMonthPeriod
import com.habitramp.core.util.currentWeekPeriod
import com.habitramp.core.util.previousMonthComparablePeriod
import com.habitramp.core.util.previousWeekComparablePeriod
import com.habitramp.core.util.roundActualToUnit
import com.habitramp.core.util.roundToUnit
import com.habitramp.core.util.toDecimalOrNull
import com.habitramp.core.util.toLocalDate
import com.habitramp.core.util.toStorage
import com.habitramp.data.entity.HabitDailyRecordEntity
import com.habitramp.data.entity.HabitEntity
import com.habitramp.data.entity.HabitEventEntity
import com.habitramp.data.entity.HabitPauseEntity
import com.habitramp.data.entity.HabitSettingsHistoryEntity
import com.habitramp.data.entity.UserProgressEntity
import com.habitramp.data.mapper.decodeWeekdays
import com.habitramp.data.mapper.toDomain
import com.habitramp.data.mapper.toEntity
import com.habitramp.domain.calculator.MissCalculator
import com.habitramp.domain.calculator.PauseCalculator
import com.habitramp.domain.calculator.PeriodStatistics
import com.habitramp.domain.calculator.ProgressionCalculator
import com.habitramp.domain.calculator.ResetCalculator
import com.habitramp.domain.calculator.StatisticsCalculator
import com.habitramp.domain.calculator.UnlockCalculator
import com.habitramp.domain.model.DailyRecord
import com.habitramp.domain.model.Habit
import com.habitramp.domain.model.HabitEvent
import com.habitramp.domain.model.HabitPause
import com.habitramp.domain.model.ManualResetEvent
import com.habitramp.domain.model.ProgressionState
import com.habitramp.domain.model.SettingsSnapshot
import com.habitramp.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

class HabitRepository(
    private val db: HabitDatabase,
    private val clock: () -> LocalDate = { LocalDate.now() }
) {
    private val habitDao = db.habitDao()
    private val recordDao = db.dailyRecordDao()
    private val eventDao = db.eventDao()
    private val pauseDao = db.pauseDao()
    private val settingsDao = db.settingsHistoryDao()
    private val progressDao = db.userProgressDao()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ------------------------------------------------------------------ flows

    fun observeAllHabits(): Flow<List<Habit>> =
        habitDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeLiveHabits(): Flow<List<Habit>> =
        habitDao.observeLive().map { list -> list.map { it.toDomain() } }

    fun observeHabit(id: Long): Flow<Habit?> =
        habitDao.observeById(id).map { it?.toDomain() }

    fun observeActiveCount(): Flow<Int> = habitDao.observeActiveCount()

    fun observeRecords(habitId: Long): Flow<List<DailyRecord>> =
        recordDao.observeByHabit(habitId).map { list -> list.map { it.toDomain() } }

    fun observePauses(habitId: Long): Flow<List<HabitPause>> =
        pauseDao.observeByHabit(habitId).map { list -> list.map { it.toDomain() } }

    fun observeSettings(habitId: Long): Flow<List<SettingsSnapshot>> =
        settingsDao.observeByHabit(habitId).map { list -> list.map { it.toDomain() } }

    fun observeEvents(habitId: Long): Flow<List<HabitEvent>> =
        eventDao.observeByHabit(habitId).map { list -> list.map { it.toDomain() } }

    fun observeUserProgress(): Flow<UserProgress> =
        progressDao.observe().map { entity ->
            entity?.toDomain() ?: UserProgress(0, 1, clock())
        }

    // ------------------------------------------------------------------ reads

    suspend fun getHabit(id: Long): Habit? = habitDao.getById(id)?.toDomain()

    suspend fun getLiveHabits(): List<Habit> = habitDao.getLive().map { it.toDomain() }

    suspend fun getAllHabits(): List<Habit> = habitDao.getAll().map { it.toDomain() }

    suspend fun getRecords(habitId: Long): List<DailyRecord> =
        recordDao.getByHabit(habitId).map { it.toDomain() }

    suspend fun getPauses(habitId: Long): List<HabitPause> =
        pauseDao.getByHabit(habitId).map { it.toDomain() }

    suspend fun getSettings(habitId: Long): List<SettingsSnapshot> =
        settingsDao.getByHabit(habitId).map { it.toDomain() }

    suspend fun getManualResets(habitId: Long): List<ManualResetEvent> =
        eventDao.getByType(habitId, EventType.MANUAL_RESET.code).mapNotNull { entity ->
            runCatching {
                val payload = json.decodeFromString<EventPayload>(entity.eventData)
                val goal = payload.newGoal.toDecimalOrNull() ?: return@runCatching null
                ManualResetEvent(entity.effectiveDate.toLocalDate(), goal)
            }.getOrNull()
        }

    suspend fun getStatistics(
        habitId: Long,
        period: StatisticsPeriod
    ): PeriodStatistics? {
        val habit = getHabit(habitId) ?: return null
        val today = clock()
        val currentPeriod: DatePeriod = when (period) {
            StatisticsPeriod.WEEK -> currentWeekPeriod(today)
            StatisticsPeriod.MONTH -> currentMonthPeriod(today)
        }
        val previousPeriod: DatePeriod = when (period) {
            StatisticsPeriod.WEEK -> previousWeekComparablePeriod(today)
            StatisticsPeriod.MONTH -> previousMonthComparablePeriod(today)
        }
        return StatisticsCalculator.calculate(
            habit = habit,
            records = getRecords(habitId),
            pauses = getPauses(habitId),
            settings = getSettings(habitId),
            manualResets = getManualResets(habitId),
            period = currentPeriod,
            previousPeriod = previousPeriod
        )
    }

    suspend fun previewReset(habitId: Long): BigDecimal? {
        val habit = getHabit(habitId) ?: return null
        return ResetCalculator.newGoal(habit.currentGoal, habit.unit)
    }

    // -------------------------------------------------------------- lifecycle

    /** Creates the singleton progress row and catches up on all missed dates. */
    suspend fun initialize() {
        db.withTransaction {
            ensureProgress(clock())
        }
        runDailyProcessing()
    }

    suspend fun runDailyProcessing() {
        db.withTransaction {
            val today = clock()
            ensureProgress(today)
            processPauses(today)
            processMissedDays(today)
            applyPendingResets(today)
            evaluatePoints(today)
        }
    }

    private suspend fun ensureProgress(today: LocalDate) {
        if (progressDao.get() == null) {
            progressDao.upsert(
                UserProgressEntity(
                    id = 1,
                    currentPoints = 0,
                    unlockedSlots = 1,
                    lastProcessedDate = today.toEpochDay()
                )
            )
        }
    }

    private suspend fun processPauses(today: LocalDate) {
        for (entity in habitDao.getLive()) {
            val active = pauseDao.getActive(entity.id) ?: continue
            val habit = entity.toDomain()
            val pause = active.toDomain()
            val evaluationDate = if (today.isBefore(pause.endDate)) today else pause.endDate
            val periods = PauseCalculator.periodsElapsed(
                pause.startDate,
                evaluationDate,
                pause.pauseType
            )
            var state = ProgressionState.from(habit)
            var processed = active.processedPeriods
            if (periods > processed) {
                state = PauseCalculator.applyPeriods(
                    state,
                    periods - processed,
                    pause.pauseType,
                    habit.unit
                )
                processed = periods
                insertEvent(
                    habitId = habit.id,
                    type = EventType.PAUSE_PERIOD_PROCESSED,
                    effectiveDate = evaluationDate,
                    payload = EventPayload(
                        pauseType = pause.pauseType.code,
                        periods = periods
                    )
                )
            }
            val ended = !today.isBefore(pause.endDate)
            if (ended) {
                state = PauseCalculator.finish(state, habit.unit)
                val updated = habit.withState(state).copy(
                    status = HabitStatus.ACTIVE,
                    lastProcessedDate = maxOf(habit.lastProcessedDate, pause.endDate.minusDays(1))
                )
                habitDao.update(updated.toEntity())
                pauseDao.update(active.copy(processedPeriods = processed, status = PauseStatus.ENDED.code))
                insertEvent(
                    habitId = habit.id,
                    type = EventType.PAUSE_ENDED,
                    effectiveDate = pause.endDate,
                    payload = EventPayload(pauseType = pause.pauseType.code)
                )
            } else {
                habitDao.update(habit.withState(state).toEntity())
                pauseDao.update(active.copy(processedPeriods = processed))
            }
        }
    }

    /**
     * Applies a deferred reset that became due today but was not reached by
     * [processMissedDays] (for example, when the habit was paused on the
     * originally planned date). Resets missed in the past are applied
     * chronologically while catch-up processing walks those dates.
     */
    private suspend fun applyPendingResets(today: LocalDate) {
        for (entity in habitDao.getLive()) {
            val habit = entity.toDomain()
            if (habit.status != HabitStatus.ACTIVE) continue
            val pendingDate = habit.pendingResetDate ?: continue
            if (today.isBefore(pendingDate)) continue
            val updated = applyPendingResetIfDue(habit, today)
            if (updated != habit) habitDao.update(updated.toEntity())
        }
    }

    private suspend fun processMissedDays(today: LocalDate) {
        for (entity in habitDao.getLive()) {
            var habit = entity.toDomain()
            val pauses = pauseDao.getByHabit(habit.id).map { it.toDomain() }
            var last = habit.lastProcessedDate
            var date = last.plusDays(1)
            var changed = false

            while (date.isBefore(today)) {
                if (pauses.any { it.covers(date) }) {
                    last = date
                    date = date.plusDays(1)
                    continue
                }
                if (habit.isScheduledOn(date)) {
                    val existing = recordDao.getForDate(habit.id, date.toEpochDay())
                    if (existing == null) {
                        habit = applyPendingResetIfDue(habit, date)
                        val planned = habit.currentGoal
                        recordDao.insert(
                            HabitDailyRecordEntity(
                                habitId = habit.id,
                                date = date.toEpochDay(),
                                plannedValue = planned.toStorage(),
                                actualValue = "0",
                                completionStatus = CompletionStatus.MISSED.code,
                                cycleIndex = habit.cycleIndex,
                                dayIndex = habit.dayIndex,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        val state = MissCalculator.applyMiss(
                            state = ProgressionState.from(habit),
                            policy = habit.missPolicy,
                            resetValue = habit.resetValue,
                            penalty = habit.missPenalty,
                            unit = habit.unit
                        )
                        habit = habit.withState(state)
                        insertEvent(
                            habitId = habit.id,
                            type = EventType.MISS,
                            effectiveDate = date,
                            payload = EventPayload(
                                actual = "0",
                                policy = habit.missPolicy.code
                            )
                        )
                        changed = true
                    }
                }
                last = date
                date = date.plusDays(1)
            }

            if (changed || last != habit.lastProcessedDate) {
                habitDao.update(habit.copy(lastProcessedDate = last).toEntity())
            }
        }
    }

    private suspend fun evaluatePoints(today: LocalDate) {
        val progress = progressDao.get() ?: return
        if (progress.lastProcessedDate >= today.toEpochDay()) return

        var points = progress.currentPoints
        var slots = progress.unlockedSlots
        var last = progress.lastProcessedDate
        var date = last.toLocalDate().plusDays(1)

        while (!date.isAfter(today)) {
            val evaluation = evaluateDate(date)
            val earned = UnlockCalculator.pointsForDay(
                evaluation.scheduled,
                evaluation.completed
            )
            if (date == today) {
                if (evaluation.scheduled > 0 && evaluation.allRecorded) {
                    if (earned != null) {
                        points = if (earned == 0) 0 else points + earned
                        slots = maxOf(slots, UnlockCalculator.slotsForPoints(points))
                    }
                    last = date.toEpochDay()
                }
                break
            } else {
                if (earned != null) {
                    points = if (earned == 0) 0 else points + earned
                    slots = maxOf(slots, UnlockCalculator.slotsForPoints(points))
                }
                last = date.toEpochDay()
            }
            date = date.plusDays(1)
        }

        if (points != progress.currentPoints || slots != progress.unlockedSlots ||
            last != progress.lastProcessedDate
        ) {
            progressDao.upsert(
                UserProgressEntity(
                    id = 1,
                    currentPoints = points,
                    unlockedSlots = slots,
                    lastProcessedDate = last
                )
            )
        }
    }

    private suspend fun evaluateDate(date: LocalDate): DayEvaluation {
        val day = date.toEpochDay()
        var scheduled = 0
        var completed = 0
        var allRecorded = true

        for (entity in habitDao.getAll()) {
            if (entity.createdAt > day || entity.startDate > day) continue
            if (entity.archivedAt != null && entity.archivedAt <= day) continue
            if (entity.completedAt != null && entity.completedAt < day) continue
            val pauses = pauseDao.getByHabit(entity.id)
            if (pauses.any { day >= it.startDate && day < it.endDate }) continue
            if (date.dayOfWeek !in weekdaysOn(entity, date)) continue

            scheduled++
            val record = recordDao.getForDate(entity.id, day)
            if (record == null) {
                allRecorded = false
            } else if (record.completionStatus == CompletionStatus.COMPLETED.code) {
                completed++
            }
        }
        return DayEvaluation(scheduled, completed, allRecorded)
    }

    private suspend fun weekdaysOn(entity: HabitEntity, date: LocalDate): Set<DayOfWeek> {
        val snapshots = settingsDao.getByHabit(entity.id)
        val day = date.toEpochDay()
        val snapshot = snapshots.lastOrNull { it.effectiveDate <= day }
        return decodeWeekdays(snapshot?.weekdays ?: entity.selectedWeekdays)
    }

    // -------------------------------------------------------------- mutations

    suspend fun createHabit(
        name: String,
        initialValue: BigDecimal,
        unitCode: String
    ): Result<Long> {
        runDailyProcessing()
        val unit = Units.byCode(unitCode)
        val value = initialValue.roundToUnit(unit)
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Укажите название"))
        if (value <= BigDecimal.ZERO) return Result.failure(IllegalArgumentException("Значение должно быть больше нуля"))

        return db.withTransaction {
            val progress = progressDao.get()
                ?: UserProgressEntity(1, 0, 1, clock().toEpochDay())
            val active = habitDao.activeCount()
            if (active >= progress.unlockedSlots) {
                return@withTransaction Result.failure(
                    IllegalStateException("Нет доступного слота. Наберите баллы для разблокировки.")
                )
            }
            val today = clock()
            val id = habitDao.insert(
                HabitEntity(
                    name = name.trim(),
                    initialValue = value.toStorage(),
                    resetValue = value.toStorage(),
                    progressionBaseValue = value.toStorage(),
                    currentGoal = value.toStorage(),
                    unit = unit.code,
                    step = BigDecimal.ONE.toStorage(),
                    cycleLength = 7,
                    cycleIndex = 0,
                    dayIndex = 0,
                    targetValue = null,
                    targetBehavior = TargetBehavior.ASK.code,
                    selectedWeekdays = "1,2,3,4,5,6,7",
                    missPolicy = MissPolicy.KEEP.code,
                    missPenalty = BigDecimal.ONE.toStorage(),
                    status = HabitStatus.ACTIVE.code,
                    startDate = today.plusDays(1).toEpochDay(),
                    createdAt = today.toEpochDay(),
                    archivedAt = null,
                    completedAt = null,
                    lastProcessedDate = today.toEpochDay(),
                    pendingResetValue = null,
                    pendingResetDate = null,
                    targetReached = false,
                    notificationsEnabled = false,
                    notificationHour = 9,
                    notificationMinute = 0
                )
            )
            val habit = habitDao.getById(id)!!.toDomain()
            insertSnapshot(habit, today)
            insertEvent(
                habitId = id,
                type = EventType.HABIT_CREATED,
                effectiveDate = today,
                payload = EventPayload(newGoal = value.toStorage())
            )
            Result.success(id)
        }
    }

    /**
     * Records today's result. [actual] == null means "done" and stores the goal itself.
     * Repeated calls for the same date are ignored.
     */
    suspend fun recordCompletion(habitId: Long, actual: BigDecimal?): Boolean {
        runDailyProcessing()
        return db.withTransaction {
            val entity = habitDao.getById(habitId) ?: return@withTransaction false
            var habit = entity.toDomain()
            if (habit.status != HabitStatus.ACTIVE) return@withTransaction false
            val today = clock()
            ensureClockConsistent(today, habit)
            if (!habit.isScheduledOn(today)) return@withTransaction false
            if (recordDao.getForDate(habitId, today.toEpochDay()) != null) {
                return@withTransaction false
            }

            habit = applyPendingResetIfDue(habit, today)
            val planned = habit.currentGoal
            val actualValue = (actual ?: planned).roundActualToUnit(habit.unit)
            val status = when {
                actualValue >= planned -> CompletionStatus.COMPLETED
                actualValue <= BigDecimal.ZERO -> CompletionStatus.MISSED
                else -> CompletionStatus.PARTIAL
            }

            recordDao.insert(
                HabitDailyRecordEntity(
                    habitId = habitId,
                    date = today.toEpochDay(),
                    plannedValue = planned.toStorage(),
                    actualValue = actualValue.toStorage(),
                    completionStatus = status.code,
                    cycleIndex = habit.cycleIndex,
                    dayIndex = habit.dayIndex,
                    createdAt = System.currentTimeMillis()
                )
            )

            val state = ProgressionState.from(habit)
            val newState = if (status == CompletionStatus.COMPLETED) {
                ProgressionCalculator.advanceAfterCompletion(state, planned, habit.unit)
            } else {
                MissCalculator.applyMiss(
                    state = state,
                    policy = habit.missPolicy,
                    resetValue = habit.resetValue,
                    penalty = habit.missPenalty,
                    unit = habit.unit
                )
            }

            habit = habit.withState(newState).copy(
                lastProcessedDate = today
            )

            if (status != CompletionStatus.COMPLETED) {
                insertEvent(
                    habitId = habitId,
                    type = EventType.MISS,
                    effectiveDate = today,
                    payload = EventPayload(
                        actual = actualValue.toStorage(),
                        policy = habit.missPolicy.code
                    )
                )
            }

            val target = habit.targetValue
            val targetCompleted = status == CompletionStatus.COMPLETED &&
                target != null && planned >= target

            if (targetCompleted && habit.targetBehavior == TargetBehavior.FINISH) {
                habit = habit.copy(
                    status = HabitStatus.COMPLETED,
                    completedAt = today,
                    targetReached = false
                )
                insertEvent(
                    habitId = habitId,
                    type = EventType.HABIT_COMPLETED,
                    effectiveDate = today,
                    payload = EventPayload(targetBehavior = TargetBehavior.FINISH.code)
                )
            } else if (targetCompleted && habit.targetBehavior == TargetBehavior.ASK) {
                insertEvent(
                    habitId = habitId,
                    type = EventType.GOAL_REACHED,
                    effectiveDate = today,
                    payload = EventPayload(newGoal = target?.toStorage())
                )
            }

            habitDao.update(habit.toEntity())
            evaluatePoints(today)
            true
        }
    }

    /** Manual reset by 50%. Returns the new goal, or null if not applicable. */
    suspend fun manualReset(habitId: Long): BigDecimal? {
        runDailyProcessing()
        return db.withTransaction {
            val entity = habitDao.getById(habitId) ?: return@withTransaction null
            var habit = entity.toDomain()
            if (habit.status != HabitStatus.ACTIVE) return@withTransaction null
            val today = clock()
            ensureClockConsistent(today, habit)
            val newGoal = ResetCalculator.newGoal(habit.currentGoal, habit.unit)
            val todayRecord = recordDao.getForDate(habitId, today.toEpochDay())
            val appliesToday = habit.isScheduledOn(today) && todayRecord == null

            if (appliesToday) {
                val state = ProgressionCalculator.resetTo(
                    ProgressionState.from(habit),
                    newGoal,
                    habit.unit
                )
                habit = habit.withState(state).copy(
                    lastProcessedDate = today,
                    pendingResetValue = null,
                    pendingResetDate = null
                )
                habitDao.update(habit.toEntity())
                insertEvent(
                    habitId = habitId,
                    type = EventType.MANUAL_RESET,
                    effectiveDate = today,
                    payload = EventPayload(newGoal = newGoal.toStorage())
                )
            } else {
                val from = if (todayRecord != null) today.plusDays(1) else today
                val effective = nextPlannedDate(habit, from)
                habit = habit.copy(
                    pendingResetValue = newGoal,
                    pendingResetDate = effective
                )
                habitDao.update(habit.toEntity())
                // The event is recorded when the deferred reset is actually applied,
                // so a superseded or cancelled plan never affects statistics.
            }
            newGoal
        }
    }

    suspend fun startPause(habitId: Long, type: PauseType): Boolean {
        runDailyProcessing()
        return db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction false
            if (habit.status != HabitStatus.ACTIVE) return@withTransaction false
            if (pauseDao.getActive(habitId) != null) return@withTransaction false
            val today = clock()
            ensureClockConsistent(today, habit)
            val end = PauseCalculator.endDate(today, type)
            pauseDao.insert(
                HabitPauseEntity(
                    habitId = habitId,
                    startDate = today.toEpochDay(),
                    endDate = end.toEpochDay(),
                    pauseType = type.code,
                    processedPeriods = 0,
                    status = PauseStatus.ACTIVE.code
                )
            )
            habitDao.update(
                habit.copy(
                    status = HabitStatus.PAUSED,
                    lastProcessedDate = today
                ).toEntity()
            )
            insertEvent(
                habitId = habitId,
                type = EventType.PAUSE_STARTED,
                effectiveDate = today,
                payload = EventPayload(pauseType = type.code)
            )
            true
        }
    }

    suspend fun extendPause(habitId: Long): Boolean {
        runDailyProcessing()
        return db.withTransaction {
            val active = pauseDao.getActive(habitId) ?: return@withTransaction false
            val type = PauseType.fromCode(active.pauseType)
            val newEnd = PauseCalculator.endDate(active.endDate.toLocalDate(), type)
            pauseDao.update(active.copy(endDate = newEnd.toEpochDay()))
            insertEvent(
                habitId = habitId,
                type = EventType.PAUSE_EXTENDED,
                effectiveDate = clock(),
                payload = EventPayload(pauseType = type.code)
            )
            true
        }
    }

    suspend fun updateName(habitId: Long, name: String) {
        if (name.isBlank()) return
        runDailyProcessing()
        db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            habitDao.update(habit.copy(name = name.trim()).toEntity())
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = clock(),
                payload = EventPayload(note = "name")
            )
        }
    }

    suspend fun updateStep(habitId: Long, step: BigDecimal) {
        if (step <= BigDecimal.ZERO) return
        runDailyProcessing()
        db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            if (habit.status != HabitStatus.ACTIVE) return@withTransaction
            val today = clock()
            val newStep = step.roundToUnit(habit.unit)
            val state = ProgressionCalculator.rebaseForNewStep(
                ProgressionState.from(habit),
                newStep,
                habit.unit
            )
            val updated = habit.withState(state).copy(step = newStep)
            habitDao.update(updated.toEntity())
            insertSnapshot(updated, effectiveSettingsDate(habit, today))
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = effectiveSettingsDate(habit, today),
                payload = EventPayload(note = "step")
            )
        }
    }

    suspend fun updateCycleLength(habitId: Long, cycleLength: Int) {
        if (cycleLength < 1) return
        runDailyProcessing()
        db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            if (habit.status != HabitStatus.ACTIVE) return@withTransaction
            val today = clock()
            val state = ProgressionCalculator.rebaseForNewCycleLength(
                ProgressionState.from(habit),
                cycleLength,
                habit.unit
            )
            val updated = habit.withState(state).copy(cycleLength = cycleLength)
            habitDao.update(updated.toEntity())
            insertSnapshot(updated, effectiveSettingsDate(habit, today))
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = effectiveSettingsDate(habit, today),
                payload = EventPayload(note = "cycleLength")
            )
        }
    }

    suspend fun updateResetValue(habitId: Long, value: BigDecimal) {
        val habit = getHabit(habitId) ?: return
        val normalized = value.roundToUnit(habit.unit)
        if (normalized <= BigDecimal.ZERO) return
        runDailyProcessing()
        db.withTransaction {
            val current = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            habitDao.update(current.copy(resetValue = normalized).toEntity())
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = clock(),
                payload = EventPayload(note = "resetValue")
            )
        }
    }

    /** Unit can only be changed before the first saved execution. */
    suspend fun updateUnit(habitId: Long, unitCode: String): Boolean {
        val unit = Units.byCode(unitCode)
        runDailyProcessing()
        return db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction false
            val hasExecutions = recordDao.getByHabit(habitId).any {
                CompletionStatus.fromCode(it.completionStatus).isExecution
            }
            if (hasExecutions) return@withTransaction false
            val updated = habit.copy(
                unit = unit,
                initialValue = habit.initialValue.roundToUnit(unit),
                resetValue = habit.resetValue.roundToUnit(unit),
                progressionBaseValue = habit.progressionBaseValue.roundToUnit(unit),
                currentGoal = habit.currentGoal.roundToUnit(unit),
                step = habit.step.roundToUnit(unit),
                missPenalty = habit.missPenalty.roundToUnit(unit),
                targetValue = habit.targetValue?.roundToUnit(unit)
            )
            habitDao.update(updated.toEntity())
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = clock(),
                payload = EventPayload(note = "unit")
            )
            true
        }
    }

    suspend fun updateMissPolicy(
        habitId: Long,
        policy: MissPolicy,
        penalty: BigDecimal
    ) {
        runDailyProcessing()
        db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            val normalized = if (penalty <= BigDecimal.ZERO) BigDecimal.ONE else penalty.roundToUnit(habit.unit)
            val updated = habit.copy(missPolicy = policy, missPenalty = normalized)
            habitDao.update(updated.toEntity())
            insertSnapshot(updated, effectiveSettingsDate(habit, clock()))
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = clock(),
                payload = EventPayload(note = "missPolicy")
            )
        }
    }

    suspend fun updateWeekdays(habitId: Long, weekdays: Set<DayOfWeek>) {
        if (weekdays.isEmpty()) return
        runDailyProcessing()
        db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            val today = clock()
            val updated = habit.copy(weekdays = weekdays)
            val effective = settingsEffectiveDate(updated, today)
            habitDao.update(updated.toEntity())
            insertSnapshot(updated, effective)
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = effective,
                payload = EventPayload(note = "weekdays")
            )
        }
    }

    suspend fun updateTarget(
        habitId: Long,
        target: BigDecimal?,
        behavior: TargetBehavior
    ) {
        runDailyProcessing()
        db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction
            val normalized = target?.roundToUnit(habit.unit)
            var updated = habit.copy(
                targetValue = normalized,
                targetBehavior = if (normalized == null) TargetBehavior.ASK else behavior,
                targetReached = false
            )
            if (normalized != null && behavior == TargetBehavior.MAINTAIN &&
                updated.currentGoal > normalized
            ) {
                updated = updated.withState(
                    ProgressionCalculator.maintainTarget(
                        ProgressionState.from(updated),
                        updated.unit
                    )
                )
            }
            habitDao.update(updated.toEntity())
            insertSnapshot(updated, effectiveSettingsDate(habit, clock()))
            insertEvent(
                habitId = habitId,
                type = EventType.SETTINGS_CHANGED,
                effectiveDate = clock(),
                payload = EventPayload(
                    newGoal = normalized?.toStorage(),
                    targetBehavior = behavior.code
                )
            )
        }
    }

    suspend fun updateNotifications(
        habitId: Long,
        enabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        val habit = getHabit(habitId) ?: return
        db.withTransaction {
            habitDao.update(
                habit.copy(
                    notificationsEnabled = enabled,
                    notificationHour = hour.coerceIn(0, 23),
                    notificationMinute = minute.coerceIn(0, 59)
                ).toEntity()
            )
        }
    }

    suspend fun resolveTarget(habitId: Long, behavior: TargetBehavior): Boolean {
        runDailyProcessing()
        return db.withTransaction {
            val entity = habitDao.getById(habitId) ?: return@withTransaction false
            var habit = entity.toDomain()
            val target = habit.targetValue ?: return@withTransaction false
            when (behavior) {
                TargetBehavior.MAINTAIN -> {
                    habit = habit.withState(
                        ProgressionCalculator.maintainTarget(
                            ProgressionState.from(habit),
                            habit.unit
                        )
                    ).copy(targetBehavior = TargetBehavior.MAINTAIN, targetReached = false)
                }

                TargetBehavior.CONTINUE -> {
                    habit = habit.withState(
                        ProgressionCalculator.continueAfterTarget(
                            ProgressionState.from(habit),
                            habit.unit
                        )
                    ).copy(targetBehavior = TargetBehavior.CONTINUE, targetReached = false)
                }

                TargetBehavior.FINISH -> {
                    habit = habit.copy(
                        status = HabitStatus.COMPLETED,
                        completedAt = clock(),
                        targetReached = false
                    )
                    insertEvent(
                        habitId = habitId,
                        type = EventType.HABIT_COMPLETED,
                        effectiveDate = clock(),
                        payload = EventPayload(
                            newGoal = target.toStorage(),
                            targetBehavior = TargetBehavior.FINISH.code
                        )
                    )
                }

                TargetBehavior.ASK -> return@withTransaction false
            }
            habitDao.update(habit.toEntity())
            if (behavior != TargetBehavior.FINISH) {
                // The chosen behavior only applies from the first day that is not
                // already recorded, so historical statistics stay unchanged.
                insertSnapshot(habit, effectiveSettingsDate(habit, clock()))
            }
            insertEvent(
                habitId = habitId,
                type = EventType.GOAL_REACHED,
                effectiveDate = clock(),
                payload = EventPayload(targetBehavior = behavior.code)
            )
            true
        }
    }

    suspend fun archiveHabit(habitId: Long): Boolean {
        runDailyProcessing()
        return db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction false
            if (habit.status == HabitStatus.ARCHIVED || habit.status == HabitStatus.COMPLETED) {
                return@withTransaction false
            }
            val today = clock()
            pauseDao.getActive(habitId)?.let {
                pauseDao.update(it.copy(status = PauseStatus.ENDED.code))
            }
            habitDao.update(
                habit.copy(
                    status = HabitStatus.ARCHIVED,
                    archivedAt = today,
                    lastProcessedDate = today,
                    pendingResetValue = null,
                    pendingResetDate = null
                ).toEntity()
            )
            insertEvent(
                habitId = habitId,
                type = EventType.HABIT_ARCHIVED,
                effectiveDate = today,
                payload = EventPayload()
            )
            true
        }
    }

    suspend fun completeHabit(habitId: Long): Boolean {
        runDailyProcessing()
        return db.withTransaction {
            val habit = habitDao.getById(habitId)?.toDomain() ?: return@withTransaction false
            if (habit.status == HabitStatus.COMPLETED || habit.status == HabitStatus.ARCHIVED) {
                return@withTransaction false
            }
            val today = clock()
            pauseDao.getActive(habitId)?.let {
                pauseDao.update(it.copy(status = PauseStatus.ENDED.code))
            }
            habitDao.update(
                habit.copy(
                    status = HabitStatus.COMPLETED,
                    completedAt = today,
                    lastProcessedDate = today,
                    pendingResetValue = null,
                    pendingResetDate = null
                ).toEntity()
            )
            insertEvent(
                habitId = habitId,
                type = EventType.HABIT_COMPLETED,
                effectiveDate = today,
                payload = EventPayload()
            )
            true
        }
    }

    suspend fun deleteHabit(habitId: Long) {
        db.withTransaction {
            habitDao.deleteById(habitId)
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun effectiveSettingsDate(habit: Habit, today: LocalDate): LocalDate {
        if (!habit.isScheduledOn(today)) return nextPlannedDate(habit, today)
        val record = recordDao.getForDate(habit.id, today.toEpochDay())
        return if (record != null) nextPlannedDate(habit, today.plusDays(1)) else today
    }

    /**
     * Schedule changes must not rewrite an already recorded day, and they take
     * effect immediately when the updated schedule includes today.
     */
    private suspend fun settingsEffectiveDate(updated: Habit, today: LocalDate): LocalDate {
        val hasRecord = recordDao.getForDate(updated.id, today.toEpochDay()) != null
        return if (!hasRecord && updated.isScheduledOn(today)) {
            today
        } else {
            nextPlannedDate(updated, today.plusDays(1))
        }
    }

    private fun ensureClockConsistent(today: LocalDate, habit: Habit) {
        check(!today.isBefore(habit.lastProcessedDate)) {
            "Системная дата раньше последней обработанной даты привычки. Проверьте время на устройстве."
        }
    }

    private fun nextPlannedDate(habit: Habit, fromInclusive: LocalDate): LocalDate {
        var date = if (fromInclusive.isBefore(habit.startDate)) habit.startDate else fromInclusive
        repeat(400) {
            if (habit.isScheduledOn(date)) return date
            date = date.plusDays(1)
        }
        return date
    }

    private suspend fun applyPendingResetIfDue(habit: Habit, date: LocalDate): Habit {
        val pendingDate = habit.pendingResetDate ?: return habit
        val pendingValue = habit.pendingResetValue ?: return habit
        if (date.isBefore(pendingDate)) return habit
        val state = ProgressionCalculator.resetTo(
            ProgressionState.from(habit),
            pendingValue,
            habit.unit
        )
        val updated = habit.withState(state).copy(
            pendingResetValue = null,
            pendingResetDate = null
        )
        insertEvent(
            habitId = habit.id,
            type = EventType.MANUAL_RESET,
            effectiveDate = date,
            payload = EventPayload(newGoal = pendingValue.toStorage())
        )
        return updated
    }

    private fun Habit.withState(state: ProgressionState): Habit = copy(
        progressionBaseValue = state.base,
        currentGoal = state.currentGoal,
        step = state.step,
        cycleLength = state.cycleLength,
        cycleIndex = state.cycleIndex,
        dayIndex = state.dayIndex,
        targetBehavior = state.targetBehavior,
        targetReached = state.targetReached
    )

    private suspend fun insertSnapshot(habit: Habit, effectiveDate: LocalDate) {
        settingsDao.insert(
            HabitSettingsHistoryEntity(
                habitId = habit.id,
                effectiveDate = effectiveDate.toEpochDay(),
                step = habit.step.toStorage(),
                cycleLength = habit.cycleLength,
                weekdays = habit.weekdays.map { it.value }.sorted().joinToString(","),
                missPolicy = habit.missPolicy.code,
                missPenalty = habit.missPenalty.toStorage(),
                targetValue = habit.targetValue?.toStorage(),
                targetBehavior = habit.targetBehavior.code,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun insertEvent(
        habitId: Long,
        type: EventType,
        effectiveDate: LocalDate,
        payload: EventPayload
    ) {
        eventDao.insert(
            HabitEventEntity(
                habitId = habitId,
                eventType = type.code,
                effectiveDate = effectiveDate.toEpochDay(),
                eventData = json.encodeToString(payload),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private data class DayEvaluation(
        val scheduled: Int,
        val completed: Int,
        val allRecorded: Boolean
    )
}

enum class StatisticsPeriod {
    WEEK,
    MONTH
}
