package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeaknessRecordDto(
    @SerialName("skill_key") val skillKey: String,
    val status: String, // NEW | REPEATED | IMPROVING | NEARLY_CLOSED | CLOSED
    val occurrences: Int,
    @SerialName("consecutive_good_signals") val consecutiveGoodSignals: Int,
    @SerialName("last_score") val lastScore: Double,
    @SerialName("last_seen_at") val lastSeenAt: String,
)
