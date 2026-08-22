package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerateRoadmapRequestDto(
    @SerialName("jd_id") val jdId: Int? = null,
    @SerialName("duration_days") val durationDays: Int = 30,
)

@Serializable
data class UpdateRoadmapTaskRequestDto(
    val status: String,
)

@Serializable
data class RoadmapTaskDto(
    val id: Int,
    @SerialName("day_number") val dayNumber: Int,
    @SerialName("task_type") val taskType: String,
    @SerialName("skill_tag") val skillTag: String,
    val title: String,
    val description: String,
    val status: String,
    @SerialName("due_date") val dueDate: String,
)

@Serializable
data class RoadmapDto(
    val id: Int,
    @SerialName("jd_id") val jdId: Int?,
    @SerialName("duration_days") val durationDays: Int,
    val status: String,
    val tasks: List<RoadmapTaskDto>,
)
