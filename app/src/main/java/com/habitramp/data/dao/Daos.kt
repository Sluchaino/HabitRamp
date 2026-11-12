package com.habitramp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.habitramp.data.entity.HabitDailyRecordEntity
import com.habitramp.data.entity.HabitEntity
import com.habitramp.data.entity.HabitEventEntity
import com.habitramp.data.entity.HabitPauseEntity
import com.habitramp.data.entity.HabitSettingsHistoryEntity
import com.habitramp.data.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE status = :status ORDER BY createdAt ASC")
    fun observeByStatus(status: String): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun getAll(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE status IN ('ACTIVE', 'PAUSED') ORDER BY createdAt ASC")
    suspend fun getLive(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE status = 'ACTIVE' OR status = 'PAUSED'")
    fun observeLive(): Flow<List<HabitEntity>>

    @Query("SELECT COUNT(*) FROM habits WHERE status IN ('ACTIVE', 'PAUSED')")
    suspend fun activeCount(): Int

    @Query("SELECT COUNT(*) FROM habits WHERE status IN ('ACTIVE', 'PAUSED')")
    fun observeActiveCount(): Flow<Int>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_records WHERE habitId = :habitId ORDER BY date ASC")
    fun observeByHabit(habitId: Long): Flow<List<HabitDailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE habitId = :habitId ORDER BY date ASC")
    suspend fun getByHabit(habitId: Long): List<HabitDailyRecordEntity>

    @Query("SELECT * FROM daily_records WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getForDate(habitId: Long, date: Long): HabitDailyRecordEntity?

    @Query("SELECT * FROM daily_records WHERE habitId = :habitId AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getBetween(habitId: Long, from: Long, to: Long): List<HabitDailyRecordEntity>

    @Query("SELECT * FROM daily_records WHERE habitId = :habitId AND date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeBetween(habitId: Long, from: Long, to: Long): Flow<List<HabitDailyRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: HabitDailyRecordEntity): Long

    @Query("DELETE FROM daily_records WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: HabitEventEntity): Long

    @Query("SELECT * FROM habit_events WHERE habitId = :habitId ORDER BY effectiveDate ASC, createdAt ASC")
    suspend fun getByHabit(habitId: Long): List<HabitEventEntity>

    @Query("SELECT * FROM habit_events WHERE habitId = :habitId ORDER BY effectiveDate ASC, createdAt ASC")
    fun observeByHabit(habitId: Long): Flow<List<HabitEventEntity>>

    @Query("SELECT * FROM habit_events WHERE habitId = :habitId AND eventType = :type ORDER BY effectiveDate ASC")
    suspend fun getByType(habitId: Long, type: String): List<HabitEventEntity>

    @Query("DELETE FROM habit_events WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

@Dao
interface PauseDao {
    @Insert
    suspend fun insert(pause: HabitPauseEntity): Long

    @Update
    suspend fun update(pause: HabitPauseEntity)

    @Query("SELECT * FROM habit_pauses WHERE habitId = :habitId ORDER BY startDate ASC")
    suspend fun getByHabit(habitId: Long): List<HabitPauseEntity>

    @Query("SELECT * FROM habit_pauses WHERE habitId = :habitId ORDER BY startDate ASC")
    fun observeByHabit(habitId: Long): Flow<List<HabitPauseEntity>>

    @Query("SELECT * FROM habit_pauses WHERE status = 'ACTIVE'")
    suspend fun getAllActive(): List<HabitPauseEntity>

    @Query("SELECT * FROM habit_pauses WHERE habitId = :habitId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActive(habitId: Long): HabitPauseEntity?

    @Query("DELETE FROM habit_pauses WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

@Dao
interface SettingsHistoryDao {
    @Insert
    suspend fun insert(snapshot: HabitSettingsHistoryEntity): Long

    @Query("SELECT * FROM habit_settings_history WHERE habitId = :habitId ORDER BY effectiveDate ASC, id ASC")
    suspend fun getByHabit(habitId: Long): List<HabitSettingsHistoryEntity>

    @Query("SELECT * FROM habit_settings_history WHERE habitId = :habitId ORDER BY effectiveDate ASC, id ASC")
    fun observeByHabit(habitId: Long): Flow<List<HabitSettingsHistoryEntity>>

    @Query("DELETE FROM habit_settings_history WHERE habitId = :habitId")
    suspend fun deleteByHabit(habitId: Long)
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 1")
    suspend fun get(): UserProgressEntity?

    @Query("SELECT * FROM user_progress WHERE id = 1")
    fun observe(): Flow<UserProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: UserProgressEntity)

    @Query("DELETE FROM user_progress")
    suspend fun clear()
}
