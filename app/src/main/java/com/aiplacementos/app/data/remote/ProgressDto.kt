package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProgressTrendPointDto(
    @SerialName("created_at") val createdAt: String,
    val kind: String,
    val label: String,
    val score: Double,
)

@Serializable
data class ProgressDashboardDto(
    @SerialName("readiness_score") val readinessScore: Double?,
    @SerialName("completed_assessments") val completedAssessments: Int,
    @SerialName("completed_interviews") val completedInterviews: Int,
    @SerialName("roadmap_tasks_done") val roadmapTasksDone: Int,
    @SerialName("roadmap_tasks_total") val roadmapTasksTotal: Int,
    @SerialName("top_weaknesses") val topWeaknesses: List<String>,
    val trend: List<ProgressTrendPointDto>,
)
