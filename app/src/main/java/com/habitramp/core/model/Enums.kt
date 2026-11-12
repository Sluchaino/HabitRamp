package com.habitramp.core.model

/** Lifecycle state of a habit. */
enum class HabitStatus(val code: String) {
    ACTIVE("ACTIVE"),
    PAUSED("PAUSED"),
    ARCHIVED("ARCHIVED"),
    COMPLETED("COMPLETED");

    companion object {
        fun fromCode(code: String?): HabitStatus =
            entries.firstOrNull { it.code == code } ?: ACTIVE
    }
}

/** Result of a planned day. */
enum class CompletionStatus(val code: String) {
    COMPLETED("COMPLETED"),
    PARTIAL("PARTIAL"),
    MISSED("MISSED");

    /** A saved execution that must lock the unit of measurement. */
    val isExecution: Boolean
        get() = this == COMPLETED || this == PARTIAL

    companion object {
        fun fromCode(code: String?): CompletionStatus =
            entries.firstOrNull { it.code == code } ?: MISSED
    }
}

enum class MissPolicy(val code: String) {
    /** Start over from the configured reset value. */
    RESTART("RESTART"),

    /** Reduce the current goal by the configured penalty. */
    REDUCE("REDUCE"),

    /** Keep the current goal and continue the normal progression. */
    KEEP("KEEP");

    companion object {
        fun fromCode(code: String?): MissPolicy =
            entries.firstOrNull { it.code == code } ?: KEEP
    }
}

enum class PauseType(val code: String) {
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY");

    companion object {
        fun fromCode(code: String?): PauseType =
            entries.firstOrNull { it.code == code } ?: WEEKLY
    }
}

enum class PauseStatus(val code: String) {
    ACTIVE("ACTIVE"),
    ENDED("ENDED");

    companion object {
        fun fromCode(code: String?): PauseStatus =
            entries.firstOrNull { it.code == code } ?: ENDED
    }
}

enum class TargetBehavior(val code: String) {
    /** Target reached, the user has not decided yet. */
    ASK("ASK"),

    /** Freeze growth at the target value. */
    MAINTAIN("MAINTAIN"),

    /** Keep growing past the target. */
    CONTINUE("CONTINUE"),

    /** Complete the habit when the target is reached. */
    FINISH("FINISH");

    companion object {
        fun fromCode(code: String?): TargetBehavior =
            entries.firstOrNull { it.code == code } ?: ASK
    }
}

enum class EventType(val code: String) {
    HABIT_CREATED("HABIT_CREATED"),
    MANUAL_RESET("MANUAL_RESET"),
    MISS("MISS"),
    PAUSE_STARTED("PAUSE_STARTED"),
    PAUSE_EXTENDED("PAUSE_EXTENDED"),
    PAUSE_PERIOD_PROCESSED("PAUSE_PERIOD_PROCESSED"),
    PAUSE_ENDED("PAUSE_ENDED"),
    SETTINGS_CHANGED("SETTINGS_CHANGED"),
    GOAL_REACHED("GOAL_REACHED"),
    HABIT_ARCHIVED("HABIT_ARCHIVED"),
    HABIT_COMPLETED("HABIT_COMPLETED");

    companion object {
        fun fromCode(code: String?): EventType =
            entries.firstOrNull { it.code == code } ?: SETTINGS_CHANGED
    }
}
