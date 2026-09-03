package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PendingNotificationDto(
    val id: Int,
    val kind: String, // mission_completed | weakness_improved | evidence_verified | readiness_milestone
    val title: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AcknowledgeNotificationsRequestDto(val ids: List<Int>)
