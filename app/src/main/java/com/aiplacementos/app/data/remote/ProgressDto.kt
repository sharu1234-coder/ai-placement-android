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
    // PERFORMANCE: the mean of the latest test's and the latest interview's overall score,
    // 0-1. Renamed from readiness_score in Phase 2F -- it says how the student PERFORMED on
    // the last things they did, not how ready they are for anything. Null means neither has
    // been completed, which is not a performance of zero.
    @SerialName("performance_score") val performanceScore: Double?,
    @SerialName("completed_assessments") val completedAssessments: Int,
    @SerialName("completed_interviews") val completedInterviews: Int,
    @SerialName("roadmap_tasks_done") val roadmapTasksDone: Int,
    @SerialName("roadmap_tasks_total") val roadmapTasksTotal: Int,
    @SerialName("top_weaknesses") val topWeaknesses: List<String>,
    val trend: List<ProgressTrendPointDto>,
)
