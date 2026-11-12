package com.habitramp.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class EventPayload(
    val actual: String? = null,
    val newGoal: String? = null,
    val policy: String? = null,
    val pauseType: String? = null,
    val periods: Int? = null,
    val targetBehavior: String? = null,
    val note: String? = null
)
