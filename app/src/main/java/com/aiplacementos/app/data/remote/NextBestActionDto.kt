package com.aiplacementos.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecommendedActionDto(
    @SerialName("has_recommendation") val hasRecommendation: Boolean,
    val tier: String? = null,
    val title: String,
    @SerialName("action_type") val actionType: String, // learn/practice/project/resume/roadmap/maintenance
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    val reasons: List<String> = emptyList(),
    val skill: String? = null,
)
