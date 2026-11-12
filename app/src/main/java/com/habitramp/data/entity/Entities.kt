package com.habitramp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val initialValue: String,
    val resetValue: String,
    val progressionBaseValue: String,
    val currentGoal: String,
    val unit: String,
    val step: String,
    val cycleLength: Int,
    val cycleIndex: Int,
    val dayIndex: Int,
    val targetValue: String?,
    val targetBehavior: String,
    val selectedWeekdays: String,
    val missPolicy: String,
    val missPenalty: String,
    val status: String,
    val startDate: Long,
    val createdAt: Long,
    val archivedAt: Long?,
    val completedAt: Long?,
    val lastProcessedDate: Long,
    val pendingResetValue: String?,
    val pendingResetDate: Long?,
    val targetReached: Boolean,
    val notificationsEnabled: Boolean,
    val notificationHour: Int,
    val notificationMinute: Int
)

@Entity(
    tableName = "daily_records",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitDailyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: Long,
    val plannedValue: String,
    val actualValue: String,
    val completionStatus: String,
    val cycleIndex: Int,
    val dayIndex: Int,
    val createdAt: Long
)

@Entity(
    tableName = "habit_events",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId")]
)
data class HabitEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val eventType: String,
    val effectiveDate: Long,
    val eventData: String,
    val createdAt: Long
)

@Entity(
    tableName = "habit_pauses",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId")]
)
data class HabitPauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val startDate: Long,
    val endDate: Long,
    val pauseType: String,
    val processedPeriods: Int,
    val status: String
)

@Entity(
    tableName = "habit_settings_history",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId")]
)
data class HabitSettingsHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val effectiveDate: Long,
    val step: String,
    val cycleLength: Int,
    val weekdays: String,
    val missPolicy: String,
    val missPenalty: String,
    val targetValue: String?,
    val targetBehavior: String,
    val createdAt: Long
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 1,
    val currentPoints: Int,
    val unlockedSlots: Int,
    val lastProcessedDate: Long
)
