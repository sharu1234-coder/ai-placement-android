package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MissionDto(
    val id: Int,
    @SerialName("mission_date") val missionDate: String,
    val tier: String? = null,
    val title: String,
    @SerialName("action_type") val actionType: String, // learn/practice/project/resume/roadmap/maintenance
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    val reasons: List<String> = emptyList(),
    val skill: String? = null,
    val status: String, // not_started/in_progress/completed
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("assessment_id") val assessmentId: Int? = null,
)

@Serializable
data class CompleteMissionRequestDto(@SerialName("assessment_id") val assessmentId: Int? = null)
