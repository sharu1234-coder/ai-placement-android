package com.aiplacementos.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per on-device alarm firing. clientAlarmId is generated once when the alarm fires
 * (AlarmFiredReceiver) and is what makes the backend's start-challenge call idempotent --
 * reopening this same firing instance (Back, app close, process death, tapping the
 * notification again) always returns to the exact same 6 questions rather than a new random
 * set, because the backend looks up an existing in-progress challenge by this id first.
 */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val clientAlarmId: String,
    val alarmConfigId: Long,
    val backendChallengeId: Int? = null,
    val status: String = "in_progress",
    val createdAtEpochMillis: Long,
)
