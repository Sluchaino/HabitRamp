package com.habitramp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.habitramp.data.dao.DailyRecordDao
import com.habitramp.data.dao.EventDao
import com.habitramp.data.dao.HabitDao
import com.habitramp.data.dao.PauseDao
import com.habitramp.data.dao.SettingsHistoryDao
import com.habitramp.data.dao.UserProgressDao
import com.habitramp.data.entity.HabitDailyRecordEntity
import com.habitramp.data.entity.HabitEntity
import com.habitramp.data.entity.HabitEventEntity
import com.habitramp.data.entity.HabitPauseEntity
import com.habitramp.data.entity.HabitSettingsHistoryEntity
import com.habitramp.data.entity.UserProgressEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitDailyRecordEntity::class,
        HabitEventEntity::class,
        HabitPauseEntity::class,
        HabitSettingsHistoryEntity::class,
        UserProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun eventDao(): EventDao
    abstract fun pauseDao(): PauseDao
    abstract fun settingsHistoryDao(): SettingsHistoryDao
    abstract fun userProgressDao(): UserProgressDao

    companion object {
        fun build(context: Context): HabitDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HabitDatabase::class.java,
                "habitramp.db"
            ).build()
    }
}
